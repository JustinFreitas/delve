package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.CombatEncounter;
import dev.freitas.delve.game.model.ContentType;
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

/** A Cleric's {@code /turn} attempt against an undead encounter ({@code CombatService.turnRound}). */
class TurnUndeadTest {

    @Test
    void aHighLevelClericReliablyTurnsALowHdUndeadGroup() {
        int turned = 0;
        int trials = 30;
        for (int seed = 0; seed < trials; seed++) {
            Dice dice = new Dice(new Random(seed));
            CombatService combat = new CombatService(dice, new SpellService(dice));
            Character cleric = cleric(dice, 10);
            SaveGame save = combatSave(dice, cleric, Bestiary.SKELETON, 1);

            combat.turnRound(save);
            if (save.getSession().getCombat() == null || save.getSession().getCombat().isMoraleBroken()) {
                turned++;
            }
        }
        assertThat(turned).isGreaterThan(trials / 2); // level 10 vs. HD 1: reliably turned
    }

    @Test
    void aLowLevelClericMostlyFailsAgainstHigherHdUndead() {
        int turned = 0;
        int trials = 30;
        for (int seed = 0; seed < trials; seed++) {
            Dice dice = new Dice(new Random(seed));
            CombatService combat = new CombatService(dice, new SpellService(dice));
            Character cleric = cleric(dice, 1);
            SaveGame save = combatSave(dice, cleric, Bestiary.WIGHT, 1);

            combat.turnRound(save);
            CombatEncounter encounter = save.getSession().getCombat();
            if (encounter != null && encounter.isMoraleBroken()) {
                turned++;
            }
        }
        assertThat(turned).isLessThan(trials / 2); // level 1 vs. HD 3: mostly fails
    }

    @Test
    void turningANonUndeadEncounterFails() {
        Dice dice = new Dice(new Random(5));
        CombatService combat = new CombatService(dice, new SpellService(dice));
        Character cleric = cleric(dice, 10);
        SaveGame save = combatSave(dice, cleric, Bestiary.GOBLIN, 1);

        var result = combat.turnRound(save);
        assertThat(result.text()).contains("nothing undead");
        assertThat(save.getSession().getState()).isEqualTo(SessionState.EXPLORING); // never started a fight
    }

    @Test
    void onlyAClericCanTurnUndead() {
        Dice dice = new Dice(new Random(6));
        CombatService combat = new CombatService(dice, new SpellService(dice));
        Character fighter = new CharacterFactory(dice)
                .create("Brute", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
        SaveGame save = combatSave(dice, fighter, Bestiary.SKELETON, 1);

        var result = combat.turnRound(save);
        assertThat(result.text()).contains("Only a Cleric");
    }

    // --- helpers -------------------------------------------------------------

    private Character cleric(Dice dice, int level) {
        Character cleric = new CharacterFactory(dice)
                .create("Brother", CharacterClass.CLERIC, new AbilityScores(9, 9, 13, 9, 12, 12));
        cleric.setLevel(level);
        cleric.setMaxHp(200);
        cleric.setCurrentHp(200);
        return cleric;
    }

    private SaveGame combatSave(Dice dice, Character hero, MonsterType type, int count) {
        SaveGame save = new SaveGame();
        save.setCharacter(hero);

        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room here = new Room(0);
        here.setDescription("a crypt");
        here.setContent(ContentType.MONSTER);
        here.setMonsterName(type.name());
        here.setMonsterCount(count);
        Room next = new Room(1);
        next.setDescription("a passage");
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
