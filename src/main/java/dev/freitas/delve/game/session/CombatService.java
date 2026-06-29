package dev.freitas.delve.game.session;

import dev.freitas.delve.game.Bestiary;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.AttackResolver;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Leveling;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.CombatEncounter;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.GameSession;
import dev.freitas.delve.game.model.Monster;
import dev.freitas.delve.game.model.MonsterType;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The B/X combat loop. Each call to {@link #attackRound} resolves one full round: side initiative
 * (1d6 per side), the player's attack and the monsters' attacks in initiative order, then a morale
 * check. Victory awards XP (and may level the character up); defeat ends the delve.
 */
@Service
public class CombatService {

    private final Dice dice;

    public CombatService(Dice dice) {
        this.dice = dice;
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
                ExplorationResult intro = startCombat(save);
                return continueRound(save, targetIndex, intro);
            }
            return ExplorationResult.failure("There is nothing here to attack.");
        }
        return continueRound(save, targetIndex, new ExplorationResult());
    }

    private ExplorationResult continueRound(SaveGame save, Integer targetIndex, ExplorationResult result) {
        GameSession session = save.getSession();
        Character character = save.getCharacter();
        CombatEncounter encounter = session.getCombat();
        encounter.setRound(encounter.getRound() + 1);
        result.add("__Round " + encounter.getRound() + "__");

        boolean playerFirst = dice.d(6) >= dice.d(6); // side initiative, ties to the player
        if (playerFirst) {
            playerAttack(character, encounter, targetIndex, result);
            if (!encounter.isOver()) {
                monstersAttack(save, result);
            }
        } else {
            monstersAttack(save, result);
            if (character.isAlive() && !encounter.aliveMonsters().isEmpty()) {
                playerAttack(character, encounter, targetIndex, result);
            }
        }

        if (!character.isAlive()) {
            return defeat(save, result);
        }
        checkMorale(encounter, result);
        if (encounter.isOver()) {
            return victory(save, result);
        }
        result.add("");
        result.add(status(character, encounter));
        return result;
    }

    private void playerAttack(
            Character character, CombatEncounter encounter, Integer targetIndex, ExplorationResult result) {
        List<Monster> alive = encounter.aliveMonsters();
        if (alive.isEmpty()) {
            return;
        }
        int index = (targetIndex != null && targetIndex >= 1 && targetIndex <= alive.size()) ? targetIndex - 1 : 0;
        Monster target = alive.get(index);

        var outcome = AttackResolver.resolve(
                dice.d20(), character.meleeToHitModifier(), character.thac0(), target.getType().armorClass());
        String name = target.getType().name().toLowerCase();
        if (outcome.hit()) {
            int damage = Math.max(1, character.getMainWeaponDamage().roll(dice) + character.meleeDamageModifier());
            target.takeDamage(damage);
            result.add("You strike the " + name + " with your " + character.getMainWeapon().toLowerCase()
                    + " for " + damage + " damage" + (outcome.critical() ? " (critical!)" : "")
                    + (target.isAlive() ? " (" + target.getCurrentHp() + " hp left)." : " — it falls!"));
        } else {
            result.add("You swing at the " + name + " and miss" + (outcome.fumble() ? " badly" : "") + ".");
        }
    }

    private void monstersAttack(SaveGame save, ExplorationResult result) {
        Character character = save.getCharacter();
        CombatEncounter encounter = save.getSession().getCombat();
        for (Monster monster : encounter.aliveMonsters()) {
            MonsterType type = monster.getType();
            var outcome = AttackResolver.resolve(dice.d20(), 0, type.thac0(), character.armorClass());
            if (outcome.hit()) {
                int damage = type.attack().roll(dice);
                character.setCurrentHp(character.getCurrentHp() - damage);
                result.add("The " + type.name().toLowerCase() + " hits you for " + damage + " damage"
                        + (character.isAlive() ? " (" + Math.max(0, character.getCurrentHp()) + " hp left)." : "."));
                if (!character.isAlive()) {
                    return;
                }
            } else {
                result.add("The " + type.name().toLowerCase() + " misses you.");
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

        int xp = 0;
        for (Monster m : encounter.getMonsters()) {
            if (!m.isAlive()) {
                xp += m.getType().xpValue();
            }
        }

        Room room = session.currentRoom();
        room.setCleared(true);
        session.setState(SessionState.EXPLORING);
        session.setCombat(null);

        result.add("");
        result.add(encounter.isMoraleBroken()
                ? "The enemy has fled. You hold the room."
                : "**Victory!** The last of them falls.");
        if (xp > 0) {
            result.getLines().addAll(Leveling.awardXp(character, xp, dice));
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

    /** Flee: the monsters take parting blows, then the party retreats to a random adjacent room. */
    public ExplorationResult flee(SaveGame save) {
        GameSession session = save.getSession();
        Character character = save.getCharacter();
        if (session.getState() != SessionState.IN_COMBAT) {
            return ExplorationResult.failure("You are not in combat.");
        }
        ExplorationResult result = new ExplorationResult();
        result.add("You turn to flee — the enemy lashes out as you go!");
        monstersAttack(save, result);
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
        result.add("You escape to the " + escape.getDirection().lower() + ". (`look` to get your bearings.)");
        return result;
    }

    private String status(Character character, CombatEncounter encounter) {
        StringBuilder sb = new StringBuilder();
        sb.append("You: ").append(Math.max(0, character.getCurrentHp())).append("/").append(character.getMaxHp())
                .append(" hp. Enemies: ");
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
