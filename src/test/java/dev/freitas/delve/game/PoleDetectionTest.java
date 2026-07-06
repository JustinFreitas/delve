package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.DungeonLevel;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.GameSession;
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

class PoleDetectionTest {

    @Test
    void polingEventuallyDetectsARoomTrapWithoutSearching() {
        Dice dice = new Dice(new Random(35));
        ExplorationService service = newService(dice);

        // Detection and spring are independent per-visit rolls (poling lowers overall risk but doesn't
        // guarantee detection wins the race on any single visit) — so loop until either resolves, and
        // just confirm the passive-detection path itself does fire across enough attempts.
        int detectedWithoutSpringing = 0;
        for (int trial = 0; trial < 300 && detectedWithoutSpringing == 0; trial++) {
            SaveGame attempt = twoRoomSave(dice, 9);
            attempt.getSession().setPolingFrontRank(true);
            Room room = attempt.getSession().currentLevel().room(1);
            room.setTrapped(true);
            room.setTrapDescription("a concealed pit");
            room.setTrapDamage(new DamageRoll(1, 6));
            service.move(attempt, Direction.EAST);
            if (room.isTrapDetected() && !room.isTrapSprung()) {
                detectedWithoutSpringing++;
            }
        }
        assertThat(detectedWithoutSpringing).isGreaterThan(0);
    }

    @Test
    void withoutPolingTheSameTrapCanStillSpringUndetected() {
        Dice dice = new Dice(new Random(36));
        ExplorationService service = newService(dice);

        int sprangUndetected = 0;
        for (int trial = 0; trial < 200; trial++) {
            SaveGame save = twoRoomSave(dice, 9);
            Room target = save.getSession().currentLevel().room(1);
            target.setTrapped(true);
            target.setTrapDescription("a concealed pit");
            target.setTrapDamage(new DamageRoll(1, 6));
            int hpBefore = save.getCharacter().getCurrentHp();
            service.move(save, Direction.EAST);
            if (save.getCharacter().getCurrentHp() < hpBefore) {
                sprangUndetected++;
            }
        }
        assertThat(sprangUndetected).isGreaterThan(0);
    }

    private ExplorationService newService(Dice dice) {
        return new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
    }

    private SaveGame twoRoomSave(Dice dice, int torches) {
        Character c = new CharacterFactory(dice)
                .create("Tester", CharacterClass.FIGHTER, new AbilityScores(13, 9, 9, 12, 14, 9));
        c.setTorches(torches);
        SaveGame save = new SaveGame();
        save.setCharacter(c);
        GameSession session = save.getSession();

        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room a = new Room(0);
        a.setDescription("a test chamber");
        Room b = new Room(1);
        b.setDescription("another test chamber");
        a.getExits().put(Direction.EAST, new Exit(Direction.EAST, 1, DoorState.NONE, false));
        b.getExits().put(Direction.WEST, new Exit(Direction.WEST, 0, DoorState.NONE, false));
        level.addRoom(a);
        level.addRoom(b);
        level.setEntranceRoomId(0);
        dungeon.addLevel(level);

        session.setDungeon(dungeon);
        session.setCurrentLevel(0);
        session.setCurrentRoomId(0);
        session.setState(SessionState.EXPLORING);
        session.setLightTurnsRemaining(6);
        session.setActiveLight(dev.freitas.delve.game.engine.LightSource.TORCH);
        session.setLightBearer(SaveGame.PLAYER_SLOT);
        return save;
    }
}
