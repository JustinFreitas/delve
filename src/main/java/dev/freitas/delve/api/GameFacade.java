package dev.freitas.delve.api;

import dev.freitas.delve.config.GameProps;

import dev.freitas.delve.api.dto.ActionResult;
import dev.freitas.delve.api.dto.StateSnapshot;
import dev.freitas.delve.command.CharacterSheets;
import dev.freitas.delve.command.PartySummary;
import dev.freitas.delve.data.GameStateService;
import dev.freitas.delve.game.CharacterFactory;
import dev.freitas.delve.game.MuleFactory;
import dev.freitas.delve.game.PregenExport;
import dev.freitas.delve.game.PregenService;
import dev.freitas.delve.game.RetainerFactory;
import dev.freitas.delve.game.dungeon.ModuleLoader;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.MuleRules;
import dev.freitas.delve.game.engine.RetainerRules;
import dev.freitas.delve.game.engine.Spell;
import dev.freitas.delve.game.engine.SpellTables;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.GameSession;
import dev.freitas.delve.game.model.Mule;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.BankService;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.ExplorationService;
import dev.freitas.delve.game.session.MuleService;
import dev.freitas.delve.game.session.SpellService;
import dev.freitas.delve.game.session.TownService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Discord-agnostic orchestration for one player's game, shared by the web API (and available to any
 * other front-end). Mirrors what the JDA commands do — load the {@link SaveGame}, guard, call the
 * appropriate domain service, persist — but returns plain {@link ActionResult} DTOs instead of Discord
 * replies. The actual rules live in the unchanged services this delegates to.
 */
@Service
public class GameFacade {

    private static final DamageRoll HEALING_POTION = new DamageRoll(2, 4, 2);

    private final GameStateService gameState;
    private final ExplorationService exploration;
    private final CombatService combat;
    private final SpellService spells;
    private final TownService town;
    private final PregenService pregen;
    private final RetainerFactory retainerFactory;
    private final CharacterFactory characterFactory;
    private final MuleService muleService;
    private final MuleFactory muleFactory;
    private final BankService bankService;
    private final Dice dice;
    private final GameProps gameProps;

    public GameFacade(
            GameStateService gameState,
            ExplorationService exploration,
            CombatService combat,
            SpellService spells,
            TownService town,
            PregenService pregen,
            RetainerFactory retainerFactory,
            CharacterFactory characterFactory,
            MuleService muleService,
            MuleFactory muleFactory,
            BankService bankService,
            Dice dice,
            GameProps gameProps) {
        this.gameState = gameState;
        this.exploration = exploration;
        this.combat = combat;
        this.spells = spells;
        this.town = town;
        this.pregen = pregen;
        this.retainerFactory = retainerFactory;
        this.characterFactory = characterFactory;
        this.muleService = muleService;
        this.muleFactory = muleFactory;
        this.bankService = bankService;
        this.dice = dice;
        this.gameProps = gameProps;
    }

    // --- state & character ------------------------------------------------

    public StateSnapshot state(long userId) {
        return StateSnapshot.of(gameState.load(userId));
    }

