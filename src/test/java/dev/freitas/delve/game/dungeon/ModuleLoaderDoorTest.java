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
}
