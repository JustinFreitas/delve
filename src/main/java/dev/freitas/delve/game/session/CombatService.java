package dev.freitas.delve.game.session;

import dev.freitas.delve.game.Bestiary;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.AttackResolver;
import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Formation;
import dev.freitas.delve.game.engine.Leveling;
import dev.freitas.delve.game.engine.RangeBand;
import dev.freitas.delve.game.engine.Spell;
import dev.freitas.delve.game.engine.WeaponCatalog;
import dev.freitas.delve.game.engine.WeaponCatalog.WeaponProfile;
import dev.freitas.delve.game.engine.WeaponClass;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.CombatEncounter;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.GameSession;
import dev.freitas.delve.game.model.Monster;
import dev.freitas.delve.game.model.MonsterType;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import java.util.ArrayList;
import java.util.List;
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

    public CombatService(Dice dice, SpellService spells) {
        this.dice = dice;
        this.spells = spells;
    }

    /** B/X reaction: undead are mindlessly hostile; otherwise 2d6 + CHA modifier, hostile on 8 or less. */
    public boolean isHostileReaction(Character character, MonsterType type) {
        if (isUndead(type)) {
            return true;
        }
        int reaction = dice.roll2d6() + character.getAbilities().modifier(Ability.CHA);
        return reaction <= 8;
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

    /** Resolves one round of combat; starts the fight first if the player initiated it. */
    public ExplorationResult attackRound(SaveGame save, Integer targetIndex) {
        return attackRound(save, targetIndex, false);
    }

    /** As {@link #attackRound(SaveGame, Integer)}, but with {@code verbose} showing the raw XP/prime-
        requisite breakdown behind a victory's award (used by {@code /autodelve}'s verbose log detail). */
    public ExplorationResult attackRound(SaveGame save, Integer targetIndex, boolean verbose) {
        GameSession session = save.getSession();
        if (session.getState() != SessionState.IN_COMBAT) {
            if (session.currentRoom().hasLiveMonster()) {
                return continueRound(save, targetIndex, startCombat(save), verbose);
            }
            return ExplorationResult.failure("There is nothing here to attack.");
        }
        return continueRound(save, targetIndex, new ExplorationResult(), verbose);
    }

    /** Casts a combat spell as the player's action for one round (starting the fight if needed). */
    public ExplorationResult castRound(SaveGame save, Spell spell, Integer targetIndex) {
        Character character = save.getCharacter();
        if (!spells.isMemorized(character, spell)) {
            return ExplorationResult.failure("You don't have **" + spell.displayName() + "** prepared.");
        }
        GameSession session = save.getSession();
        if (session.getState() != SessionState.IN_COMBAT && !session.currentRoom().hasLiveMonster()) {
            return ExplorationResult.failure("There is nothing here to target.");
        }
        ExplorationResult result =
                session.getState() != SessionState.IN_COMBAT ? startCombat(save) : new ExplorationResult();
        return resolveRound(save, result, () -> {
            applyPlayerSpell(save, spell, targetIndex, result);
            retainersAttack(save, result);
        }, false);
    }

    private ExplorationResult continueRound(SaveGame save, Integer targetIndex, ExplorationResult result, boolean verbose) {
        return resolveRound(save, result, () -> partyAttacks(save, targetIndex, result), verbose);
    }

    /** Runs one round given the player's chosen action, with side initiative and the aftermath checks. */
    private ExplorationResult resolveRound(SaveGame save, ExplorationResult result, Runnable playerAction, boolean verbose) {
        Character character = save.getCharacter();
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
            if (!encounter.isOver() && monstersActEligible && encounter.isMelee()) {
                monstersAttackParty(save, result);
            }
        } else {
            if (monstersActEligible && encounter.isMelee()) {
                monstersAttackParty(save, result);
            }
            if (character.isAlive() && !encounter.aliveMonsters().isEmpty() && partyActs) {
                playerAction.run();
            }
        }
        // Surprise only ever applies to round 1.
        encounter.setPartySurprised(false);
        encounter.setMonstersSurprised(false);

        if (!character.isAlive()) {
            return defeat(save, result);
        }
        loyaltyChecks(save, result);
        checkMorale(encounter, result);
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

    private void partyAttacks(SaveGame save, Integer targetIndex, ExplorationResult result) {
        act(save, save.getCharacter(), targetIndex, result);
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
        String name = isPlayer ? "You" : attacker.getName();
        int fatiguePenalty = save.getSession().isFatigued() ? -1 : 0;

        if (encounter.isMelee()) {
            boolean canMelee = engaged || (rank == 2 && profile.weaponClass() == WeaponClass.REACH);
            if (canMelee) {
                meleeAttack(attacker, profile, encounter, targetIndex, fatiguePenalty, result);
            } else {
                result.add(name + " can't reach the fight from the " + rankLabel(rank) + " rank.");
            }
        } else if (rank >= 2 && profile.weaponClass() == WeaponClass.MISSILE) {
            missileAttack(attacker, profile, attacker.getMainWeaponDamage(), encounter, targetIndex,
                    fatiguePenalty, result);
        } else if (rank >= 2 && secondaryMissileProfile(attacker) != null) {
            // A melee-equipped retainer still carries a missile backup (e.g. a sling) — use it
            // pre-melee instead of standing idle.
            Retainer r = (Retainer) attacker;
            missileAttack(attacker, secondaryMissileProfile(attacker), r.getSecondaryWeaponDamage(),
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

    /** Applies the player's spell: a damaging bolt, a sleep effect, or in-combat healing. */
    private void applyPlayerSpell(SaveGame save, Spell spell, Integer targetIndex, ExplorationResult result) {
        Character character = save.getCharacter();
        CombatEncounter encounter = save.getSession().getCombat();
        spells.consume(character, spell);
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
                result.add("You loose **" + spell.displayName() + "** at the " + target.getType().name().toLowerCase()
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
                result.add("You cast **Sleep** — " + slept + " enemy" + (slept == 1 ? "" : " enemies")
                        + " collapse" + (slept == 1 ? "s" : "") + " into helpless slumber.");
            }
            case HEAL -> {
                int healed = SpellService.CURE_LIGHT_WOUNDS.roll(dice);
                int before = character.getCurrentHp();
                character.setCurrentHp(Math.min(character.getMaxHp(), character.getCurrentHp() + healed));
                result.add("You cast **" + spell.displayName() + "**, recovering "
                        + (character.getCurrentHp() - before) + " hp.");
            }
            default -> result.add("You cast **" + spell.displayName() + "**.");
        }
    }

    /** Fallback weapon for a missile-armed combatant forced into melee (front of a broken column). */
    private static final DamageRoll FALLBACK_DAGGER = new DamageRoll(1, 4);

    private int resolveTargetIndex(Integer targetIndex, int aliveCount) {
        return (targetIndex != null && targetIndex >= 1 && targetIndex <= aliveCount) ? targetIndex - 1 : 0;
    }

    private void meleeAttack(
            Combatant attacker, WeaponProfile profile, CombatEncounter encounter, Integer targetIndex,
            int fatiguePenalty, ExplorationResult result) {
        List<Monster> alive = encounter.aliveMonsters();
        if (alive.isEmpty()) {
            return;
        }
        Monster target = alive.get(resolveTargetIndex(targetIndex, alive.size()));
        boolean isPlayer = attacker instanceof Character;
        String who = isPlayer ? "You" : attacker.getName();
        String name = target.getType().name().toLowerCase();

        boolean fallbackDagger = profile.weaponClass() == WeaponClass.MISSILE;
        String weapon = fallbackDagger ? "a dagger"
                : (isPlayer ? "your " + attacker.getMainWeapon().toLowerCase() : attacker.getMainWeapon().toLowerCase());
        DamageRoll damageRoll = fallbackDagger ? FALLBACK_DAGGER : attacker.getMainWeaponDamage();

        var outcome = AttackResolver.resolve(dice.d20(), attacker.meleeToHitModifier() + fatiguePenalty,
                attacker.thac0(), target.getType().armorClass());
        if (outcome.hit()) {
            int damage = Math.max(1, damageRoll.roll(dice) + attacker.meleeDamageModifier() + fatiguePenalty);
            target.takeDamage(damage);
            result.add(who + " " + (isPlayer ? "strike" : "strikes") + " the " + name + " with " + weapon
                    + " for " + damage + " damage" + (outcome.critical() ? " (critical!)" : "")
                    + (target.isAlive() ? " (" + target.getCurrentHp() + " hp left)." : " — it falls!"));
        } else {
            result.add(who + " " + (isPlayer ? "swing" : "swings") + " at the " + name + " and "
                    + (isPlayer ? "miss" : "misses") + (outcome.fumble() ? " badly" : "") + ".");
        }
    }

    private void missileAttack(
            Combatant attacker, WeaponProfile profile, DamageRoll weaponDamage, CombatEncounter encounter,
            Integer targetIndex, int fatiguePenalty, ExplorationResult result) {
        List<Monster> alive = encounter.aliveMonsters();
        if (alive.isEmpty()) {
            return;
        }
        boolean isPlayer = attacker instanceof Character;
        String who = isPlayer ? "You" : attacker.getName();
        Monster target = alive.get(resolveTargetIndex(targetIndex, alive.size()));
        String name = target.getType().name().toLowerCase();

        RangeBand band = profile.rangeTable().band(encounter.getDistanceFeet());
        if (band == null) {
            result.add(who + " " + (isPlayer ? "have" : "has") + " no clear shot — the range is too great.");
            return;
        }
        var outcome = AttackResolver.resolve(dice.d20(),
                attacker.missileToHitModifier() + band.toHitModifier() + fatiguePenalty,
                attacker.thac0(), target.getType().armorClass());
        if (outcome.hit()) {
            // No ability-score bonus to missile damage in B/X.
            int damage = Math.max(1, weaponDamage.roll(dice) + fatiguePenalty);
            target.takeDamage(damage);
            result.add(who + " " + (isPlayer ? "fire" : "fires") + " at the " + name + " for " + damage
                    + " damage" + (outcome.critical() ? " (critical!)" : "")
                    + (target.isAlive() ? " (" + target.getCurrentHp() + " hp left)." : " — it falls!"));
        } else {
            result.add(who + " " + (isPlayer ? "fire" : "fires") + " at the " + name + " and "
                    + (isPlayer ? "miss" : "misses") + ".");
        }
    }

    private void monstersAttackParty(SaveGame save, ExplorationResult result) {
        Character character = save.getCharacter();
        CombatEncounter encounter = save.getSession().getCombat();
        int width = save.getSession().currentRoom().getCorridorWidth();
        for (Monster monster : encounter.aliveMonsters()) {
            List<Combatant> targetable = Formation.engagedFront(save.fullOrder(), width);
            if (targetable.isEmpty()) {
                return;
            }
            Combatant target = targetable.get(dice.d(targetable.size()) - 1);
            MonsterType type = monster.getType();
            int targetAc = target.armorClass() + polingSurpriseAcPenalty(save, encounter, width, target);
            var outcome = AttackResolver.resolve(dice.d20(), 0, type.thac0(), targetAc);
            String victim = (target instanceof Character) ? "you" : target.getName();
            if (outcome.hit()) {
                int damage = type.attack().roll(dice);
                target.setCurrentHp(target.getCurrentHp() - damage);
                result.add("The " + type.name().toLowerCase() + " hits " + victim + " for " + damage + " damage"
                        + (target.isAlive() ? "." : (target instanceof Character ? "." : " — " + target.getName() + " falls!")));
                if (target instanceof Character && !character.isAlive()) {
                    return;
                }
            } else {
                result.add("The " + type.name().toLowerCase() + " misses " + victim + ".");
            }
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
    private void checkMorale(CombatEncounter encounter, ExplorationResult result) {
        if (encounter.isMoraleBroken() || encounter.getInitialCount() <= 1) {
            return;
        }
        int alive = encounter.aliveMonsters().size();
        if (alive == 0 || alive == encounter.getInitialCount()) {
            return; // no losses yet
        }
        boolean dueFirst = !encounter.isFirstCasualtyChecked() && alive < encounter.getInitialCount();
        boolean dueHalf = !encounter.isHalfLossChecked() && alive <= (encounter.getInitialCount() + 1) / 2;
        if (dueFirst) {
            encounter.setFirstCasualtyChecked(true);
        }
        if (dueHalf) {
            encounter.setHalfLossChecked(true);
        }
        if (!dueFirst && !dueHalf) {
            return;
        }
        MonsterType type = encounter.aliveMonsters().get(0).getType();
        if (dice.roll2d6() > type.morale()) {
            encounter.setMoraleBroken(true);
            result.add("The surviving " + type.name().toLowerCase() + "s break and flee!");
        }
    }

    private ExplorationResult victory(SaveGame save, ExplorationResult result, boolean verbose) {
        GameSession session = save.getSession();
        Character character = save.getCharacter();
        CombatEncounter encounter = session.getCombat();

        int totalXp = 0;
        for (Monster m : encounter.getMonsters()) {
            if (!m.isAlive()) {
                totalXp += m.getType().xpValue();
            }
        }

        session.currentRoom().setCleared(true);
        session.setState(SessionState.EXPLORING);
        session.setCombat(null);

        result.add("");
        result.add(encounter.isMoraleBroken()
                ? "The enemy has fled. You hold the room."
                : "**Victory!** The last of them falls.");

        if (totalXp > 0) {
            List<Retainer> survivors = save.livingRetainers();
            int shares = 1 + survivors.size();
            int perShare = totalXp / shares;
            result.getLines().addAll(Leveling.awardXp(character, perShare, dice, verbose));
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
        result.add("**" + save.getCharacter().getName() + " has been slain in the dungeon.** "
                + "Roll a new character to delve again.");
        return result;
    }

    /** Flee: the monsters take parting blows, then the party retreats. Permanent desertion is only ever
        at risk after a genuinely bad fight (the PC bloodied, or a companion already down this delve),
        and only for retainers who'd already broken and sat out from being bloodied themselves
        ({@link #loyaltyChecks}) — someone who held steady through the fight just retreats with everyone. */
    public ExplorationResult flee(SaveGame save) {
        GameSession session = save.getSession();
        Character character = save.getCharacter();
        if (session.getState() != SessionState.IN_COMBAT) {
            return ExplorationResult.failure("You are not in combat.");
        }
        ExplorationResult result = new ExplorationResult();
        result.add("You turn to flee — the enemy lashes out as you go!");
        monstersAttackParty(save, result);
        if (!character.isAlive()) {
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
        Exit escape = escapes.get(dice.d(escapes.size()) - 1);
        session.setCurrentRoomId(escape.getDestinationRoomId());
        session.currentRoom().setVisited(true);
        session.setState(SessionState.EXPLORING);
        session.setCombat(null);

        boolean catastrophic = character.getCurrentHp() * 4 <= character.getMaxHp()
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

    private String status(SaveGame save) {
        CombatEncounter encounter = save.getSession().getCombat();
        int width = save.getSession().currentRoom().getCorridorWidth();
        List<List<Combatant>> ranks = Formation.ranks(save.fullOrder(), width);

        StringBuilder sb = new StringBuilder();
        List<String> rankParts = new ArrayList<>();
        for (int i = 0; i < ranks.size(); i++) {
            List<String> members = new ArrayList<>();
            for (Combatant c : ranks.get(i)) {
                if (!c.isAlive()) {
                    continue;
                }
                String label = (c instanceof Character) ? "You" : c.getName();
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

    private boolean isUndead(MonsterType type) {
        String n = type.name().toLowerCase();
        return n.equals("skeleton") || n.equals("zombie");
    }
}