    /** The current character as a clean B/X stat-block map, or null if none. {@code pcName} selects a
        specific PC in a multi-PC party; null/blank defaults to the primary (first-rolled) PC. */
    public Map<String, Object> character(long userId, String pcName) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return null;
        }
        Character pc = resolveActor(save, pcName);
        return pc == null ? null : PregenExport.of(pc);
    }

    /** Additive, matching {@code /roll-character}: the first roll starts the party (and a fresh dungeon
        session); every roll after that adds another PC up to {@link SaveGame#MAX_CHARACTERS}, without
        disturbing an in-progress delve. */
    public ActionResult rollCharacter(long userId, String classToken, String name) {
        CharacterClass cls = CharacterClass.parse(classToken);
        if (cls == null) {
            return fail(userId, "Unknown class. Choose: cleric, fighter, magic-user, thief, dwarf, elf, halfling.");
        }
        AbilityScores abilities = AbilityScores.roll(dice);
        if (!cls.meetsRequirements(abilities)) {
            return fail(userId, "Those 3d6 rolls don't qualify for a " + cls.displayName() + ": needs "
                    + cls.unmetRequirements(abilities) + ". Try again.");
        }
        String pcName = blankToNull(name) != null ? name.trim() : cls.displayName();
        SaveGame existing = gameState.load(userId);
        boolean firstPc = !existing.hasCharacter();
        if (!firstPc && existing.getCharacters().size() >= SaveGame.MAX_CHARACTERS) {
            return fail(existing, "Your party already has the maximum of " + SaveGame.MAX_CHARACTERS
                    + " characters. Dismiss one first.");
        }
        if (!firstPc && nameCollides(existing, pcName)) {
            return fail(existing, "'" + pcName + "' is already a name in your party — every PC and "
                    + "retainer needs a distinct name.");
        }
        Character character = characterFactory.create(pcName, cls, abilities);
        spells.autoPrepare(character);
        gameState.mutate(userId, s -> {
            if (firstPc) {
                s.setCharacter(character);
                s.setSession(new GameSession());
            } else {
                s.addCharacter(character);
            }
        });
        return ok(userId, "Rolled up " + pcName + ", a level 1 " + cls.displayName() + "!");
    }

    /** Additive, same rule as {@link #rollCharacter}. */
    public ActionResult pregen(long userId, String classToken, int level, String name) {
        CharacterClass cls = CharacterClass.parse(classToken);
        if (cls == null) {
            return fail(userId, "Unknown class.");
        }
        if (level < 1 || level > pregen.maxLevel(cls)) {
            return fail(userId, "Level must be 1.." + pregen.maxLevel(cls) + " for a " + cls.displayName() + ".");
        }
        String pcName = blankToNull(name) != null ? name.trim() : cls.displayName();
        SaveGame existing = gameState.load(userId);
        boolean firstPc = !existing.hasCharacter();
        if (!firstPc && existing.getCharacters().size() >= SaveGame.MAX_CHARACTERS) {
            return fail(existing, "Your party already has the maximum of " + SaveGame.MAX_CHARACTERS
                    + " characters. Dismiss one first.");
        }
        if (!firstPc && nameCollides(existing, pcName)) {
            return fail(existing, "'" + pcName + "' is already a name in your party — every PC and "
                    + "retainer needs a distinct name.");
        }
        Character character = pregen.create(pcName, cls, level);
        gameState.mutate(userId, s -> {
            if (firstPc) {
                s.setCharacter(character);
                s.setSession(new GameSession());
            } else {
                s.addCharacter(character);
            }
        });
        return ok(userId, "Generated " + pcName + ", a level " + level + " " + cls.displayName() + ".");
    }

    private static boolean nameCollides(SaveGame save, String name) {
        return save.getCharacters().stream().anyMatch(c -> c.getName().equalsIgnoreCase(name))
                || save.getRetainers().stream().anyMatch(r -> r.getName().equalsIgnoreCase(name));
    }

    // --- exploration ------------------------------------------------------

    public ActionResult enter(long userId, String moduleName) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll or generate a character first.");
        }
        if (!save.getCharacter().isAlive()) {
            return fail(save, "Your character has died. Roll a new one.");
        }
        if (save.getSession().isInDungeon()) {
            return fail(save, "You are already delving. Use look.");
        }
        ExplorationResult result;
        if (blankToNull(moduleName) == null) {
            result = exploration.enter(save);
        } else {
            ModuleLoader.LoadedModule loaded = ModuleLoader.load(moduleName);
            if (loaded == null) {
                return fail(save, "No module named '" + moduleName + "'.");
            }
            result = exploration.enterModule(save, loaded.dungeon(), loaded.title());
            loaded.warnings().forEach(result::add);
        }
        gameState.save(userId, save);
        return toResult(save, result);
    }

    public ActionResult look(long userId) {
        SaveGame save = gameState.load(userId);
        ActionResult guard = requireDelving(save);
        if (guard != null) {
            return guard;
        }
        return toResult(save, exploration.look(save.getSession()));
    }

    public ActionResult move(long userId, String direction) {
        SaveGame save = gameState.load(userId);
        ActionResult guard = requireDelving(save);
        if (guard != null) {
            return guard;
        }
        String arg = direction == null ? "" : direction.trim().toLowerCase();
        ExplorationResult result;
        if (arg.equals("up") || arg.equals("down")) {
            result = exploration.useStairs(save, arg.equals("down"));
        } else {
            Direction dir = Direction.parse(arg);
            if (dir == null) {
                return fail(save, "Move where? north/south/east/west, or up/down at stairs.");
            }
            result = exploration.move(save, dir);
        }
        gameState.save(userId, save);
        return toResult(save, result);
    }

    public ActionResult search(long userId) {
        SaveGame save = gameState.load(userId);
        ActionResult guard = requireDelving(save);
        if (guard != null) {
            return guard;
        }
        ExplorationResult result = exploration.search(save);
        gameState.save(userId, save);
        return toResult(save, result);
    }

    public ActionResult rest(long userId) {
        SaveGame save = gameState.load(userId);
        ActionResult guard = requireDelving(save);
        if (guard != null) {
            return guard;
        }
        ExplorationResult result = exploration.rest(save);
        gameState.save(userId, save);
        return toResult(save, result);
    }

    public ActionResult open(long userId, String direction) {
        SaveGame save = gameState.load(userId);
        ActionResult guard = requireDelving(save);
        if (guard != null) {
            return guard;
        }
        Direction dir = Direction.parse(direction);
        if (dir == null) {
            return fail(save, "Open which door? north/south/east/west.");
        }
        ExplorationResult result = exploration.open(save, dir);
        gameState.save(userId, save);
        return toResult(save, result);
    }

    // --- combat -----------------------------------------------------------

    /** {@code pcName} names the acting PC in a multi-PC party (null/blank defaults to the primary),
        same as {@code /attack [pc-name] [n]} — passed straight through as {@link CombatService}'s own
        actor-token, which already resolves/defaults it identically to the Discord path. */
    public ActionResult attack(long userId, String pcName, Integer target) {
        SaveGame save = gameState.load(userId);
        ActionResult guard = requireDelving(save);
        if (guard != null) {
            return guard;
        }
        ExplorationResult result = combat.attackRound(save, blankToNull(pcName), target);
        gameState.save(userId, save);
        return toResult(save, result);
    }

    public ActionResult flee(long userId) {
        SaveGame save = gameState.load(userId);
        ActionResult guard = requireDelving(save);
        if (guard != null) {
            return guard;
        }
        ExplorationResult result = combat.flee(save);
        gameState.save(userId, save);
        return toResult(save, result);
    }

    // --- spells & potions -------------------------------------------------

    /** {@code pcName} names the acting/casting PC (null/blank defaults to the primary), same as
        {@code /cast [pc-name] <spell> [target]}. The in-combat branch passes it straight through as
        {@link CombatService}'s actor-token (same as {@link #attack}); the out-of-combat branch resolves
        it to a {@link Character} via {@link #resolveActor} since {@link SpellService#castOutOfCombat}
        takes one directly. */
    public ActionResult cast(long userId, String pcName, String spellName, Integer target) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        Spell spell = Spell.parse(spellName);
        if (spell == null) {
            return fail(save, "Unknown spell '" + spellName + "'.");
        }
        ExplorationResult result;
        if (save.getSession().getState() == SessionState.IN_COMBAT) {
            result = combat.castRound(save, blankToNull(pcName), spell, target);
        } else {
            Character actor = resolveActor(save, pcName);
            if (actor == null) {
                return fail(save, "No character named '" + pcName + "'.");
            }
            result = spells.castOutOfCombat(actor, spell, save.getSession());
        }
        gameState.save(userId, save);
        return toResult(save, result);
    }

    public ActionResult prepare(long userId, String pcName, String spellName) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        Character actor = resolveActor(save, pcName);
        if (actor == null) {
            return fail(save, "No character named '" + pcName + "'.");
        }
        if (!SpellTables.isCaster(actor.getCharacterClass())) {
            return fail(save, "Your class cannot cast spells.");
        }
        if (save.getSession().getState() == SessionState.IN_COMBAT) {
            return fail(save, "You cannot prepare spells mid-fight.");
        }
        Spell spell = Spell.parse(spellName);
        if (spell == null) {
            return fail(save, "Unknown spell '" + spellName + "'.");
        }
        ExplorationResult result = spells.prepare(actor, spell);
        gameState.save(userId, save);
        return toResult(save, result);
    }

    public ActionResult quaff(long userId, String pcName) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        Character c = resolveActor(save, pcName);
        if (c == null) {
            return fail(save, "No character named '" + pcName + "'.");
        }
        if (c.getHealingPotions() <= 0) {
            return fail(save, "You have no potions of healing.");
        }
        c.setHealingPotions(c.getHealingPotions() - 1);
        int before = c.getCurrentHp();
        c.setCurrentHp(Math.min(c.getMaxHp(), c.getCurrentHp() + HEALING_POTION.roll(dice)));
        gameState.save(userId, save);
        return ok(save, "You quaff a potion and recover " + (c.getCurrentHp() - before) + " hp ("
                + c.getCurrentHp() + "/" + c.getMaxHp() + ").");
    }

    // --- town & retainers -------------------------------------------------

    public ActionResult town(long userId) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        if (save.getSession().getState() == SessionState.IN_COMBAT) {
            return fail(save, "You can't stroll back to town mid-fight! Flee first.");
        }
        ExplorationResult result = town.returnToTown(save);
        gameState.save(userId, save);
        return toResult(save, result);
    }

    public ActionResult hire(long userId, String pcName, String classToken, String name) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        if (save.getSession().isInDungeon()) {
            return fail(save, "You can only hire retainers in town.");
        }
        Character pc = resolveActor(save, pcName);
        if (pc == null) {
            return fail(save, "No character named '" + pcName + "'.");
        }
        int max = save.retainerCapFor(pc);
        if (save.atRetainerCap(pc)) {
            return fail(save, "Your Charisma supports at most " + max + " retainers.");
        }
        CharacterClass cls = CharacterClass.parse(classToken);
        if (cls == null) {
            return fail(save, "Unknown class for the retainer.");
        }
        int fee = gameProps.getRetainerHiringFee();
        if (pc.getGold() < fee) {
            return fail(save, "You need a " + fee + " gp advance; you have " + pc.getGold() + ".");
        }
        String hireName = blankToNull(name) != null ? name.trim() : cls.displayName() + " hireling";
        int loyalty = RetainerRules.baseLoyalty(pc.getAbilities().score(Ability.CHA));
        Retainer retainer = retainerFactory.create(hireName, cls, 1, loyalty);
        retainer.setOwner(pc.getName());
        pc.setGold(pc.getGold() - fee);
        save.getRetainers().add(retainer);
        gameState.save(userId, save);
        return ok(save, hireName + ", a level 1 " + cls.displayName() + ", joins you (" + retainer.getMaxHp()
                + " hp, AC " + retainer.armorClass() + ", loyalty " + loyalty + ").");
    }

    /** Reuses {@link PartySummary#text}, the exact same rendering Discord's {@code /party} uses — every
        PC (not just the primary) grouped with their own retainers, plus the mule's status line — so the
        web view can never drift from Discord's the way the old hand-rolled single-PC listing here did. */
    public ActionResult party(long userId) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        List<String> lines = List.of(PartySummary.text(save).split("\n"));
        return ActionResult.of(true, lines, StateSnapshot.of(save));
    }

    public ActionResult dismiss(long userId, String pcName, String name) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter() || save.getRetainers().isEmpty()) {
            return fail(save, "You have no retainers to dismiss.");
        }
        Retainer match = save.getRetainers().stream()
                .filter(r -> r.getName().equalsIgnoreCase(name == null ? "" : name.trim()))
                .findFirst()
                .orElse(null);
        if (match == null) {
            return fail(save, "No retainer named '" + name + "'.");
        }
        Character pc = resolveActor(save, pcName);
        if (pc == null) {
            return fail(save, "No character named '" + pcName + "'.");
        }
        if (save.ownerOf(match) != pc) {
            return fail(save, "'" + match.getName() + "' is " + save.ownerOf(match).getName()
                    + "'s retainer, not yours.");
        }
        save.getRetainers().remove(match);
        gameState.save(userId, save);
        return ok(save, "You release " + match.getName() + " from service.");
    }

    // --- mule ---------------------------------------------------------------
    // Mirrors MuleCommand exactly, using resolveActor(pcName) in place of peelLeadingPcName since the
    // web layer already has a structured request field instead of free text to parse a token out of.

    public ActionResult buyMule(long userId, String pcName, String name) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        if (save.getSession().isInDungeon()) {
            return fail(save, "You can only buy a mule in town.");
        }
        if (save.getMules().size() >= SaveGame.MAX_MULES) {
            return fail(save, "Your party already has a mule — only one may come along at a time.");
        }
        Character payer = resolveActor(save, pcName);
        if (payer == null) {
            return fail(save, "No character named '" + pcName + "'.");
        }
        if (payer.getGold() < MuleRules.PURCHASE_COST_GP) {
            return fail(save, "You need " + MuleRules.PURCHASE_COST_GP + " gp to buy a mule; "
                    + payer.getName() + " has " + payer.getGold() + " gp.");
        }
        String muleName = blankToNull(name) != null ? name.trim() : "Mule";
        Mule mule = muleFactory.create(muleName);
        mule.setOwner(payer.getName());
        payer.setGold(payer.getGold() - MuleRules.PURCHASE_COST_GP);
        save.getMules().add(mule);
        mule.setHandler(muleService.pickEligibleHandler(save));
        gameState.save(userId, save);
        String handlerNote = mule.getHandler() != null
                ? " " + mule.getHandler() + " takes the lead."
                : " Nobody has a free hand to lead it yet.";
        return ok(save, muleName + " (AC " + mule.armorClass() + ", " + mule.getMaxHp() + " hp) joins "
                + payer.getName() + "'s party for " + MuleRules.PURCHASE_COST_GP + " gp." + handlerNote);
    }

    public ActionResult loadMule(long userId, String pcName, int gold) {
        return transferMule(userId, pcName, gold, true);
    }

    public ActionResult unloadMule(long userId, String pcName, int gold) {
        return transferMule(userId, pcName, gold, false);
    }

    private ActionResult transferMule(long userId, String pcName, int amount, boolean loading) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        Mule mule = save.getMule();
        if (mule == null) {
            return fail(save, "You don't have a mule.");
        }
        Character pc = resolveActor(save, pcName);
        if (pc == null) {
            return fail(save, "No character named '" + pcName + "'.");
        }
        if (amount <= 0) {
            return fail(save, "Amount must be a positive number of gold.");
        }
        if (loading) {
            int moved = muleService.load(mule, pc, amount);
            if (moved == 0) {
                return fail(save, mule.getName() + " can't carry any more, or " + pc.getName() + " has no gold to load.");
            }
            gameState.save(userId, save);
            return ok(save, pc.getName() + " loads " + moved + " gp onto " + mule.getName() + " ("
                    + mule.getCarriedGold() + "/" + MuleRules.CAPACITY_MAX_CNS + " gp, "
                    + MuleRules.descriptor(mule.getCarriedGold()) + ").");
        }
        int moved = muleService.unload(mule, pc, amount);
        if (moved == 0) {
            return fail(save, mule.getName() + " isn't carrying any gold to unload.");
        }
        gameState.save(userId, save);
        return ok(save, pc.getName() + " unloads " + moved + " gp from " + mule.getName() + " ("
                + mule.getCarriedGold() + "/" + MuleRules.CAPACITY_MAX_CNS + " gp left on it).");
    }

    public ActionResult muleHandler(long userId, String name) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        Mule mule = save.getMule();
        if (mule == null) {
            return fail(save, "You don't have a mule.");
        }
        String failure = muleService.assignHandler(save, mule, name);
        if (failure != null) {
            return fail(save, failure);
        }
        gameState.save(userId, save);
        return ok(save, mule.getHandler() + " now leads " + mule.getName() + ".");
    }

    // --- bank ---------------------------------------------------------------
    // Mirrors the mule load/unload pair exactly, using resolveActor(pcName) the same way.

    public ActionResult bankDeposit(long userId, String pcName, int gold) {
        return transferBank(userId, pcName, gold, true);
    }

    public ActionResult bankWithdraw(long userId, String pcName, int gold) {
        return transferBank(userId, pcName, gold, false);
    }

    private ActionResult transferBank(long userId, String pcName, int amount, boolean depositing) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        if (save.getSession().isInDungeon()) {
            return fail(save, "You can only reach the bank in town.");
        }
        Character pc = resolveActor(save, pcName);
        if (pc == null) {
            return fail(save, "No character named '" + pcName + "'.");
        }
        if (amount <= 0) {
            return fail(save, "Amount must be a positive number of gold.");
        }
        if (depositing) {
            BankService.DepositResult result = bankService.deposit(pc, amount);
            if (result.deposited() == 0) {
                return fail(save, pc.getName() + " has no gold on hand to deposit.");
            }
            gameState.save(userId, save);
            return ok(save, pc.getName() + " deposits " + result.deposited() + " gp (a " + result.fee()
                    + " gp fee, " + result.credited() + " gp credited) — " + pc.getBankedGold()
                    + " gp now banked, " + pc.getGold() + " gp on hand.");
        }
        int moved = bankService.withdraw(pc, amount);
        if (moved == 0) {
            return fail(save, pc.getName() + " has no gold banked to withdraw.");
        }
        gameState.save(userId, save);
        return ok(save, pc.getName() + " withdraws " + moved + " gp from the bank — " + pc.getGold()
                + " gp on hand, " + pc.getBankedGold() + " gp still banked.");
    }

    // --- export & DM batch tools (stateless; do not touch the caller's save) ---

    public String exportText(long userId, String pcName) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return null;
        }
        Character pc = resolveActor(save, pcName);
        return pc == null ? null : CharacterSheets.textBlock(pc);
    }

    public Map<String, Object> exportJson(long userId, String pcName) {
        return character(userId, pcName);
    }

    public List<String> listModules() {
        return ModuleLoader.listModules();
    }

    public List<Map<String, Object>> roster(int count, int level, String classToken) {
        CharacterClass fixed = blankToNull(classToken) == null ? null : CharacterClass.parse(classToken);
        List<Character> party = pregen.createBatch(Math.max(1, Math.min(count, 8)), level, fixed, null);
        return party.stream().map(PregenExport::of).toList();
    }

    public Map<String, Object> npc(int level, String classToken, String name) {
        CharacterClass cls = CharacterClass.parse(classToken);
        if (cls == null) {
            return null;
        }
        String npcName = blankToNull(name) != null ? name.trim() : cls.displayName() + " NPC";
        return PregenExport.of(pregen.create(npcName, cls, Math.max(1, Math.min(level, pregen.maxLevel(cls)))));
    }

    // --- helpers ----------------------------------------------------------

    /** Resolves {@code pcName} to a specific PC in a multi-PC party (null/blank defaults to the
        primary, first-rolled PC) — the web layer's equivalent of {@link SaveGame#peelLeadingPcName},
        adapted for a structured request field instead of a leading token in free text. Returns
        {@code null} if a name was given but doesn't resolve to a live PC, same as Discord commands
        reporting "no character named X" rather than silently falling back to the primary. */
    private Character resolveActor(SaveGame save, String pcName) {
        if (blankToNull(pcName) == null) {
            return save.getCharacter();
        }
        return save.resolve(pcName) instanceof Character c ? c : null;
    }

    public ActionResult undo(long userId) {
        SaveGame undone = gameState.undo(userId);
        if (undone == null) {
            SaveGame current = gameState.load(userId);
            return fail(current, "No history available to undo.");
        }
        ExplorationResult res = ExplorationResult.of("🔮 **Time Rewound**: Rolled back the last action.");
        return toResult(undone, res);
    }

    private ActionResult requireDelving(SaveGame save) {
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        if (!save.getSession().isInDungeon()) {
            return fail(save, "You're in town. Use enter to begin a delve.");
        }
        return null;
    }

    private ActionResult toResult(SaveGame save, ExplorationResult er) {
        return ActionResult.of(er.isSuccess(), er.getLines(), StateSnapshot.of(save));
    }

    private ActionResult ok(SaveGame save, String line) {
        return ActionResult.of(true, List.of(line), StateSnapshot.of(save));
    }

    private ActionResult ok(long userId, String line) {
        return ok(gameState.load(userId), line);
    }

    private ActionResult fail(SaveGame save, String line) {
        return ActionResult.of(false, List.of(line), StateSnapshot.of(save));
    }

    private ActionResult fail(long userId, String line) {
        return fail(gameState.load(userId), line);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
