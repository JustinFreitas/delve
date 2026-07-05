package dev.freitas.delve.api;

import dev.freitas.delve.api.dto.ActionResult;
import dev.freitas.delve.api.dto.StateSnapshot;
import dev.freitas.delve.command.CharacterSheets;
import dev.freitas.delve.data.GameStateService;
import dev.freitas.delve.game.CharacterFactory;
import dev.freitas.delve.game.PregenExport;
import dev.freitas.delve.game.PregenService;
import dev.freitas.delve.game.RetainerFactory;
import dev.freitas.delve.game.dungeon.ModuleLoader;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.RetainerRules;
import dev.freitas.delve.game.engine.Spell;
import dev.freitas.delve.game.engine.SpellTables;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.ExplorationService;
import dev.freitas.delve.game.session.SpellService;
import dev.freitas.delve.game.session.TownService;
import java.util.ArrayList;
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

    private static final int HIRING_FEE = 25;
    private static final DamageRoll HEALING_POTION = new DamageRoll(2, 4, 2);

    private final GameStateService gameState;
    private final ExplorationService exploration;
    private final CombatService combat;
    private final SpellService spells;
    private final TownService town;
    private final PregenService pregen;
    private final RetainerFactory retainerFactory;
    private final CharacterFactory characterFactory;
    private final Dice dice;

    public GameFacade(
            GameStateService gameState,
            ExplorationService exploration,
            CombatService combat,
            SpellService spells,
            TownService town,
            PregenService pregen,
            RetainerFactory retainerFactory,
            CharacterFactory characterFactory,
            Dice dice) {
        this.gameState = gameState;
        this.exploration = exploration;
        this.combat = combat;
        this.spells = spells;
        this.town = town;
        this.pregen = pregen;
        this.retainerFactory = retainerFactory;
        this.characterFactory = characterFactory;
        this.dice = dice;
    }

    // --- state & character ------------------------------------------------

    public StateSnapshot state(long userId) {
        return StateSnapshot.of(gameState.load(userId));
    }

    /** The current character as a clean B/X stat-block map, or null if none. */
    public Map<String, Object> character(long userId) {
        SaveGame save = gameState.load(userId);
        return save.hasCharacter() ? PregenExport.of(save.getCharacter()) : null;
    }

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
        Character character = characterFactory.create(pcName, cls, abilities);
        spells.autoPrepare(character);
        gameState.mutate(userId, s -> s.setCharacter(character));
        return ok(userId, "Rolled up " + pcName + ", a level 1 " + cls.displayName() + "!");
    }

    public ActionResult pregen(long userId, String classToken, int level, String name) {
        CharacterClass cls = CharacterClass.parse(classToken);
        if (cls == null) {
            return fail(userId, "Unknown class.");
        }
        if (level < 1 || level > pregen.maxLevel(cls)) {
            return fail(userId, "Level must be 1.." + pregen.maxLevel(cls) + " for a " + cls.displayName() + ".");
        }
        String pcName = blankToNull(name) != null ? name.trim() : cls.displayName();
        Character character = pregen.create(pcName, cls, level);
        gameState.mutate(userId, s -> s.setCharacter(character));
        return ok(userId, "Generated " + pcName + ", a level " + level + " " + cls.displayName() + ".");
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

    public ActionResult attack(long userId, Integer target) {
        SaveGame save = gameState.load(userId);
        ActionResult guard = requireDelving(save);
        if (guard != null) {
            return guard;
        }
        ExplorationResult result = combat.attackRound(save, target);
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

    public ActionResult cast(long userId, String spellName, Integer target) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        Spell spell = Spell.parse(spellName);
        if (spell == null) {
            return fail(save, "Unknown spell '" + spellName + "'.");
        }
        ExplorationResult result = save.getSession().getState() == SessionState.IN_COMBAT
                ? combat.castRound(save, spell, target)
                : spells.castOutOfCombat(save.getCharacter(), spell);
        gameState.save(userId, save);
        return toResult(save, result);
    }

    public ActionResult prepare(long userId, String spellName) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        if (!SpellTables.isCaster(save.getCharacter().getCharacterClass())) {
            return fail(save, "Your class cannot cast spells.");
        }
        if (save.getSession().getState() == SessionState.IN_COMBAT) {
            return fail(save, "You cannot prepare spells mid-fight.");
        }
        Spell spell = Spell.parse(spellName);
        if (spell == null) {
            return fail(save, "Unknown spell '" + spellName + "'.");
        }
        ExplorationResult result = spells.prepare(save.getCharacter(), spell);
        gameState.save(userId, save);
        return toResult(save, result);
    }

    public ActionResult quaff(long userId) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        Character c = save.getCharacter();
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

    public ActionResult hire(long userId, String classToken, String name) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        if (save.getSession().isInDungeon()) {
            return fail(save, "You can only hire retainers in town.");
        }
        Character pc = save.getCharacter();
        int max = RetainerRules.maxRetainers(pc.getAbilities().score(Ability.CHA));
        if (save.getRetainers().size() >= max) {
            return fail(save, "Your Charisma supports at most " + max + " retainers.");
        }
        CharacterClass cls = CharacterClass.parse(classToken);
        if (cls == null) {
            return fail(save, "Unknown class for the retainer.");
        }
        if (pc.getGold() < HIRING_FEE) {
            return fail(save, "You need a " + HIRING_FEE + " gp advance; you have " + pc.getGold() + ".");
        }
        String hireName = blankToNull(name) != null ? name.trim() : cls.displayName() + " hireling";
        int loyalty = RetainerRules.baseLoyalty(pc.getAbilities().score(Ability.CHA));
        Retainer retainer = retainerFactory.create(hireName, cls, 1, loyalty);
        pc.setGold(pc.getGold() - HIRING_FEE);
        save.getRetainers().add(retainer);
        gameState.save(userId, save);
        return ok(save, hireName + ", a level 1 " + cls.displayName() + ", joins you (" + retainer.getMaxHp()
                + " hp, AC " + retainer.armorClass() + ", loyalty " + loyalty + ").");
    }

    public ActionResult party(long userId) {
        SaveGame save = gameState.load(userId);
        if (!save.hasCharacter()) {
            return fail(save, "Roll a character first.");
        }
        Character pc = save.getCharacter();
        List<String> lines = new ArrayList<>();
        lines.add(pc.getName() + " (you) — L" + pc.getLevel() + " " + pc.getCharacterClass().displayName()
                + ", " + pc.getCurrentHp() + "/" + pc.getMaxHp() + " hp, AC " + pc.armorClass());
        for (Retainer r : save.getRetainers()) {
            lines.add(r.getName() + " — L" + r.getLevel() + " " + r.getCharacterClass().displayName()
                    + ", " + r.getCurrentHp() + "/" + r.getMaxHp() + " hp, AC " + r.armorClass()
                    + ", loyalty " + r.getLoyalty() + " (" + RetainerRules.loyaltyDescriptor(r.getLoyalty()) + ")");
        }
        return ActionResult.of(true, lines, StateSnapshot.of(save));
    }

    public ActionResult dismiss(long userId, String name) {
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
        save.getRetainers().remove(match);
        gameState.save(userId, save);
        return ok(save, "You release " + match.getName() + " from service.");
    }

    // --- export & DM batch tools (stateless; do not touch the caller's save) ---

    public String exportText(long userId) {
        SaveGame save = gameState.load(userId);
        return save.hasCharacter() ? CharacterSheets.textBlock(save.getCharacter()) : null;
    }

    public Map<String, Object> exportJson(long userId) {
        return character(userId);
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
