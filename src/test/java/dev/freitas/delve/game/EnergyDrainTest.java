package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.DungeonLevel;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.MonsterType;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.SpellService;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** A Wight's hit drains a level instead of dealing normal damage (see {@code AttackEffect.DRAIN}). */
class EnergyDrainTest {

    @Test
    void aHigherLevelVictimLosesALevelAndPreparedSpells() {
        // Statistical: run several fresh fights until a drain lands (a Wight always drains on a hit,
        // and its THAC0 against a lightly-armored victim lands fairly often within a handful of rounds).
        for (int trial = 0; trial < 30; trial++) {
            Dice dice = new Dice(new Random(100 + trial));
            CombatService combat = new CombatService(dice, new SpellService(dice));
            SaveGame save = combatSave(dice, Bestiary.WIGHT, 1, 200);
            Character hero = save.getCharacter();
            hero.setLevel(3);
            hero.getMemorizedSpells().add("Magic Missile");
            int levelBefore = hero.getLevel();

            combat.startCombat(save);
            for (int round = 0; round < 20 && save.getSession().getState() == SessionState.IN_COMBAT; round++) {
                combat.attackRound(save, null);
                if (hero.getLevel() < levelBefore) {
                    assertThat(hero.getLevel()).isEqualTo(levelBefore - 1);
                    assertThat(hero.getMemorizedSpells()).isEmpty();
                    return;
                }
            }
        }
        throw new AssertionError("Drain never landed across 30 trials");
    }

    @Test
    void aLevelOneVictimWhoFailsTheDeathSaveDies() {
        for (int trial = 0; trial < 60; trial++) {
            Dice dice = new Dice(new Random(200 + trial));
            CombatService combat = new CombatService(dice, new SpellService(dice));
            SaveGame save = combatSave(dice, Bestiary.WIGHT, 1, 200);
            Character hero = save.getCharacter();

            combat.startCombat(save);
            for (int round = 0; round < 20 && save.getSession().getState() == SessionState.IN_COMBAT; round++) {
                combat.attackRound(save, null);
            }
            if (!hero.isAlive()) {
                assertThat(save.getSession().getState()).isEqualTo(SessionState.IN_TOWN);
                return;
            }
        }
        throw new AssertionError("Death-by-drain never occurred across 60 trials");
    }

    @Test
    void aLevelOneVictimWhoSavesSurvivesAtTwoHp() {
        for (int trial = 0; trial < 60; trial++) {
            Dice dice = new Dice(new Random(300 + trial));
            CombatService combat = new CombatService(dice, new SpellService(dice));
            SaveGame save = combatSave(dice, Bestiary.WIGHT, 1, 200);
            Character hero = save.getCharacter();
            hero.getMemorizedSpells().add("Magic Missile");

            combat.startCombat(save);
            for (int round = 0; round < 20 && save.getSession().getState() == SessionState.IN_COMBAT; round++) {
                combat.attackRound(save, null);
                if (hero.isAlive() && hero.getMaxHp() == 2) {
                    assertThat(hero.getCurrentHp()).isEqualTo(2);
                    assertThat(hero.getMemorizedSpells()).isEmpty();
                    assertThat(hero.getLevel()).isEqualTo(1);
                    return;
                }
            }
        }
        throw new AssertionError("Surviving the level-0 death save never occurred across 60 trials");
    }

    private SaveGame combatSave(Dice dice, MonsterType type, int count, int heroHp) {
        Character hero = new CharacterFactory(dice)
                .create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
        hero.setMaxHp(heroHp);
        hero.setCurrentHp(heroHp);

        SaveGame save = new SaveGame();
        save.setCharacter(hero);

        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room here = new Room(0);
        here.setDescription("a fighting pit");
        here.setContent(dev.freitas.delve.game.model.ContentType.MONSTER);
        here.setMonsterName(type.name());
        here.setMonsterCount(count);
        Room next = new Room(1);
        next.setDescription("an antechamber");
        here.getExits().put(Direction.EAST, new Exit(Direction.EAST, 1, DoorState.NONE, false));
        next.getExits().put(Direction.WEST, new Exit(Direction.WEST, 0, DoorState.NONE, false));
        level.addRoom(here);
        level.addRoom(next);
        level.setEntranceRoomId(0);
        dungeon.addLevel(level);

        save.getSession().setDungeon(dungeon);
        save.getSession().setCurrentLevel(0);
        save.getSession().setCurrentRoomId(0);
        save.getSession().setState(SessionState.EXPLORING);
        return save;
    }
}
