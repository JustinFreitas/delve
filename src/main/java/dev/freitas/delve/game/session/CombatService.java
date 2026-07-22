package dev.freitas.delve.game.session;

import dev.freitas.delve.game.Bestiary;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.Advanceable;
import dev.freitas.delve.game.engine.AttackResolver;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Encumbrance;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Formation;
import dev.freitas.delve.game.engine.Leveling;
import dev.freitas.delve.game.engine.MuleRules;
import dev.freitas.delve.game.engine.RangeBand;
import dev.freitas.delve.game.engine.RangedAttack;
import dev.freitas.delve.game.engine.ReactionTier;
import dev.freitas.delve.game.engine.SavingThrows;
import dev.freitas.delve.game.engine.Spell;
import dev.freitas.delve.game.engine.WeaponCatalog;
import dev.freitas.delve.game.engine.WeaponCatalog.WeaponProfile;
import dev.freitas.delve.game.engine.WeaponClass;
import dev.freitas.delve.game.model.AttackEffect;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.CombatEncounter;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.GameSession;
import dev.freitas.delve.game.model.Monster;
import dev.freitas.delve.game.model.MonsterType;
import dev.freitas.delve.game.model.Mule;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The B/X combat loop for the whole party (player character + retainers). Each call to
 * {@link #attackRound} resolves one round: side initiative (1d6 per side), the party's attacks and
 * the monsters' attacks (spread across random party members), then loyalty and morale checks.
 * Victory splits XP into shares (retainers take half-shares); the delve ends only if the player dies.
 */
@Service
public class CombatService {

    private final Dice dice;
    private final SpellService spells;
    private final ContainerService containers;

    /** Test convenience: a plain {@code new ContainerService()} has no state of its own worth mocking. */
    public CombatService(Dice dice, SpellService spells) {
        this(dice, spells, new ContainerService());
    }

    @Autowired
    public CombatService(Dice dice, SpellService spells, ContainerService containers) {
        this.dice = dice;
        this.spells = spells;
        this.containers = containers;
    }

    /** B/X reaction: undead always attack; otherwise the 5-tier 2d6 + CHA modifier table (only 5 or
        less is hostile — most encounters are avoidable, per the house rule). */
    public ReactionTier reaction(Character character, MonsterType type) {
        if (type.undead()) {
            return ReactionTier.ATTACKS;
        }
        int roll = dice.roll2d6() + character.getAbilities().modifier(Ability.CHA);
        if (roll <= 2) {
            return ReactionTier.ATTACKS;
        }
        if (roll <= 5) {
            return ReactionTier.HOSTILE;
        }
        if (roll <= 8) {
            return ReactionTier.UNCERTAIN;
        }
        if (roll <= 11) {
            return ReactionTier.INDIFFERENT;
        }
        return ReactionTier.FRIENDLY;
    }

    /** Rolls the monster group's hit points, surprise, and starting range; enters combat in the room. */
    public ExplorationResult startCombat(SaveGame save) {
        GameSession session = save.getSession();
        Room room = session.currentRoom();
        MonsterType type = Bestiary.byName(room.getMonsterName());

        CombatEncounter encounter = new CombatEncounter();
        encounter.setMonsterName(type.name());
        encounter.setInitialCount(room.getMonsterCount());
        List<Monster> monsters = new ArrayList<>();
        for (int i = 0; i < room.getMonsterCount(); i++) {
            monsters.add(Monster.roll(type, dice));
        }
        encounter.setMonsters(monsters);

        // Surprise (2-in-6 each side) and starting engagement range are rolled once, at the top of the fight.
        encounter.setPartySurprised(dice.d(6) <= 2);
        encounter.setMonstersSurprised(dice.d(6) <= 2);
        encounter.setDistanceFeet(initialEncounterDistance(save, room));
        room.setFreshEncounter(false);

        session.setCombat(encounter);
        session.setState(SessionState.IN_COMBAT);

        ExplorationResult result = new ExplorationResult();
        result.add("**Combat!** " + room.getMonsterCount() + " "
                + type.name().toLowerCase() + (room.getMonsterCount() > 1 ? "s" : "")
                + " attack! (`attack [n]` to strike, `flee` to run.)");
        if (encounter.isPartySurprised()) {
            result.add("**You are caught flat-footed!**");
        }
        if (encounter.isMonstersSurprised()) {
            result.add("**You catch them by surprise!**");
        }
        if (!encounter.isMelee()) {
            result.add("Range: " + encounter.getDistanceFeet() + " ft.");
        }
        containers.reconcileHeldContainers(save, result);
        return result;
    }

    /** A wandering encounter rolls the classic B/X 2d6×10' distance; a room-based one starts at the
        Short-range ceiling of the first missile-armed party member (or a flat 30 ft with none). */
    private int initialEncounterDistance(SaveGame save, Room room) {
        if (room.isFreshEncounter()) {
            return dice.roll(2, 6) * 10;
        }
        for (Combatant c : save.livingInOrder()) {
            WeaponProfile profile = WeaponCatalog.classify(c.getMainWeapon());
            if (profile.weaponClass() == WeaponClass.MISSILE) {
                return profile.rangeTable().shortFeet();
            }
            WeaponProfile secondary = secondaryMissileProfile(c);
            if (secondary != null) {
                return secondary.rangeTable().shortFeet();
            }
        }
        return 30;
    }

    /** A retainer's carried missile weapon (e.g. a melee-equipped Fighter's sling), classified only if
        it actually resolves to {@link WeaponClass#MISSILE} — {@code null} otherwise or if absent. */
    private WeaponProfile secondaryMissileProfile(Combatant attacker) {
        if (!(attacker instanceof Retainer r) || r.getSecondaryWeapon() == null) {
            return null;
        }
        WeaponProfile profile = WeaponCatalog.classify(r.getSecondaryWeapon());
        return profile.weaponClass() == WeaponClass.MISSILE ? profile : null;
    }

    /** Resolves one round of combat; starts the fight first if the player initiated it. Defaults to the
        primary PC as the acting character — see {@link #attackRound(SaveGame, String, Integer, boolean)}. */
    public ExplorationResult attackRound(SaveGame save, Integer targetIndex) {
        return attackRound(save, null, targetIndex, false);
    }

    /** As {@link #attackRound(SaveGame, Integer)}, but with {@code verbose} showing the raw XP/prime-
        requisite breakdown behind a victory's award (used by {@code /autodelve}'s verbose log detail). */
    public ExplorationResult attackRound(SaveGame save, Integer targetIndex, boolean verbose) {
        return attackRound(save, null, targetIndex, verbose);
    }

    /** As {@link #attackRound(SaveGame, Integer)}, but with an explicit acting PC (for multi-PC parties;
        {@code null} defaults to the primary PC). Every other living PC and all retainers still auto-attack
        this round via {@link #partyAttacks}. */
    public ExplorationResult attackRound(SaveGame save, String actorToken, Integer targetIndex) {
        return attackRound(save, actorToken, targetIndex, false);
    }

    /** As above, with the verbose XP flag. */
    public ExplorationResult attackRound(SaveGame save, String actorToken, Integer targetIndex, boolean verbose) {
        Character actor = resolveActor(save, actorToken);
        if (actor == null) {
            return ExplorationResult.failure("Don't recognize that character.");
        }
        GameSession session = save.getSession();
        if (session.getState() != SessionState.IN_COMBAT) {
            if (session.currentRoom().hasLiveMonster()) {
                return continueRound(save, actor, targetIndex, startCombat(save), verbose);
            }
            return ExplorationResult.failure("There is nothing here to attack.");
        }
        return continueRound(save, actor, targetIndex, new ExplorationResult(), verbose);
    }

    /** Resolves {@code actorToken} to the acting PC ({@code null} means the primary PC); also returns
        {@code null} if the token doesn't name a living Character (e.g. it names a retainer instead). */
    private Character resolveActor(SaveGame save, String actorToken) {
        if (actorToken == null) {
            return save.getCharacter();
        }
        Combatant resolved = save.resolve(actorToken);
        return resolved instanceof Character c ? c : null;
    }

    /** Casts a combat spell as the acting PC's action for one round (starting the fight if needed). */
    public ExplorationResult castRound(SaveGame save, Spell spell, Integer targetIndex) {
        return castRound(save, null, spell, targetIndex);
    }

    /** As above, with an explicit acting PC ({@code null} defaults to the primary PC). */
    public ExplorationResult castRound(SaveGame save, String actorToken, Spell spell, Integer targetIndex) {
        Character actor = resolveActor(save, actorToken);
        if (actor == null) {
            return ExplorationResult.failure("Don't recognize that character.");
        }
        if (!spells.isMemorized(actor, spell)) {
            return ExplorationResult.failure("You don't have **" + spell.displayName() + "** prepared.");
        }
        GameSession session = save.getSession();
        if (session.getState() != SessionState.IN_COMBAT && !session.currentRoom().hasLiveMonster()) {
            return ExplorationResult.failure("There is nothing here to target.");
        }
        ExplorationResult result =
                session.getState() != SessionState.IN_COMBAT ? startCombat(save) : new ExplorationResult();
        return resolveRound(save, result, () -> {
            applyPlayerSpell(save, actor, spell, targetIndex, result);
            othersAttack(save, actor, result);
        }, false);
    }

    /** Attempts to turn an undead encounter as a Cleric PC's action for the round (starting the fight if
        needed). Cleric-only — retainer Clerics don't turn undead in this pass. Encounters are always a
        single monster type per room, so a successful turn breaks the whole group's morale at once via
        the existing broken-morale -> {@link #victory} flow. */
    public ExplorationResult turnRound(SaveGame save) {
        return turnRound(save, null);
    }

    /** As above, with an explicit acting PC ({@code null} defaults to the primary PC). */
    public ExplorationResult turnRound(SaveGame save, String actorToken) {
        Character actor = resolveActor(save, actorToken);
        if (actor == null) {
            return ExplorationResult.failure("Don't recognize that character.");
        }
        if (actor.getCharacterClass() != CharacterClass.CLERIC) {
            return ExplorationResult.failure("Only a Cleric can turn undead.");
        }
        GameSession session = save.getSession();
        boolean alreadyFighting = session.getState() == SessionState.IN_COMBAT;
        MonsterType type;
        if (alreadyFighting) {
            List<Monster> alive = session.getCombat().aliveMonsters();
            type = alive.isEmpty() ? null : alive.get(0).getType();
        } else if (session.currentRoom().hasLiveMonster()) {
            type = Bestiary.byName(session.currentRoom().getMonsterName());
        } else {
            type = null;
        }
        if (type == null || !type.undead()) {
            return ExplorationResult.failure("There's nothing undead here to turn.");
        }
        ExplorationResult result = alreadyFighting ? new ExplorationResult() : startCombat(save);
        return resolveRound(save, result, () -> {
            applyTurnUndead(save, actor, session.getCombat(), result);
            othersAttack(save, actor, result);
        }, false);
    }

    /** Rolls 2d6 + 2x(cleric level - undead Hit Dice) vs. a flat target of 9. */
    private void applyTurnUndead(SaveGame save, Character actor, CombatEncounter encounter, ExplorationResult result) {
        MonsterType type = encounter.aliveMonsters().get(0).getType();
        int roll = dice.roll2d6() + 2 * (actor.getLevel() - type.hitDiceCount());
        boolean secondPerson = save.getCharacters().size() == 1;
        if (roll >= 9) {
            encounter.setMoraleBroken(true);
            result.add((secondPerson ? "You turn" : actor.getName() + " turns") + " the undead — they flee in terror!");
        } else {
            result.add((secondPerson ? "Your" : actor.getName() + "'s") + " holy symbol flares, but the dead do not flee.");
        }
    }

    private ExplorationResult continueRound(SaveGame save, Character actor, Integer targetIndex, ExplorationResult result, boolean verbose) {
        return resolveRound(save, result, () -> partyAttacks(save, actor, targetIndex, result), verbose);
    }

    /** Runs one round given the player's chosen action, with side initiative and the aftermath checks.
        Defeat is whole-party (no living PC remains), not any single PC falling — critical for multi-PC
        parties, where the first PC to fall must not end the delve while others still stand. */
    private ExplorationResult resolveRound(SaveGame save, ExplorationResult result, Runnable playerAction, boolean verbose) {
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setRound(encounter.getRound() + 1);
        result.add("__Round " + encounter.getRound() + "__");

        boolean partyActs = !encounter.isPartySurprised();
        boolean monstersActEligible = !encounter.isMonstersSurprised();

        if (!encounter.isMelee()) {
            closeDistance(encounter, result);
        }

        boolean playerFirst = dice.d(6) >= dice.d(6); // side initiative, ties to the player
        if (playerFirst) {
            if (partyActs) {
                playerAction.run();
            }
            if (!encounter.isOver() && monstersActEligible) {
                monstersAct(save, result);
            }
        } else {
            if (monstersActEligible) {
                monstersAct(save, result);
            }
            if (!save.livingCharacters().isEmpty() && !encounter.aliveMonsters().isEmpty() && partyActs) {
                playerAction.run();
            }
        }
        // Surprise only ever applies to round 1.
        encounter.setPartySurprised(false);
        encounter.setMonstersSurprised(false);

        if (save.livingCharacters().isEmpty()) {
            return defeat(save, result);
        }
        loyaltyChecks(save, result);
        checkMorale(save, encounter, result);
        if (encounter.isOver()) {
            return victory(save, result, verbose);
        }
        result.add("");
        result.add(status(save));
        return result;
    }

    /** While still outside melee range, the monsters close at their move rate (encounter movement is
        conventionally 1/3 of the listed dungeon move rate per round). */
    private void closeDistance(CombatEncounter encounter, ExplorationResult result) {
        List<Monster> alive = encounter.aliveMonsters();
        if (alive.isEmpty()) {
            return;
        }
        int closing = alive.get(0).getType().moveRate() / 3;
        encounter.setDistanceFeet(encounter.getDistanceFeet() - closing);
        result.add(encounter.isMelee() ? "*They are on you!*"
                : "*They close to " + encounter.getDistanceFeet() + " ft.*");
    }

    private void partyAttacks(SaveGame save, Character actor, Integer targetIndex, ExplorationResult result) {
        if (actor.isAlive()) {
            act(save, actor, targetIndex, result);
        }
        othersAttack(save, actor, result);
    }

    /** Every living PC other than {@code actingPc}, plus all retainers, auto-attacks — used both for the
        non-acting PCs during a normal attack round and for the whole rest of the party when the acting PC
        takes a special action instead (a spell, turning undead). */
    private void othersAttack(SaveGame save, Character actingPc, ExplorationResult result) {
        for (Character other : save.getCharacters()) {
            if (other != actingPc && other.isAlive()) {
                act(save, other, null, result);
            }
        }
        retainersAttack(save, result);
    }

    private void retainersAttack(SaveGame save, ExplorationResult result) {
        CombatEncounter encounter = save.getSession().getCombat();
        for (Retainer retainer : save.livingRetainers()) {
            if (!encounter.aliveMonsters().isEmpty()) {
                act(save, retainer, null, result);
            }
        }
    }

    /** Dispatches one combatant's action for the round, gated by rank/engagement and weapon class:
        melee while engaged (front of their column) or a REACH weapon at rank 2 past a living rank-1
        column-mate; missile fire from rank 2+ while not yet in melee range. Anyone ineligible gets a
        flavor line instead of silently doing nothing. */
    private void act(SaveGame save, Combatant attacker, Integer targetIndex, ExplorationResult result) {
        CombatEncounter encounter = save.getSession().getCombat();
        if (encounter.aliveMonsters().isEmpty()) {
            return;
        }
        List<Combatant> fullOrder = save.fullOrder();
        int width = save.getSession().currentRoom().getCorridorWidth();
        int rank = Formation.nominalRank(fullOrder, width, attacker);
        boolean engaged = Formation.isEngaged(fullOrder, width, attacker);
        WeaponProfile profile = WeaponCatalog.classify(attacker.getMainWeapon());
        boolean isPlayer = attacker instanceof Character;
        boolean solo = save.getCharacters().size() == 1;
        String name = isPlayer && solo ? "You" : attacker.getName();
        int fatiguePenalty = save.getSession().isFatigued() ? -1 : 0;

        if (encounter.isMelee()) {
            boolean canMelee = engaged || (rank == 2 && profile.weaponClass() == WeaponClass.REACH);
            if (canMelee) {
                meleeAttack(save, attacker, profile, encounter, targetIndex, fatiguePenalty, result);
            } else {
                result.add(name + " can't reach the fight from the " + rankLabel(rank) + " rank.");
            }
        } else if (rank >= 2 && profile.weaponClass() == WeaponClass.MISSILE) {
            missileAttack(save, attacker, profile, attacker.getMainWeaponDamage(), encounter, targetIndex,
                    fatiguePenalty, result);
        } else if (rank >= 2 && secondaryMissileProfile(attacker) != null) {
            // A melee-equipped retainer still carries a missile backup (e.g. a sling) — use it
            // pre-melee instead of standing idle.
            Retainer r = (Retainer) attacker;
            missileAttack(save, attacker, secondaryMissileProfile(attacker), r.getSecondaryWeaponDamage(),
                    encounter, targetIndex, fatiguePenalty, result);
        } else if (rank == 1) {
            result.add(name + " waits, weapon ready, for them to close.");
        } else {
            result.add(name + " can't get a clear shot from the " + rankLabel(rank) + " rank.");
        }
    }

    private String rankLabel(int rank) {
        return switch (rank) {
            case 1 -> "front";
            case 2 -> "second";
            case 3 -> "third";
            default -> rank + "th";
        };
    }

    /** Applies the acting PC's spell: a damaging bolt, a sleep effect, or in-combat healing. */
    private void applyPlayerSpell(SaveGame save, Character actor, Spell spell, Integer targetIndex, ExplorationResult result) {
        CombatEncounter encounter = save.getSession().getCombat();
        boolean secondPerson = save.getCharacters().size() == 1;
        String who = secondPerson ? "You" : actor.getName();
        spells.consume(actor, spell);
        switch (spell.effect()) {
            case DAMAGE -> {
                List<Monster> alive = encounter.aliveMonsters();
                if (alive.isEmpty()) {
                    return;
                }
                int index = (targetIndex != null && targetIndex >= 1 && targetIndex <= alive.size())
                        ? targetIndex - 1 : 0;
                Monster target = alive.get(index);
                int damage = SpellService.MAGIC_MISSILE_DAMAGE.roll(dice);
                target.takeDamage(damage);
                result.add(who + " " + (secondPerson ? "loose" : "looses") + " **" + spell.displayName()
                        + "** at the " + target.getType().name().toLowerCase()
                        + " for " + damage + " automatic damage"
                        + (target.isAlive() ? " (" + target.getCurrentHp() + " hp left)." : " — it is destroyed!"));
            }
            case SLEEP -> {
                int budget = dice.roll(2, 8); // 2d8 Hit Dice of creatures affected
                int slept = 0;
                List<Monster> byHd = new ArrayList<>(encounter.aliveMonsters());
                byHd.sort((a, b) -> Integer.compare(a.getType().hitDiceCount(), b.getType().hitDiceCount()));
                for (Monster m : byHd) {
                    int hd = Math.max(1, m.getType().hitDiceCount());
                    if (m.getType().hitDiceCount() <= 4 && budget >= hd) {
                        m.takeDamage(m.getMaxHp()); // falls asleep and is dispatched
                        budget -= hd;
                        slept++;
                    }
                }
                result.add(who + " " + (secondPerson ? "cast" : "casts") + " **" + spell.displayName() + "** — "
                        + slept + " enemy" + (slept == 1 ? "" : " enemies")
                        + " collapse" + (slept == 1 ? "s" : "") + " into helpless slumber.");
            }
            case HEAL -> {
                int healed = SpellService.CURE_LIGHT_WOUNDS.roll(dice);
                int before = actor.getCurrentHp();
                actor.setCurrentHp(Math.min(actor.getMaxHp(), actor.getCurrentHp() + healed));
                result.add(who + " " + (secondPerson ? "cast" : "casts") + " **" + spell.displayName()
                        + "**, recovering " + (actor.getCurrentHp() - before) + " hp.");
            }
            default -> result.add(who + " " + (secondPerson ? "cast" : "casts") + " **" + spell.displayName() + "**.");
        }
    }

    /** Fallback weapon for a missile-armed combatant forced into melee (front of a broken column). */
    private static final DamageRoll FALLBACK_DAGGER = new DamageRoll(1, 4);

    private int resolveTargetIndex(Integer targetIndex, int aliveCount) {
        return (targetIndex != null && targetIndex >= 1 && targetIndex <= aliveCount) ? targetIndex - 1 : 0;
    }

    private void meleeAttack(
            SaveGame save, Combatant attacker, WeaponProfile profile, CombatEncounter encounter, Integer targetIndex,
            int fatiguePenalty, ExplorationResult result) {
        List<Monster> alive = encounter.aliveMonsters();
        if (alive.isEmpty()) {
            return;
        }
        Monster target = alive.get(resolveTargetIndex(targetIndex, alive.size()));
        boolean isPlayer = attacker instanceof Character;
        boolean secondPerson = isPlayer && save.getCharacters().size() == 1;
        String who = secondPerson ? "You" : attacker.getName();
        String name = target.getType().name().toLowerCase();

        boolean fallbackDagger = profile.weaponClass() == WeaponClass.MISSILE;
        String weapon = fallbackDagger ? "a dagger"
                : (secondPerson ? "your " + attacker.getMainWeapon().toLowerCase() : attacker.getMainWeapon().toLowerCase());
        DamageRoll damageRoll = fallbackDagger ? FALLBACK_DAGGER : attacker.getMainWeaponDamage();

        // Backstab: a Thief (or, per gygax75-rules, a Half-Orc) attacking while the monsters are still
        // flat-footed gets +4 to hit and double damage — the classic B/X trigger, reusing the existing
        // surprise flags instead of needing a separate flanking/facing concept.
        boolean backstab = (attacker.getCharacterClass() == CharacterClass.THIEF
                || attacker.getCharacterClass() == CharacterClass.HALF_ORC) && encounter.isMonstersSurprised();
        // Two-weapon fighting: a flat +1 to attack, no extra attack or damage. PC-only — retainers have
        // no way to acquire an off-hand weapon.
        boolean dualWielding = isPlayer && ((Character) attacker).getOffHandWeapon() != null;
        int toHitModifier = attacker.meleeToHitModifier() + fatiguePenalty + (backstab ? 4 : 0) + (dualWielding ? 1 : 0);

        var outcome = AttackResolver.resolve(dice.d20(), toHitModifier, attacker.thac0(), target.getType().armorClass());
        if (outcome.hit()) {
            int damage = Math.max(1, damageRoll.roll(dice) + attacker.meleeDamageModifier() + fatiguePenalty);
            if (backstab) {
                damage *= 2;
            }
            target.takeDamage(damage);
            result.add(who + " " + (secondPerson ? "strike" : "strikes") + " the " + name + " with " + weapon
                    + " for " + damage + " damage" + (backstab ? " (backstab!)" : "")
                    + (outcome.critical() ? " (critical!)" : "")
                    + (target.isAlive() ? " (" + target.getCurrentHp() + " hp left)." : " — it falls!"));
        } else {
            result.add(who + " " + (secondPerson ? "swing" : "swings") + " at the " + name + " and "
                    + (secondPerson ? "miss" : "misses") + (outcome.fumble() ? " badly" : "") + ".");
        }
    }

    private void missileAttack(
            SaveGame save, Combatant attacker, WeaponProfile profile, DamageRoll weaponDamage, CombatEncounter encounter,
            Integer targetIndex, int fatiguePenalty, ExplorationResult result) {
        List<Monster> alive = encounter.aliveMonsters();
        if (alive.isEmpty()) {
            return;
        }
        boolean isPlayer = attacker instanceof Character;
        boolean secondPerson = isPlayer && save.getCharacters().size() == 1;
        String who = secondPerson ? "You" : attacker.getName();
        Monster target = alive.get(resolveTargetIndex(targetIndex, alive.size()));
        String name = target.getType().name().toLowerCase();

        RangeBand band = profile.rangeTable().band(encounter.getDistanceFeet());
        if (band == null) {
            result.add(who + " " + (secondPerson ? "have" : "has") + " no clear shot — the range is too great.");
            return;
        }
        var outcome = AttackResolver.resolve(dice.d20(),
                attacker.missileToHitModifier() + band.toHitModifier() + fatiguePenalty,
                attacker.thac0(), target.getType().armorClass());
        if (outcome.hit()) {
            // No ability-score bonus to missile damage in B/X.
            int damage = Math.max(1, weaponDamage.roll(dice) + fatiguePenalty);
            target.takeDamage(damage);
            result.add(who + " " + (secondPerson ? "fire" : "fires") + " at the " + name + " for " + damage
                    + " damage" + (outcome.critical() ? " (critical!)" : "")
                    + (target.isAlive() ? " (" + target.getCurrentHp() + " hp left)." : " — it falls!"));
        } else {
            result.add(who + " " + (secondPerson ? "fire" : "fires") + " at the " + name + " and "
                    + (secondPerson ? "miss" : "misses") + ".");
        }
    }

    /** One monster side-turn: a melee attack while in contact, otherwise a missile volley from whichever
        monsters carry a ranged attack — the rest simply keep closing (see {@link #closeDistance}). B/X
        places no monster-specific restriction on missile fire, so an armed monster shoots during the
        approach under the same rules the party's own missile fire already follows. */
    private void monstersAct(SaveGame save, ExplorationResult result) {
        if (save.getSession().getCombat().isMelee()) {
            monstersAttackParty(save, result);
        } else {
            monstersFireMissiles(save, result);
        }
    }

    private void monstersAttackParty(SaveGame save, ExplorationResult result) {
        CombatEncounter encounter = save.getSession().getCombat();
        int width = save.getSession().currentRoom().getCorridorWidth();
        boolean solo = save.getCharacters().size() == 1;
        for (Monster monster : encounter.aliveMonsters()) {
            List<Combatant> targetable = Formation.engagedFront(save.fullOrder(), width);
            if (targetable.isEmpty()) {
                return;
            }
            Combatant target = targetable.get(dice.d(targetable.size()) - 1);
            MonsterType type = monster.getType();
            int targetAc = target.armorClass() + polingSurpriseAcPenalty(save, encounter, width, target);
            var outcome = AttackResolver.resolve(dice.d20(), 0, type.thac0(), targetAc);
            String victim = (target instanceof Character && solo) ? "you" : target.getName();
            // Drain only has a level to take from an Advanceable combatant (PC/retainer) — a mule has no
            // levels, so it takes ordinary damage from the same hit instead of falling through untouched.
            if (outcome.hit() && type.effect() == AttackEffect.DRAIN && target instanceof Advanceable) {
                applyDrain(save, target, result);
                if (target instanceof Character && save.livingCharacters().isEmpty()) {
                    return;
                }
            } else if (outcome.hit()) {
                if (applyMonsterDamage(save, target, type.attack().roll(dice), type, "hits", victim, result)) {
                    return;
                }
            } else {
                result.add("The " + type.name().toLowerCase() + " misses " + victim + ".");
            }
        }
    }

    /** Pre-melee missile volley: every alive monster with a {@link MonsterType#ranged()} attack shoots a
        front-rank party member if the party is within its range bands, resolved with the same range-band
        to-hit math the party's missile fire uses. Monsters without a ranged attack — or out of Long range
        this round — do nothing here and keep closing. */
    private void monstersFireMissiles(SaveGame save, ExplorationResult result) {
        CombatEncounter encounter = save.getSession().getCombat();
        int width = save.getSession().currentRoom().getCorridorWidth();
        boolean solo = save.getCharacters().size() == 1;
        for (Monster monster : encounter.aliveMonsters()) {
            MonsterType type = monster.getType();
            RangedAttack ranged = type.ranged();
            if (ranged == null) {
                continue;
            }
            RangeBand band = ranged.range().band(encounter.getDistanceFeet());
            if (band == null) {
                continue; // still beyond Long range this round
            }
            List<Combatant> targetable = Formation.engagedFront(save.fullOrder(), width);
            if (targetable.isEmpty()) {
                return;
            }
            Combatant target = targetable.get(dice.d(targetable.size()) - 1);
            String victim = (target instanceof Character && solo) ? "you" : target.getName();
            var outcome = AttackResolver.resolve(dice.d20(), band.toHitModifier(), type.thac0(), target.armorClass());
            if (outcome.hit()) {
                if (applyMonsterDamage(save, target, ranged.damage().roll(dice), type, "shoots", victim, result)) {
                    return;
                }
            } else {
                result.add("The " + type.name().toLowerCase() + " shoots at " + victim + " and misses.");
            }
        }
    }

    /** Applies {@code damage} from {@code type} to {@code target}: HP loss, a "{@code hitVerb}" narration
        line, and a fallen mule's cargo spill. Shared by the melee and missile monster-attack paths.
        Returns {@code true} if this drops the party's last living PC, so the caller stops resolving. */
    private boolean applyMonsterDamage(SaveGame save, Combatant target, int damage, MonsterType type,
            String hitVerb, String victim, ExplorationResult result) {
        target.setCurrentHp(target.getCurrentHp() - damage);
        result.add("The " + type.name().toLowerCase() + " " + hitVerb + " " + victim + " for " + damage + " damage"
                + (target.isAlive() ? "." : (target instanceof Character ? "." : " — " + target.getName() + " falls!")));
        if (target instanceof Mule mule && !mule.isAlive()) {
            save.getMules().remove(mule);
            recoverMuleCargo(save, mule, result);
        }
        return target instanceof Character && save.livingCharacters().isEmpty();
    }

    /** A fallen mule's cargo spills where it drops — every living PC in turn scoops up as much as their
        own remaining carry capacity allows ({@link Encumbrance#capacityRemaining} against their real
        total {@link Character#carriedWeightCns() carried weight} — gear and gold both count now, not
        just gold), so a party already loaded down may not recover all of it (or any of it). Whatever
        nobody has room for is left behind with the corpse. Each PC is credited XP for their own
        recovered share, same 1-XP-per-gp rate as ordinary dungeon treasure; retainers hold no gold of
        their own (see {@code Retainer}) so don't share in this one, unlike a full room haul.
        Package-private: tests exercise this directly without needing to engineer a mule's death. */
    void recoverMuleCargo(SaveGame save, Mule mule, ExplorationResult result) {
        int remaining = mule.getCarriedGold();
        if (remaining == 0) {
            return;
        }
        boolean solo = save.getCharacters().size() == 1;
        int recovered = 0;
        for (Character pc : save.livingCharacters()) {
            if (remaining == 0) {
                break;
            }
            int share = Math.min(remaining, Encumbrance.capacityRemaining(pc.carriedWeightCns()));
            if (share == 0) {
                continue;
            }
            pc.setGold(pc.getGold() + share);
            remaining -= share;
            recovered += share;
            result.getLines().addAll(Leveling.awardXp(pc, share, dice, false));
        }
        if (recovered > 0) {
            result.add((solo ? "You recover" : "The party recovers") + " **" + recovered + " gp** from "
                    + mule.getName() + "'s cargo" + (remaining > 0 ? "" : ".")
                    + (remaining > 0 ? " — **" + remaining + " gp** is left behind, nobody has room for it." : ""));
        } else {
            result.add(mule.getName() + " goes down carrying " + remaining
                    + " gp — nobody has room to recover it.");
        }
    }

    /** Energy drain: a level-2+ combatant simply loses a level ({@link Leveling#drainLevel}); a
        level-1 combatant instead risks death outright — a save vs. Death that succeeds leaves them
        alive at level 1 with 2 HP and (if a spellcaster) no prepared spells, "reduced to a shell of
        themself"; failure kills them. Restoring drained levels needs a safe-haven rest and isn't
        modeled yet — drains are permanent for now. */
    private void applyDrain(SaveGame save, Combatant target, ExplorationResult result) {
        if (!(target instanceof Advanceable adv)) {
            return;
        }
        boolean secondPerson = target instanceof Character && save.getCharacters().size() == 1;
        String who = secondPerson ? "You" : adv.getName();
        if (adv.getLevel() > 1) {
            result.add(Leveling.drainLevel(adv, dice));
            return;
        }
        int saveTarget = SavingThrows.forCharacter(adv.getCharacterClass(), adv.getLevel()).deathPoison();
        boolean saved = dice.d20() >= saveTarget;
        if (saved) {
            adv.setMaxHp(2);
            adv.setCurrentHp(2);
            if (target instanceof Character character) {
                character.getMemorizedSpells().clear();
            }
            result.add(who + " " + (secondPerson ? "barely survive" : "barely survives")
                    + " the drain, reduced to a shell of " + (secondPerson ? "your" : "their")
                    + " former self (2 hp, no spells or special abilities).");
        } else {
            adv.setCurrentHp(0);
            result.add(who + " " + (secondPerson ? "fail" : "fails")
                    + " the saving throw and " + (secondPerson ? "die" : "dies")
                    + " from the drain!");
        }
    }

    /** Poling front-rankers fight with their hands off their weapon; if surprised this round, they're
        easier to hit (descending AC: a positive penalty makes the number worse). */
    private int polingSurpriseAcPenalty(SaveGame save, CombatEncounter encounter, int width, Combatant target) {
        if (!save.getSession().isPolingFrontRank() || !encounter.isPartySurprised()) {
            return 0;
        }
        return Formation.engagedFront(save.fullOrder(), width).contains(target) ? 2 : 0;
    }

    /** A badly wounded retainer (<=1/4 HP) must check loyalty (2d6); above their score, they flee. */
    private void loyaltyChecks(SaveGame save, ExplorationResult result) {
        for (Retainer retainer : save.livingRetainers()) {
            if (retainer.getCurrentHp() * 4 <= retainer.getMaxHp() && dice.roll2d6() > retainer.getLoyalty()) {
                retainer.setFled(true);
                result.add(retainer.getName() + ", bloodied and afraid, breaks and flees the fight!");
            }
        }
    }

    /** B/X morale: checked exactly once at each of two triggers — the first casualty, and again once
        the group is reduced to half or fewer of its starting number — not every round thereafter. */
    private void checkMorale(SaveGame save, CombatEncounter encounter, ExplorationResult result) {
        if (encounter.isMoraleBroken() || encounter.getInitialCount() <= 1) {
            return;
        }
        int alive = encounter.aliveMonsters().size();
        if (alive == 0 || alive == encounter.getInitialCount()) {
            return; // no losses yet
        }
        // "Half or fewer of the original numbers remain" — for 5 that's 2 (3 dead), not 3. Compare
        // alive*2 to initialCount so odd starting sizes don't round the trigger up a casualty early.
        boolean dueFirst = !encounter.isFirstCasualtyChecked() && alive < encounter.getInitialCount();
        boolean dueHalf = !encounter.isHalfLossChecked() && alive * 2 <= encounter.getInitialCount();
        // Each trigger is an independent B/X morale check; when a single casualty crosses both at once
        // (e.g. the first kill in a group of 3), each still gets its own 2d6 roll rather than sharing one.
        if (dueFirst) {
            encounter.setFirstCasualtyChecked(true);
            if (rollMoraleBreak(save, encounter, result)) {
                return;
            }
        }
        if (dueHalf) {
            encounter.setHalfLossChecked(true);
            rollMoraleBreak(save, encounter, result);
        }
    }

    /** One 2d6 morale check for {@code encounter}: breaks (and narrates) on a failure. Returns whether
        morale broke, so a caller crossing both triggers in one round can stop after the first break. */
    private boolean rollMoraleBreak(SaveGame save, CombatEncounter encounter, ExplorationResult result) {
        MonsterType type = encounter.aliveMonsters().get(0).getType();
        int modifier = situationalMoraleModifier(save, encounter);
        if (dice.roll2d6() > type.morale() + modifier) {
            encounter.setMoraleBroken(true);
            result.add("The surviving " + type.name().toLowerCase() + "s break and flee!");
            return true;
        }
        return false;
    }

    /** A rough automated stand-in for the house rule's "referee may apply -1/+1 based on the situation":
        compares each side's proportional HP loss so far — the monsters break easier if they're clearly
        worse off than the party, and hold longer if they're clearly ahead. */
    private int situationalMoraleModifier(SaveGame save, CombatEncounter encounter) {
        double monsterLoss = 1.0 - (double) encounter.aliveMonsters().size() / encounter.getInitialCount();
        double partyLoss = partyHpLossRatio(save);
        if (monsterLoss - partyLoss > 0.25) {
            return -1; // monsters clearly worse off: break easier
        }
        if (partyLoss - monsterLoss > 0.25) {
            return 1; // monsters clearly ahead: hold longer
        }
        return 0;
    }

    private double partyHpLossRatio(SaveGame save) {
        int maxTotal = 0;
        int curTotal = 0;
        for (Combatant c : save.fullOrder()) {
            maxTotal += c.getMaxHp();
            curTotal += Math.max(0, c.getCurrentHp());
        }
        return maxTotal == 0 ? 0 : 1.0 - (double) curTotal / maxTotal;
    }

    private ExplorationResult victory(SaveGame save, ExplorationResult result, boolean verbose) {
        GameSession session = save.getSession();
        CombatEncounter encounter = session.getCombat();

        int totalXp = 0;
        for (Monster m : encounter.getMonsters()) {
            if (!m.isAlive()) {
                totalXp += m.getType().xpValue();
            }
        }

        session.currentRoom().setCleared(true);
        session.setState(SessionState.EXPLORING);
        containers.returnDroppedContainers(save, encounter);
        session.setCombat(null);

        result.add("");
        result.add(encounter.isMoraleBroken()
                ? "The enemy has fled. You hold the room."
                : "**Victory!** The last of them falls.");

        if (totalXp > 0) {
            // Every living PC earns a full share, each retainer a half-share — reduces to today's exact
            // behavior at 1 PC. Deliberate: the denominator counts retainers as FULL shares, then pays
            // them only half — the other half of each retainer's cut simply isn't awarded to anyone
            // (read it as the hireling pocketing wages the party never sees), the same wage abstraction
            // ExplorationService.loot() uses for the gold itself. Not a rounding bug.
            List<Character> livingPcs = save.livingCharacters();
            List<Retainer> survivors = save.livingRetainers();
            int shares = livingPcs.size() + survivors.size();
            int perShare = totalXp / shares;
            for (Character pc : livingPcs) {
                result.getLines().addAll(Leveling.awardXp(pc, perShare, dice, verbose));
            }
            for (Retainer retainer : survivors) {
                // Retainers earn a half-share; surface only their level-ups to keep the log readable.
                for (String line : Leveling.awardXp(retainer, perShare / 2, dice)) {
                    if (line.contains("Level up")) {
                        result.add(line);
                    }
                }
            }
        }
        return result;
    }

    private ExplorationResult defeat(SaveGame save, ExplorationResult result) {
        GameSession session = save.getSession();
        session.setState(SessionState.IN_TOWN);
        session.setCombat(null);
        result.add("");
        result.add(save.getCharacters().size() == 1
                ? "**" + save.getCharacter().getName() + " has been slain in the dungeon.** Roll a new character to delve again."
                : "**Your party has been wiped out in the dungeon.** Roll a new character to delve again.");
        return result;
    }

    /** Flee: the monsters take parting blows, then the party retreats. Permanent desertion is only ever
        at risk after a genuinely bad fight (the PC bloodied, or a companion already down this delve),
        and only for retainers who'd already broken and sat out from being bloodied themselves
        ({@link #loyaltyChecks}) — someone who held steady through the fight just retreats with everyone. */
    public ExplorationResult flee(SaveGame save) {
        GameSession session = save.getSession();
        if (session.getState() != SessionState.IN_COMBAT) {
            return ExplorationResult.failure("You are not in combat.");
        }
        ExplorationResult result = new ExplorationResult();
        result.add("You turn to flee — the enemy lashes out as you go!");
        monstersAttackParty(save, result);
        if (save.livingCharacters().isEmpty()) {
            return defeat(save, result);
        }

        List<Exit> escapes = new ArrayList<>();
        for (Exit exit : session.currentRoom().getExits().values()) {
            if (exit.isPassable()) {
                escapes.add(exit);
            }
        }
        if (escapes.isEmpty()) {
            result.add("There is no open way out — you must keep fighting!");
            return result;
        }

        List<Monster> alivePursuers = session.getCombat().aliveMonsters();
        MonsterType pursuer = alivePursuers.isEmpty() ? null : alivePursuers.get(0).getType();
        if (pursuer != null && !evades(save, pursuer)) {
            result.add("They're just as fast as you (or faster) and stay right on your heels — "
                    + "you're dragged back into the fight!");
            return result;
        }

        Exit escape = escapes.get(dice.d(escapes.size()) - 1);
        session.setCurrentRoomId(escape.getDestinationRoomId());
        session.currentRoom().setVisited(true);
        session.setState(SessionState.EXPLORING);
        containers.discardDroppedContainers(save, session.getCombat(), result);
        session.setCombat(null);

        boolean catastrophic = save.livingCharacters().stream().anyMatch(c -> c.getCurrentHp() * 4 <= c.getMaxHp())
                || save.getRetainers().stream().anyMatch(r -> r.getCurrentHp() <= 0);
        List<Retainer> deserters = new ArrayList<>();
        if (catastrophic) {
            for (Retainer retainer : save.getRetainers()) {
                if (retainer.isFled() && dice.roll2d6() > retainer.getLoyalty()) {
                    deserters.add(retainer);
                }
            }
        }
        save.getRetainers().removeAll(deserters);
        for (Retainer d : deserters) {
            result.add(d.getName() + " loses heart in the rout and abandons you.");
        }
        result.add("You escape to the " + escape.getDirection().lower() + ". (`look` to get your bearings.)");
        return result;
    }

    /** Evasion: the fleeing side automatically gets away if faster than the pursuer; otherwise a
        pursuit occurs. Running exhaustion and the rulebook's separate obstacle/dropped-loot/line-of-
        sight distraction checks aren't modeled individually (no round-tracked chase or droppable-loot
        concept exists) — instead, failing to be faster gets one flat 2-in-6 chance to still shake the
        pursuer, standing in for all three at once. */
    private boolean evades(SaveGame save, MonsterType pursuer) {
        int partyRate = groupEncounterRate(save);
        int monsterRate = pursuer.moveRate() / 3;
        if (partyRate > monsterRate) {
            return true;
        }
        return dice.d(6) <= 2;
    }

    /** The party's group movement rate is the slowest living member's — the classic B/X "the party
        moves at the pace of its slowest member" rule, across every living PC and retainer, plus the
        party's mule if it has one (an overloaded mule can drag the party down the same way an overloaded
        PC can). */
    private int groupEncounterRate(SaveGame save) {
        int slowest = Integer.MAX_VALUE;
        for (Character c : save.livingCharacters()) {
            slowest = Math.min(slowest, Encumbrance.encounterRate(c.carriedWeightCns()));
        }
        for (Retainer r : save.livingRetainers()) {
            slowest = Math.min(slowest, Encumbrance.encounterRate(r.carriedWeightCns()));
        }
        for (Mule mule : save.livingMules()) {
            slowest = Math.min(slowest, MuleRules.encounterRate(mule.getCarriedGold()));
        }
        return slowest == Integer.MAX_VALUE ? 0 : slowest;
    }

    private String status(SaveGame save) {
        CombatEncounter encounter = save.getSession().getCombat();
        int width = save.getSession().currentRoom().getCorridorWidth();
        List<List<Combatant>> ranks = Formation.ranks(save.fullOrder(), width);

        StringBuilder sb = new StringBuilder();
        boolean solo = save.getCharacters().size() == 1;
        List<String> rankParts = new ArrayList<>();
        for (int i = 0; i < ranks.size(); i++) {
            List<String> members = new ArrayList<>();
            for (Combatant c : ranks.get(i)) {
                if (!c.isAlive()) {
                    continue;
                }
                String label = (c instanceof Character && solo) ? "You" : c.getName();
                members.add(label + " " + Math.max(0, c.getCurrentHp()) + "/" + c.getMaxHp());
            }
            if (!members.isEmpty()) {
                rankParts.add((i == 0 ? "Front" : "Rank " + (i + 1)) + ": " + String.join(", ", members));
            }
        }
        sb.append(String.join(" | ", rankParts));
        if (!encounter.isMelee()) {
            sb.append(". Range: ").append(encounter.getDistanceFeet()).append(" ft (closing)");
        }
        sb.append(". Enemies: ");
        List<Monster> alive = encounter.aliveMonsters();
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < alive.size(); i++) {
            parts.add("#" + (i + 1) + " " + alive.get(i).getType().name().toLowerCase()
                    + " (" + alive.get(i).getCurrentHp() + " hp)");
        }
        sb.append(String.join(", ", parts)).append(".");
        return sb.toString();
    }
}
