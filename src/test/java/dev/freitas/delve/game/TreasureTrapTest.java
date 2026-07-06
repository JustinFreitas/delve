package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.DungeonLevel;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.LightingService;
import dev.freitas.delve.game.session.MuleService;
import dev.freitas.delve.game.session.ExplorationService;
import dev.freitas.delve.game.session.SpellService;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TreasureTrapTest {

    @Test
    void highLevelThiefEventuallyDisarmsAndLoots() {
        Dice dice = new Dice(new Random(32));
        ExplorationService service = newService(dice);
        Character thief = new CharacterFactory(dice)
                .create("Sly", CharacterClass.THIEF, new AbilityScores(9, 16, 9, 9, 9, 9));
        thief.setLevel(10); // ThiefSkills.removeTraps(10) = 55%
        SaveGame save = trapSave(thief);
        Room room = save.getSession().currentRoom();

        for (int i = 0; i < 200 && !room.isLooted(); i++) {
            service.search(save);
        }
        assertThat(room.isTreasureTrapDisarmed()).isTrue();
        assertThat(room.isLooted()).isTrue();
    }

    @Test
    void nonThiefUsesTheFlatFallbackChanceAndCanStillEventuallyLoot() {
        Dice dice = new Dice(new Random(33));
        ExplorationService service = newService(dice);
        Character fighter = new CharacterFactory(dice)
                .create("Grum", CharacterClass.FIGHTER, new AbilityScores(13, 9, 13, 9, 9, 9));
        fighter.setMaxHp(200);
        fighter.setCurrentHp(200); // survive repeated failed-disarm damage
        SaveGame save = trapSave(fighter);
        Room room = save.getSession().currentRoom();

        for (int i = 0; i < 500 && !room.isLooted(); i++) {
            service.search(save);
        }
        assertThat(room.isLooted()).isTrue();
        assertThat(fighter.getCurrentHp()).isLessThan(200); // at least one failed attempt cost HP
    }

    @Test
    void aFailedDisarmLeavesTheTreasureInPlaceForARetry() {
        Dice dice = new Dice(new Random(34));
        ExplorationService service = newService(dice);
        Character fighter = new CharacterFactory(dice)
                .create("Grum", CharacterClass.FIGHTER, new AbilityScores(13, 9, 13, 9, 9, 9));
        fighter.setMaxHp(200);
        fighter.setCurrentHp(200);
        SaveGame save = trapSave(fighter);
        Room room = save.getSession().currentRoom();

        service.search(save); // a non-Thief at 5% is overwhelmingly likely to fail on the first try
        assertThat(room.isLooted()).isFalse();
        assertThat(room.isTreasureTrapDisarmed()).isFalse();
        assertThat(room.isHasTreasure()).isTrue();
    }

    private ExplorationService newService(Dice dice) {
        return new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
    }

    private SaveGame trapSave(Character character) {
        SaveGame save = new SaveGame();
        save.setCharacter(character);
        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room room = new Room(0);
        room.setDescription("a vault");
        room.setHasTreasure(true);
        room.setTreasureGold(100);
        room.setTreasureTrapped(true);
        room.setTreasureTrapDescription("a poisoned needle in the lock");
        room.setTreasureTrapDamage(new DamageRoll(1, 4));
        level.addRoom(room);
        level.setEntranceRoomId(0);
        dungeon.addLevel(level);
        save.getSession().setDungeon(dungeon);
        save.getSession().setCurrentLevel(0);
        save.getSession().setCurrentRoomId(0);
        save.getSession().setState(SessionState.EXPLORING);
        return save;
    }
}
