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
import dev.freitas.delve.game.session.ExplorationService;
import dev.freitas.delve.game.session.SpellService;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CorridorTrapTest {

    @Test
    void corridorTrapIsDetectedBySearchingLikeARoomTrap() {
        Dice dice = new Dice(new Random(28));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, 9);
        Exit exit = save.getSession().currentRoom().getExits().get(Direction.EAST);
        exit.setTrapped(true);
        exit.setTrapDescription("a tripwire");
        exit.setTrapDamage(new DamageRoll(1, 6));

        for (int i = 0; i < 100 && !exit.isTrapDetected(); i++) {
            service.search(save);
        }
        assertThat(exit.isTrapDetected()).isTrue();
    }

    @Test
    void corridorTrapCanSpringOnTraversalButNeverOnceDetected() {
        Dice dice = new Dice(new Random(29));
        ExplorationService service = newService(dice);

        int sprang = 0;
        for (int trial = 0; trial < 200; trial++) {
            SaveGame save = twoRoomSave(dice, 9);
            Exit exit = save.getSession().currentRoom().getExits().get(Direction.EAST);
            exit.setTrapped(true);
            exit.setTrapDescription("a tripwire");
            exit.setTrapDamage(new DamageRoll(1, 6));
            int hpBefore = save.getCharacter().getCurrentHp();

            service.move(save, Direction.EAST);
            if (save.getCharacter().getCurrentHp() < hpBefore) {
                sprang++;
            }
        }
        assertThat(sprang).isGreaterThan(0);
        assertThat(sprang).isLessThan(200);

        SaveGame safe = twoRoomSave(dice, 9);
        Exit safeExit = safe.getSession().currentRoom().getExits().get(Direction.EAST);
        safeExit.setTrapped(true);
        safeExit.setTrapDetected(true);
        safeExit.setTrapDamage(new DamageRoll(1, 6));
        int hpBefore = safe.getCharacter().getCurrentHp();
        for (int i = 0; i < 20; i++) {
            safe.getSession().setCurrentRoomId(0);
            service.move(safe, Direction.EAST);
        }
        assertThat(safe.getCharacter().getCurrentHp()).isEqualTo(hpBefore);
    }

    @Test
    void anUntrappedExitIsCompletelyUnaffected() {
        Dice dice = new Dice(new Random(30));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, 9);
        int hpBefore = save.getCharacter().getCurrentHp();

        for (int i = 0; i < 10; i++) {
            save.getSession().setCurrentRoomId(0);
            service.move(save, Direction.EAST);
        }
        assertThat(save.getCharacter().getCurrentHp()).isEqualTo(hpBefore);
    }

    @Test
    void sprungCorridorTrapMirrorsOntoTheReturnExit() {
        Dice dice = new Dice(new Random(31));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, 9);
        Exit forward = save.getSession().currentRoom().getExits().get(Direction.EAST);
        forward.setTrapped(true);
        forward.setTrapDescription("a tripwire");
        forward.setTrapDamage(new DamageRoll(1, 6));
        Exit backward = save.getSession().currentLevel().room(1).getExits().get(Direction.WEST);

        for (int i = 0; i < 100 && !forward.isTrapSprung(); i++) {
            save.getSession().setCurrentRoomId(0);
            service.move(save, Direction.EAST);
        }
        assertThat(forward.isTrapSprung()).isTrue();
        assertThat(backward.isTrapSprung()).isTrue();
    }

    private ExplorationService newService(Dice dice) {
        return new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService());
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
