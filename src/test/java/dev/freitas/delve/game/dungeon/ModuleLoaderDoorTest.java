package dev.freitas.delve.game.dungeon;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.dungeon.ModuleSchema.ModuleExit;
import dev.freitas.delve.game.dungeon.ModuleSchema.ModuleRoom;
import dev.freitas.delve.game.dungeon.ModuleSchema.ModuleTrap;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.DungeonLevel;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.Room;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link ModuleLoader}'s door-authoring support: parsing "unlocked"/"ajar", wiring a door-lock trap
    from JSON, and carrying both onto an auto-synthesized reverse exit. No JSON fixture file needed --
    {@link ModuleLoader#mapRoom}/{@link ModuleLoader#parseDoor}/{@link
    ModuleLoader#makeExitsBidirectional} are package-private specifically for this. */
class ModuleLoaderDoorTest {

    @Test
    void parseDoorRecognizesUnlockedAndAjar() {
        assertThat(ModuleLoader.parseDoor("unlocked")).isEqualTo(DoorState.UNLOCKED);
        assertThat(ModuleLoader.parseDoor("ajar")).isEqualTo(DoorState.AJAR);
        assertThat(ModuleLoader.parseDoor("locked")).isEqualTo(DoorState.LOCKED);
    }

    @Test
    void parseDoorRecognizesOneWayAndItsAlias() {
        assertThat(ModuleLoader.parseDoor("one-way")).isEqualTo(DoorState.ONE_WAY);
        assertThat(ModuleLoader.parseDoor("oneway")).isEqualTo(DoorState.ONE_WAY);
    }

    @Test
    void mapRoomMarksALockedDoorAsEverLocked() {
        ModuleExit me = new ModuleExit("north", 2, "locked", false, null, null);
        ModuleRoom mr = new ModuleRoom(1, "Vault", null, null, List.of(me), null, 0, null, null, null,
                false, false, null, null, 0);

        Room room = ModuleLoader.mapRoom(mr, new ArrayList<>());

        Exit exit = room.getExits().values().iterator().next();
        assertThat(exit.getDoor()).isEqualTo(DoorState.LOCKED);
        assertThat(exit.isEverLocked()).isTrue();
    }

    @Test
    void mapRoomWiresADoorTrapFromJson() {
        ModuleTrap doorTrap = new ModuleTrap("a poisoned needle in the lock");
        ModuleExit me = new ModuleExit("north", 2, "locked", false, null, doorTrap);
        ModuleRoom mr = new ModuleRoom(1, "Vault", null, null, List.of(me), null, 0, null, null, null,
                false, false, null, null, 0);

        Room room = ModuleLoader.mapRoom(mr, new ArrayList<>());

        Exit exit = room.getExits().values().iterator().next();
        assertThat(exit.isDoorTrapped()).isTrue();
        assertThat(exit.getDoorTrapDescription()).isEqualTo("a poisoned needle in the lock");
        assertThat(exit.getDoorTrapDamage()).isNotNull();
    }

    @Test
    void mapRoomLeavesDoorTrapUnsetWhenJsonOmitsIt() {
        ModuleExit me = new ModuleExit("north", 2, "closed", false, null, null);
        ModuleRoom mr = new ModuleRoom(1, "Hall", null, null, List.of(me), null, 0, null, null, null,
                false, false, null, null, 0);

        Room room = ModuleLoader.mapRoom(mr, new ArrayList<>());

        Exit exit = room.getExits().values().iterator().next();
        assertThat(exit.isDoorTrapped()).isFalse();
        assertThat(exit.isEverLocked()).isFalse();
    }

    @Test
    void bidirectionalSynthesisCarriesEverLockedAndDoorTrapOntoTheReturnExit() {
        ModuleTrap doorTrap = new ModuleTrap("a poisoned needle in the lock");
        ModuleExit me = new ModuleExit("north", 2, "locked", false, null, doorTrap);
        ModuleRoom keyed = new ModuleRoom(1, "Vault", null, null, List.of(me), null, 0, null, null, null,
                false, false, null, null, 0);
        // Room 2 keys no exit back to room 1 -- the loader must synthesize the return exit.
        ModuleRoom unkeyed = new ModuleRoom(2, "Antechamber", null, null, List.of(), null, 0, null, null,
                null, false, false, null, null, 0);

        DungeonLevel level = new DungeonLevel(1);
        level.addRoom(ModuleLoader.mapRoom(keyed, new ArrayList<>()));
        level.addRoom(ModuleLoader.mapRoom(unkeyed, new ArrayList<>()));

        ModuleLoader.makeExitsBidirectional(level);

        Exit back = level.room(2).getExits().values().stream()
                .filter(e -> e.getDestinationRoomId() == 1)
                .findFirst()
                .orElseThrow();
        assertThat(back.isEverLocked()).isTrue();
        assertThat(back.isDoorTrapped()).isTrue();
        assertThat(back.getDoorTrapDescription()).isEqualTo("a poisoned needle in the lock");
    }

    @Test
    void anAuthorKeyedOneWayReturnExitSurvivesBidirectionalSynthesisUntouched() {
        // Room 1 keys a normal, passable exit east toward room 2. Room 2 separately keys its own
        // return exit west as ONE_WAY -- both directions authored explicitly, unlike the
        // auto-synthesized case above where room 2 keys nothing at all.
        ModuleExit forward = new ModuleExit("east", 2, "closed", false, null, null);
        ModuleRoom room1 = new ModuleRoom(1, "Antechamber", null, null, List.of(forward), null, 0, null,
                null, null, false, false, null, null, 0);
        ModuleExit reverse = new ModuleExit("west", 1, "one-way", false, null, null);
        ModuleRoom room2 = new ModuleRoom(2, "Sealed Vault", null, null, List.of(reverse), null, 0, null,
                null, null, false, false, null, null, 0);

        DungeonLevel level = new DungeonLevel(1);
        level.addRoom(ModuleLoader.mapRoom(room1, new ArrayList<>()));
        level.addRoom(ModuleLoader.mapRoom(room2, new ArrayList<>()));

        ModuleLoader.makeExitsBidirectional(level);

        Exit forwardExit = level.room(1).getExits().values().stream()
                .filter(e -> e.getDestinationRoomId() == 2)
                .findFirst()
                .orElseThrow();
        Exit reverseExit = level.room(2).getExits().values().stream()
                .filter(e -> e.getDestinationRoomId() == 1)
                .findFirst()
                .orElseThrow();
        // Bidirectional synthesis only fills in a *missing* reverse exit -- since room 2 already keys
        // its own west exit, synthesis must leave it alone rather than overwriting it to match room 1's
        // CLOSED state.
        assertThat(forwardExit.getDoor()).isEqualTo(DoorState.CLOSED);
        assertThat(reverseExit.getDoor()).isEqualTo(DoorState.ONE_WAY);
    }
}
