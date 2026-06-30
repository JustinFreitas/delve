package dev.freitas.delve.game.session;

import dev.freitas.delve.game.Bestiary;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.AttackResolver;
import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Leveling;
import dev.freitas.delve.game.engine.Spell;
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

    /** Rolls the monster group's hit points and enters combat in the current room. */
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
        session.setCombat(encounter);
        session.setState(SessionState.IN_COMBAT);

        return ExplorationResult.of("**Combat!** " + room.getMonsterCount() + " "
                + type.name().toLowerCase() + (room.getMonsterCount() > 1 ? "s" : "")
                + " attack! (`attack [n]` to strike, `flee` to run.)");
    }

    /** Resolves one round of combat; starts the fight first if the player initiated it. */
    public ExplorationResult attackRound(SaveGame save, Integer targetIndex) {
        GameSession session = save.getSession();
        if (session.getState() != SessionState.IN_COMBAT) {
            if (session.currentRoom().hasLiveMonster()) {
                return continueRound(save, targetIndex, startCombat(save));
            }
            return ExplorationResult.failure("There is nothing here to attack.");
        }
        return continueRound(save, targetIndex, new ExplorationResult());
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
        });
    }

    private ExplorationResult continueRound(SaveGame save, Integer targetIndex, ExplorationResult result) {
        return resolveRound(save, result, () -> partyAttacks(save, targetIndex, result));
    }

    /** Runs one round given the player's chosen action, with side initiative and the aftermath checks. */
    private ExplorationResult resolveRound(SaveGame save, ExplorationResult result, Runnable playerAction) {
        Character character = save.getCharacter();
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setRound(encounter.getRound() + 1);
        result.add("__Round " + encounter.getRound() + "__");

        boolean playerFirst = dice.d(6) >= dice.d(6); // side initiative, ties to the player
        if (playerFirst) {
            playerAction.run();
            if (!encounter.isOver()) {
                monstersAttackParty(save, result);
            }
        } else {
            monstersAttackParty(save, result);
            if (character.isAlive() && !encounter.aliveMonsters().isEmpty()) {
                playerAction.run();
            }
        }

        if (!character.isAlive()) {
            return defeat(save, result);
        }
        loyaltyChecks(save, result);
        checkMorale(encounter, result);
        if (encounter.isOver()) {
            return victory(save, result);
        }
        result.add("");
        result.add(status(save));
        return result;
    }

    private void partyAttacks(SaveGame save, Integer targetIndex, ExplorationResult result) {
        Character character = save.getCharacter();
        attack(character, "your " + character.getMainWeapon().toLowerCase(),
                save.getSession().getCombat(), targetIndex, result);
        retainersAttack(save, result);
    }

    private void retainersAttack(SaveGame save, ExplorationResult result) {
        CombatEncounter encounter = save.getSession().getCombat();
        for (Retainer retainer : save.livingRetainers()) {
            if (!encounter.aliveMonsters().isEmpty()) {
                attack(retainer, retainer.getMainWeapon().toLowerCase(), encounter, null, result);
            }
        }
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

    private void attack(
            Combatant attacker, String weapon, CombatEncounter encounter, Integer targetIndex, ExplorationResult result) {
        List<Monster> alive = encounter.aliveMonsters();
        if (alive.isEmpty()) {
            return;
        }
        int index = (targetIndex != null && targetIndex >= 1 && targetIndex <= alive.size()) ? targetIndex - 1 : 0;
        Monster target = alive.get(index);
        boolean isPlayer = attacker instanceof Character;
        String who = isPlayer ? "You" : attacker.getName();
        String name = target.getType().name().toLowerCase();

        var outcome = AttackResolver.resolve(
                dice.d20(), attacker.meleeToHitModifier(), attacker.thac0(), target.getType().armorClass());
        if (outcome.hit()) {
            int damage = Math.max(1, attacker.getMainWeaponDamage().roll(dice) + attacker.meleeDamageModifier());
            target.takeDamage(damage);
            result.add(who + " " + (isPlayer ? "strike" : "strikes") + " the " + name + " with " + weapon
                    + " for " + damage + " damage" + (outcome.critical() ? " (critical!)" : "")
                    + (target.isAlive() ? " (" + target.getCurrentHp() + " hp left)." : " — it falls!"));
        } else {
            result.add(who + " " + (isPlayer ? "swing" : "swings") + " at the " + name + " and "
                    + (isPlayer ? "miss" : "misses") + (outcome.fumble() ? " badly" : "") + ".");
        }
    }

    private void monstersAttackParty(SaveGame save, ExplorationResult result) {
        Character character = save.getCharacter();
        CombatEncounter encounter = save.getSession().getCombat();
        for (Monster monster : encounter.aliveMonsters()) {
            List<Combatant> party = livingParty(save);
            if (party.isEmpty()) {
                return;
            }
            Combatant target = party.get(dice.d(party.size()) - 1);
            MonsterType type = monster.getType();
            var outcome = AttackResolver.resolve(dice.d20(), 0, type.thac0(), target.armorClass());
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

    /** A badly wounded retainer (<=1/4 HP) must check loyalty (2d6); above their score, they flee. */
    private void loyaltyChecks(SaveGame save, ExplorationResult result) {
        for (Retainer retainer : save.livingRetainers()) {
            if (retainer.getCurrentHp() * 4 <= retainer.getMaxHp() && dice.roll2d6() > retainer.getLoyalty()) {
                retainer.setFled(true);
                result.add(retainer.getName() + ", bloodied and afraid, breaks and flees the fight!");
            }
        }
    }

    /** B/X morale: once part of the group has fallen, roll 2d6; above the morale score, they flee. */
    private void checkMorale(CombatEncounter encounter, ExplorationResult result) {
        if (encounter.isMoraleBroken() || encounter.getInitialCount() <= 1) {
            return;
        }
        int alive = encounter.aliveMonsters().size();
        if (alive == 0 || alive == encounter.getInitialCount()) {
            return; // no losses yet
        }
        MonsterType type = encounter.aliveMonsters().get(0).getType();
        if (dice.roll2d6() > type.morale()) {
            encounter.setMoraleBroken(true);
            result.add("The surviving " + type.name().toLowerCase() + "s break and flee!");
        }
    }

    private ExplorationResult victory(SaveGame save, ExplorationResult result) {
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
            result.getLines().addAll(Leveling.awardXp(character, perShare, dice));
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

    /** Flee: the monsters take parting blows, then the party retreats; shaken retainers may desert. */
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

        // Retainers who lose their nerve during the rout desert for good.
        List<Retainer> deserters = new ArrayList<>();
        for (Retainer retainer : save.livingRetainers()) {
            if (dice.roll2d6() > retainer.getLoyalty()) {
                deserters.add(retainer);
            }
        }
        save.getRetainers().removeAll(deserters);
        for (Retainer d : deserters) {
            result.add(d.getName() + " loses heart in the rout and abandons you.");
        }
        result.add("You escape to the " + escape.getDirection().lower() + ". (`look` to get your bearings.)");
        return result;
    }

    private List<Combatant> livingParty(SaveGame save) {
        List<Combatant> party = new ArrayList<>();
        if (save.getCharacter().isAlive()) {
            party.add(save.getCharacter());
        }
        party.addAll(save.livingRetainers());
        return party;
    }

    private String status(SaveGame save) {
        Character character = save.getCharacter();
        CombatEncounter encounter = save.getSession().getCombat();
        StringBuilder sb = new StringBuilder();
        sb.append("You: ").append(Math.max(0, character.getCurrentHp())).append("/").append(character.getMaxHp())
                .append(" hp");
        for (Retainer r : save.livingRetainers()) {
            sb.append(", ").append(r.getName()).append(" ").append(r.getCurrentHp()).append("/").append(r.getMaxHp());
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
