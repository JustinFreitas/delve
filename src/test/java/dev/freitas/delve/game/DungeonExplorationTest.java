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
import dev.freitas.delve.game.session.SpellService;
import dev.freitas.delve.game.session.ExplorationService;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DungeonExplorationTest {

    // --- Generator ---------------------------------------------------------

    @Test
    void everyRoomIsReachableFromTheEntrance() {
        for (long seed = 1; seed <= 25; seed++) {
            DungeonGenerator generator = new DungeonGenerator(new Dice(new Random(seed)));
            Dungeon dungeon = generator.generate(3, 10);
            for (DungeonLevel level : dungeon.getLevels()) {
                assertThat(reachable(level)).as("seed %d depth %d", seed, level.getDepth())
                        .hasSize(level.getRooms().size());
            }
        }
    }

    @Test
    void stairsLinkAdjacentLevels() {
        DungeonGenerator generator = new DungeonGenerator(new Dice(new Random(99)));
        Dungeon dungeon = generator.generate(3, 10);
        assertThat(dungeon.levelCount()).isEqualTo(3);

        DungeonLevel level0 = dungeon.level(0);
        Room down = level0.getRooms().values().stream()
                .filter(Room::isStairsDown)
                .findFirst()
                .orElseThrow();
        assertThat(down.getStairsDestinationLevel()).isEqualTo(1);

        Room up = dungeon.level(1).room(down.getStairsDestinationRoomId());
        assertThat(up.isStairsUp()).isTrue();
        assertThat(up.getStairsDestinationRoomId()).isEqualTo(down.getId());
    }

    /** BFS over known exits (door state is openable, so it doesn't affect reachability). */
    private Set<Integer> reachable(DungeonLevel level) {
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(level.getEntranceRoomId());
        seen.add(level.getEntranceRoomId());
        while (!queue.isEmpty()) {
            Room room = level.room(queue.poll());
            for (Exit exit : room.getExits().values()) {
                if (exit.isKnown() && seen.add(exit.getDestinationRoomId())) {
                    queue.add(exit.getDestinationRoomId());
                }
            }
        }
        return seen;
    }

    // --- Exploration service ----------------------------------------------

    @Test
    void enterStartsTheDelveAndLightsATorch() {
        Dice dice = new Dice(new Random(3));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)));
        SaveGame save = newSaveWithCharacter(6);

        var result = service.enter(save);

        assertThat(save.getSession().getState()).isEqualTo(SessionState.EXPLORING);
        assertThat(save.getSession().isInDungeon()).isTrue();
        assertThat(save.getCharacter().getTorches()).isEqualTo(5); // one lit on entry
        assertThat(save.getSession().currentRoom().isVisited()).isTrue();
        assertThat(result.text()).isNotBlank();
    }

    @Test
    void movingAdvancesDungeonTurnsAndBurnsLight() {
        Dice dice = new Dice(new Random(11));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)));
        SaveGame save = twoRoomSave(dice, 0); // no spare torches; current torch has 6 turns

        // Alternate east/west between the two rooms. Starting in room 0, even steps go east, odd west,
        // so each move is always valid. Neutralize any wandering monster that appears so it does not
        // start combat and block the next move — we are only measuring turns and light here.
        for (int i = 0; i < 6; i++) {
            if (i == 5) {
                // After five moves the torch still has one turn of light left.
                assertThat(save.getSession().getDungeonTurn()).isEqualTo(5);
                assertThat(save.getSession().isInDarkness()).isFalse();
            }
            service.move(save, (i % 2 == 0) ? Direction.EAST : Direction.WEST);
            save.getSession().setState(SessionState.EXPLORING);
            save.getSession().setCombat(null);
            save.getSession().currentRoom().setCleared(true);
        }

        // The sixth turn exhausts the torch with no spare -> darkness.
        assertThat(save.getSession().getDungeonTurn()).isEqualTo(6);
        assertThat(save.getSession().isInDarkness()).isTrue();
    }

    @Test
    void searchGathersTreasureAndCanRevealSecretDoors() {
        Dice dice = new Dice(new Random(5));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)));
        SaveGame save = twoRoomSave(dice, 3);
        Room room = save.getSession().currentRoom();
        room.setHasTreasure(true);
        room.setTreasureGold(120);
        // Add a hidden secret door.
        Exit secret = new Exit(Direction.NORTH, 1, DoorState.CLOSED, true);
        room.getExits().put(Direction.NORTH, secret);

        int goldBefore = save.getCharacter().getGold();
        service.search(save);
        assertThat(save.getCharacter().getGold()).isEqualTo(goldBefore + 120);
        assertThat(room.isLooted()).isTrue();

        // Keep searching until the 1-in-6 reveals the secret door (proves the mechanic fires).
        for (int i = 0; i < 100 && !secret.isRevealed(); i++) {
            service.search(save);
        }
        assertThat(secret.isRevealed()).isTrue();
    }

    @Test
    void trapsSpringSometimesAndCanBeAvoidedWhenDetected() {
        Dice dice = new Dice(new Random(8));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)));

        int sprang = 0;
        for (int trial = 0; trial < 200; trial++) {
            SaveGame save = twoRoomSave(dice, 9);
            Room target = save.getSession().currentLevel().room(1);
            target.setTrapped(true);
            target.setTrapDescription("a concealed pit");
            target.setTrapDamage(new DamageRoll(1, 6));
            target.setVisited(false);
            int hpBefore = save.getCharacter().getCurrentHp();

            service.move(save, Direction.EAST);
            if (save.getCharacter().getCurrentHp() < hpBefore) {
                sprang++;
            }
        }
        assertThat(sprang).isGreaterThan(0); // traps do trigger
        assertThat(sprang).isLessThan(200); // ...but not every time

        // A detected trap is stepped around: no damage.
        SaveGame safe = twoRoomSave(dice, 9);
        Room target = safe.getSession().currentLevel().room(1);
        target.setTrapped(true);
        target.setTrapDetected(true);
        target.setTrapDamage(new DamageRoll(1, 6));
        target.setVisited(false);
        int hpBefore = safe.getCharacter().getCurrentHp();
        safe.getSession().setCurrentRoomId(0);
        for (int i = 0; i < 20; i++) {
            safe.getSession().currentLevel().room(1).setVisited(false);
            safe.getSession().setCurrentRoomId(0);
            service.move(safe, Direction.EAST);
        }
        assertThat(safe.getCharacter().getCurrentHp()).isEqualTo(hpBefore);
    }

    // --- helpers -----------------------------------------------------------

    private SaveGame newSaveWithCharacter(int torches) {
        Character c = new CharacterFactory(new Dice(new Random(1)))
                .create("Tester", CharacterClass.FIGHTER, new AbilityScores(13, 9, 9, 12, 14, 9));
        c.setTorches(torches);
        SaveGame save = new SaveGame();
        save.setCharacter(c);
        return save;
    }

    /** A deterministic 2-room level (rooms 0 and 1) joined by open passages both ways. */
    private SaveGame twoRoomSave(Dice dice, int torches) {
        SaveGame save = newSaveWithCharacter(torches);
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
        session.setTorchTurnsRemaining(6);
        return save;
    }
}
