package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.LightSource;
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
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.ExplorationService;
import dev.freitas.delve.game.session.LightingService;
import dev.freitas.delve.game.session.MuleService;
import dev.freitas.delve.game.session.SpellService;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@code DoorState}'s locked/unlocked/ajar states, lock-picking, door-lock traps, swing-shut,
    spike/wedge, and one-way doors — the gygax75 "doors" backlog item. */
class DoorStateTest {

    // --- Locked / unlocked / lock-picking -----------------------------------

    @Test
    void nonThiefCannotAttemptALockedDoor() {
        Dice dice = new Dice(new Random(50));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, CharacterClass.FIGHTER, 9, DoorState.LOCKED);

        ExplorationResult result = service.open(save, Direction.EAST);

        assertThat(result.text()).contains("key or a thief's touch");
        assertThat(save.getSession().getDungeonTurn()).isZero(); // no attempt made, no turn spent
        assertThat(exitEast(save).getDoor()).isEqualTo(DoorState.LOCKED);
    }

    @Test
    void thiefEventuallyPicksALockedDoorButStillNeedsAPush() {
        Dice dice = new Dice(new Random(51));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, CharacterClass.THIEF, 9, DoorState.LOCKED);
        Exit exit = exitEast(save);

        for (int i = 0; i < 200 && exit.getDoor() == DoorState.LOCKED; i++) {
            service.open(save, Direction.EAST);
        }

        assertThat(exit.getDoor()).isEqualTo(DoorState.UNLOCKED);
        assertThat(exit.isEverLocked()).isTrue();
        assertThat(exit.getDoor().isPassable()).isFalse(); // picked, but still shut

        service.open(save, Direction.EAST);
        assertThat(exit.getDoor()).isEqualTo(DoorState.OPEN);
    }

    @Test
    void unlockedDoorOpensJustLikeAClosedOneWithNoTurnCost() {
        Dice dice = new Dice(new Random(52));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, CharacterClass.FIGHTER, 9, DoorState.UNLOCKED);

        ExplorationResult result = service.open(save, Direction.EAST);

        assertThat(exitEast(save).getDoor()).isEqualTo(DoorState.OPEN);
        assertThat(save.getSession().getDungeonTurn()).isZero();
        assertThat(result.text()).contains("open the door");
    }

    @Test
    void ajarDoorIsAlreadyPassable() {
        Dice dice = new Dice(new Random(53));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, CharacterClass.FIGHTER, 9, DoorState.AJAR);

        assertThat(exitEast(save).getDoor().isPassable()).isTrue();
        ExplorationResult result = service.open(save, Direction.EAST);
        assertThat(result.text()).contains("already open");
    }

    // --- Swing-shut ----------------------------------------------------------

    @Test
    void aClosedOriginDoorSwingsShutBehindTheParty() {
        Dice dice = new Dice(new Random(54));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, CharacterClass.FIGHTER, 9, DoorState.CLOSED);
        Exit forward = exitEast(save);
        service.open(save, Direction.EAST);
        assertThat(forward.getDoor()).isEqualTo(DoorState.OPEN);

        ExplorationResult result = service.move(save, Direction.EAST);

        assertThat(result.text()).contains("swings shut behind you");
        assertThat(forward.getDoor()).isEqualTo(DoorState.CLOSED);
        // Mirrored to the other side too.
        Exit back = save.getSession().currentLevel().room(1).getExits().get(Direction.WEST);
        assertThat(back.getDoor()).isEqualTo(DoorState.CLOSED);
    }

    @Test
    void aFormerlyLockedDoorSwingsShutToUnlockedNotClosed() {
        Dice dice = new Dice(new Random(55));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, CharacterClass.THIEF, 9, DoorState.LOCKED);
        Exit exit = exitEast(save);
        for (int i = 0; i < 200 && exit.getDoor() == DoorState.LOCKED; i++) {
            service.open(save, Direction.EAST);
        }
        service.open(save, Direction.EAST); // push it open
        assertThat(exit.getDoor()).isEqualTo(DoorState.OPEN);

        service.move(save, Direction.EAST);

        assertThat(exit.getDoor()).isEqualTo(DoorState.UNLOCKED);
    }

    @Test
    void aSpikedOpenDoorNeverSwingsShut() {
        Dice dice = new Dice(new Random(56));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, CharacterClass.FIGHTER, 9, DoorState.CLOSED);
        Exit forward = exitEast(save);
        service.open(save, Direction.EAST);
        service.spike(save, Direction.EAST, true);
        assertThat(forward.getDoor()).isEqualTo(DoorState.OPEN);

        ExplorationResult result = service.move(save, Direction.EAST);

        assertThat(result.text()).doesNotContain("swings shut");
        assertThat(forward.getDoor()).isEqualTo(DoorState.OPEN);
    }

    // --- Spike / wedge ---------------------------------------------------------

    @Test
    void spikingOpenRequiresTheDoorToAlreadyBeOpen() {
        Dice dice = new Dice(new Random(57));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, CharacterClass.FIGHTER, 9, DoorState.CLOSED);

        ExplorationResult result = service.spike(save, Direction.EAST, true);

        assertThat(result.isSuccess()).isFalse();
        assertThat(exitEast(save).isSpiked()).isFalse();
    }

    @Test
    void spikingClosedJamsAnyPresentDoorIntoStuck() {
        Dice dice = new Dice(new Random(58));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, CharacterClass.FIGHTER, 9, DoorState.OPEN);

        ExplorationResult result = service.spike(save, Direction.EAST, false);

        assertThat(result.isSuccess()).isTrue();
        assertThat(exitEast(save).getDoor()).isEqualTo(DoorState.STUCK);
        assertThat(exitEast(save).isSpiked()).isTrue();
        assertThat(save.getSession().getDungeonTurn()).isEqualTo(1); // spiking costs a turn
    }

    @Test
    void cannotSpikeTheSameDoorTwice() {
        Dice dice = new Dice(new Random(59));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, CharacterClass.FIGHTER, 9, DoorState.OPEN);
        service.spike(save, Direction.EAST, true);

        ExplorationResult result = service.spike(save, Direction.EAST, true);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.text()).contains("already");
    }

    // --- Door-lock traps -------------------------------------------------------

    @Test
    void doorTrapIsDetectedBySearching() {
        Dice dice = new Dice(new Random(60));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, CharacterClass.FIGHTER, 9, DoorState.LOCKED);
        Exit exit = exitEast(save);
        exit.setDoorTrapped(true);
        exit.setDoorTrapDescription("a poisoned needle on the latch");
        exit.setDoorTrapDamage(new DamageRoll(1, 6));

        for (int i = 0; i < 100 && !exit.isDoorTrapDetected(); i++) {
            service.search(save);
        }
        assertThat(exit.isDoorTrapDetected()).isTrue();
    }

    @Test
    void doorTrapCanSpringOnAForceOrPickAttemptButNeverOnceDetected() {
        Dice dice = new Dice(new Random(61));
        ExplorationService service = newService(dice);

        int sprang = 0;
        for (int trial = 0; trial < 200; trial++) {
            SaveGame save = twoRoomSave(dice, CharacterClass.FIGHTER, 9, DoorState.STUCK);
            Exit exit = exitEast(save);
            exit.setDoorTrapped(true);
            exit.setDoorTrapDescription("a poisoned needle on the latch");
            exit.setDoorTrapDamage(new DamageRoll(1, 6));
            int hpBefore = save.getCharacter().getCurrentHp();

            service.open(save, Direction.EAST);
            if (save.getCharacter().getCurrentHp() < hpBefore) {
                sprang++;
            }
        }
        assertThat(sprang).isGreaterThan(0);
        assertThat(sprang).isLessThan(200);

        SaveGame safe = twoRoomSave(dice, CharacterClass.FIGHTER, 9, DoorState.STUCK);
        Exit safeExit = exitEast(safe);
        safeExit.setDoorTrapped(true);
        safeExit.setDoorTrapDetected(true);
        safeExit.setDoorTrapDamage(new DamageRoll(1, 6));
        int hpBefore = safe.getCharacter().getCurrentHp();
        for (int i = 0; i < 20 && safeExit.getDoor() == DoorState.STUCK; i++) {
            service.open(safe, Direction.EAST);
        }
        assertThat(safe.getCharacter().getCurrentHp()).isEqualTo(hpBefore);
    }

    // --- One-way doors -----------------------------------------------------------

    @Test
    void forwardTraversalThroughAOneWayDoorWorksNormally() {
        Dice dice = new Dice(new Random(70));
        ExplorationService service = newService(dice);
        SaveGame save = asymmetricTwoRoomSave(dice, DoorState.NONE, DoorState.ONE_WAY);

        ExplorationResult result = service.move(save, Direction.EAST);

        assertThat(result.isSuccess()).isTrue();
        assertThat(save.getSession().getCurrentRoomId()).isEqualTo(1);
    }

    @Test
    void movingBackThroughAOneWayDoorFailsWithoutSuggestingOpen() {
        Dice dice = new Dice(new Random(71));
        ExplorationService service = newService(dice);
        SaveGame save = asymmetricTwoRoomSave(dice, DoorState.NONE, DoorState.ONE_WAY);
        service.move(save, Direction.EAST); // now standing in room 1, facing the blocked exit west

        ExplorationResult result = service.move(save, Direction.WEST);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.text()).contains("won't budge").contains("only opens from the other side");
        assertThat(result.text()).doesNotContain("`open");
        assertThat(save.getSession().getCurrentRoomId()).isEqualTo(1); // didn't move
    }

    @Test
    void openingAOneWayDoorFromTheBlockedSideAlwaysFailsWithNoStateChangeOrDiceRoll() {
        Dice dice = new Dice(new Random(72));
        ExplorationService service = newService(dice);
        SaveGame save = asymmetricTwoRoomSave(dice, DoorState.NONE, DoorState.ONE_WAY);
        service.move(save, Direction.EAST);
        Exit blocked = save.getSession().currentRoom().getExits().get(Direction.WEST);
        int turnAfterMove = save.getSession().getDungeonTurn();

        for (int i = 0; i < 20; i++) {
            ExplorationResult result = service.open(save, Direction.WEST);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.text()).contains("only opens from the other side");
        }
        assertThat(blocked.getDoor()).isEqualTo(DoorState.ONE_WAY); // never budged
        assertThat(save.getSession().getDungeonTurn()).isEqualTo(turnAfterMove); // no turn spent on a doomed attempt
    }

    @Test
    void spikingAOneWayDoorRefusesForBothHoldOpenAndHoldShut() {
        Dice dice = new Dice(new Random(73));
        ExplorationService service = newService(dice);
        SaveGame save = asymmetricTwoRoomSave(dice, DoorState.NONE, DoorState.ONE_WAY);
        service.move(save, Direction.EAST);
        Exit blocked = save.getSession().currentRoom().getExits().get(Direction.WEST);

        ExplorationResult holdOpen = service.spike(save, Direction.WEST, true);
        ExplorationResult holdShut = service.spike(save, Direction.WEST, false);

        assertThat(holdOpen.isSuccess()).isFalse();
        assertThat(holdShut.isSuccess()).isFalse();
        assertThat(blocked.getDoor()).isEqualTo(DoorState.ONE_WAY); // not corrupted into STUCK/OPEN
        assertThat(blocked.isSpiked()).isFalse();
    }

    @Test
    void roomDescriptionLabelsAOneWayExitDistinctlyFromAnOrdinaryDoor() {
        Dice dice = new Dice(new Random(74));
        ExplorationService service = newService(dice);
        SaveGame save = asymmetricTwoRoomSave(dice, DoorState.NONE, DoorState.ONE_WAY);

        ExplorationResult result = service.move(save, Direction.EAST);

        assertThat(result.text()).contains("west (one-way door)");
    }

    // --- Rendering -------------------------------------------------------------

    @Test
    void doorTrapGeneratedOnlyOnStuckOrLockedDoors() {
        // A door-lock trap should never appear on a plain CLOSED (or NONE/OPEN) door.
        for (long seed = 1; seed <= 40; seed++) {
            DungeonGenerator generator = new DungeonGenerator(new Dice(new Random(seed)));
            Dungeon dungeon = generator.generate(2, 10);
            for (DungeonLevel level : dungeon.getLevels()) {
                for (Room room : level.getRooms().values()) {
                    for (Exit exit : room.getExits().values()) {
                        if (exit.isDoorTrapped()) {
                            assertThat(exit.getDoor()).isIn(DoorState.STUCK, DoorState.LOCKED);
                        }
                    }
                }
            }
        }
    }

    // --- helpers -----------------------------------------------------------

    private Exit exitEast(SaveGame save) {
        return save.getSession().currentRoom().getExits().get(Direction.EAST);
    }

    private ExplorationService newService(Dice dice) {
        return new ExplorationService(
                dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
    }

    private SaveGame twoRoomSave(Dice dice, CharacterClass cls, int torches, DoorState doorState) {
        Character c = new CharacterFactory(dice).create("Tester", cls, new AbilityScores(9, 9, 9, 9, 9, 9));
        c.setTorches(torches);
        c.setShield(false); // a solo adventurer needs a free hand to carry their own torch
        SaveGame save = new SaveGame();
        save.setCharacter(c);
        GameSession session = save.getSession();

        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room a = new Room(0);
        a.setDescription("a test chamber");
        Room b = new Room(1);
        b.setDescription("another test chamber");
        a.getExits().put(Direction.EAST, new Exit(Direction.EAST, 1, doorState, false));
        b.getExits().put(Direction.WEST, new Exit(Direction.WEST, 0, doorState, false));
        level.addRoom(a);
        level.addRoom(b);
        level.setEntranceRoomId(0);
        dungeon.addLevel(level);

        session.setDungeon(dungeon);
        session.setCurrentLevel(0);
        session.setCurrentRoomId(0);
        session.setState(SessionState.EXPLORING);
        session.setLightTurnsRemaining(6);
        session.setActiveLight(LightSource.TORCH);
        session.setLightBearer(SaveGame.PLAYER_SLOT);
        return save;
    }

    /** As {@link #twoRoomSave}, but the forward (room 0 -> room 1) and reverse (room 1 -> room 0) exits
        get *different* door states, for testing a one-way door -- every other two-room fixture in this
        file deliberately keeps both sides identical. */
    private SaveGame asymmetricTwoRoomSave(Dice dice, DoorState forwardDoor, DoorState reverseDoor) {
        Character c = new CharacterFactory(dice).create("Tester", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
        c.setTorches(9);
        c.setShield(false);
        SaveGame save = new SaveGame();
        save.setCharacter(c);
        GameSession session = save.getSession();

        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room a = new Room(0);
        a.setDescription("a test chamber");
        Room b = new Room(1);
        b.setDescription("another test chamber");
        a.getExits().put(Direction.EAST, new Exit(Direction.EAST, 1, forwardDoor, false));
        b.getExits().put(Direction.WEST, new Exit(Direction.WEST, 0, reverseDoor, false));
        level.addRoom(a);
        level.addRoom(b);
        level.setEntranceRoomId(0);
        dungeon.addLevel(level);

        session.setDungeon(dungeon);
        session.setCurrentLevel(0);
        session.setCurrentRoomId(0);
        session.setState(SessionState.EXPLORING);
        session.setLightTurnsRemaining(6);
        session.setActiveLight(LightSource.TORCH);
        session.setLightBearer(SaveGame.PLAYER_SLOT);
        return save;
    }
}
