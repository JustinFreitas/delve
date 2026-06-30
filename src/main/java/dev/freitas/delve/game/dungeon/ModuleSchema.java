package dev.freitas.delve.game.dungeon;

import java.util.List;

/**
 * The hand-editable JSON schema for an authored module ({@code content/modules/<name>.json}) — the
 * output of the offline PDF converter and the source of truth a DM can correct before play. Kept
 * deliberately clean and B/X-flavored (not the internal {@link dev.freitas.delve.game.model.Room}
 * shape with its play-state flags); {@link ModuleLoader} maps it into the runtime dungeon model.
 *
 * <p>All nested types are records, so missing JSON fields deserialize to {@code null}/0 — a module
 * may omit {@code monster}, {@code trap}, {@code readAloud}, etc.
 */
public final class ModuleSchema {

    private ModuleSchema() {}

    public record Module(String title, List<ModuleLevel> levels) {}

    public record ModuleLevel(int depth, int entranceRoomId, List<ModuleRoom> rooms) {}

    public record ModuleRoom(
            int id,
            String name,
            String description,
            String readAloud,
            List<ModuleExit> exits,
            ModuleMonster monster,
            int treasureGold,
            ModuleTrap trap,
            String special,
            boolean stairsDown,
            boolean stairsUp,
            Integer stairsToLevel,
            Integer stairsToRoom) {}

    /** A connection to another keyed area, as read from the room's description ("door to area 7"). */
    public record ModuleExit(String direction, int toRoomId, String door, boolean secret) {}

    public record ModuleMonster(String name, int count) {}

    public record ModuleTrap(String description) {}
}
