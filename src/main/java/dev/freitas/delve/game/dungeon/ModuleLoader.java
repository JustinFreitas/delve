package dev.freitas.delve.game.dungeon;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.freitas.delve.game.Bestiary;
import dev.freitas.delve.game.dungeon.ModuleSchema.Module;
import dev.freitas.delve.game.dungeon.ModuleSchema.ModuleExit;
import dev.freitas.delve.game.dungeon.ModuleSchema.ModuleLevel;
import dev.freitas.delve.game.dungeon.ModuleSchema.ModuleRoom;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.model.ContentType;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.DungeonLevel;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.MonsterDisposition;
import dev.freitas.delve.game.model.MonsterType;
import dev.freitas.delve.game.model.Room;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads an authored {@link ModuleSchema.Module} from {@code content/modules/<name>.json} and maps it
 * into the runtime {@link Dungeon}/{@link DungeonLevel}/{@link Room}/{@link Exit} model the
 * {@code ExplorationService} already plays. Mirrors the {@link SetPieceLoader} pattern but resolves
 * modules from the working-directory {@code content/modules/} folder first (so a DM can drop in a new
 * module without rebuilding) and falls back to the classpath (for the bundled sample).
 *
 * <p>The mapping makes exits bidirectional and resolves monster names against the {@link Bestiary} so
 * combat works; unmatched names fall back to a stand-in and are reported as warnings.
 */
public final class ModuleLoader {

    private static final Logger log = LoggerFactory.getLogger(ModuleLoader.class);
    private static final Path MODULES_DIR = Path.of("content", "modules");
    private static final DamageRoll DEFAULT_TRAP_DAMAGE = new DamageRoll(1, 6);
    private static final String FALLBACK_MONSTER = "Orc";

    private static final ObjectMapper MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** The mapped dungeon plus any warnings worth surfacing to the DM (e.g. unmatched monsters). */
    public record LoadedModule(Dungeon dungeon, String title, List<String> warnings) {}

    private ModuleLoader() {}

    /** Module names available to load (filesystem {@code content/modules/} plus the bundled sample). */
    public static List<String> listModules() {
        TreeSet<String> names = new TreeSet<>();
        if (Files.isDirectory(MODULES_DIR)) {
            try (var stream = Files.list(MODULES_DIR)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .forEach(p -> names.add(stripJson(p.getFileName().toString())));
            } catch (IOException e) {
                log.warn("Could not list modules in {}", MODULES_DIR, e);
            }
        }
        if (ModuleLoader.class.getResource("/content/modules/sample.json") != null) {
            names.add("sample");
        }
        return new ArrayList<>(names);
    }

    public static boolean exists(String name) {
        return listModules().contains(sanitize(name));
    }

    /** Loads and maps the named module, or returns {@code null} if it isn't found. */
    public static LoadedModule load(String name) {
        String safe = sanitize(name);
        Module module = readModule(safe);
        if (module == null) {
            return null;
        }
        List<String> warnings = new ArrayList<>();
        Dungeon dungeon = map(module, warnings);
        String title = module.title() != null ? module.title() : safe;
        return new LoadedModule(dungeon, title, warnings);
    }

    private static Module readModule(String safe) {
        Path file = MODULES_DIR.resolve(safe + ".json");
        try {
            if (Files.isReadable(file)) {
                return MAPPER.readValue(Files.readString(file), Module.class);
            }
            try (InputStream in = ModuleLoader.class.getResourceAsStream("/content/modules/" + safe + ".json")) {
                if (in != null) {
                    return MAPPER.readValue(in, Module.class);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read module {}", safe, e);
        }
        return null;
    }

    /** Maps the authored schema into the runtime dungeon model. */
    static Dungeon map(Module module, List<String> warnings) {
        Dungeon dungeon = new Dungeon();
        List<ModuleLevel> levels = module.levels() == null ? List.of() : module.levels();
        for (ModuleLevel ml : levels) {
            DungeonLevel level = new DungeonLevel(ml.depth() > 0 ? ml.depth() : dungeon.levelCount() + 1);
            level.setEntranceRoomId(ml.entranceRoomId());
            List<ModuleRoom> rooms = ml.rooms() == null ? List.of() : ml.rooms();
            for (ModuleRoom mr : rooms) {
                level.addRoom(mapRoom(mr, warnings));
            }
            makeExitsBidirectional(level);
            dungeon.addLevel(level);
        }
        return dungeon;
    }

    /** Package-private: tests exercise this directly without a JSON fixture file. */
    static Room mapRoom(ModuleRoom mr, List<String> warnings) {
        Room room = new Room(mr.id());
        room.setName(mr.name());
        room.setDescription(mr.description() != null ? mr.description()
                : (mr.name() != null ? mr.name() : "an unremarkable area"));
        room.setReadAloud(mr.readAloud());
        // A record's missing "width" key deserializes to 0, not the desired default of 2 — must be
        // defaulted explicitly here or every existing module (none carry a width key) would silently
        // become 1-wide.
        room.setCorridorWidth(mr.width() > 0 ? mr.width() : 2);

        if (mr.exits() != null) {
            for (ModuleExit me : mr.exits()) {
                Direction dir = Direction.parse(me.direction());
                if (dir == null) {
                    warnings.add("Room " + mr.id() + ": unknown exit direction '" + me.direction() + "' (skipped).");
                    continue;
                }
                DoorState doorState = parseDoor(me.door());
                Exit exit = new Exit(dir, me.toRoomId(), doorState, me.secret());
                if (doorState == DoorState.LOCKED) {
                    exit.setEverLocked(true);
                }
                if (me.trap() != null && me.trap().description() != null) {
                    exit.setTrapped(true);
                    exit.setTrapDescription(me.trap().description());
                    exit.setTrapDamage(DEFAULT_TRAP_DAMAGE);
                }
                if (me.doorTrap() != null && me.doorTrap().description() != null) {
                    exit.setDoorTrapped(true);
                    exit.setDoorTrapDescription(me.doorTrap().description());
                    exit.setDoorTrapDamage(DEFAULT_TRAP_DAMAGE);
                }
                room.getExits().put(dir, exit);
            }
        }

        boolean hasMonster = mr.monster() != null && mr.monster().count() > 0 && mr.monster().name() != null;
        if (hasMonster) {
            room.setContent(ContentType.MONSTER);
            room.setMonsterName(resolveMonster(mr.monster().name(), mr.id(), warnings));
            room.setMonsterCount(mr.monster().count());
            room.setScriptedDisposition(parseDisposition(mr.monster().disposition()));
        } else if (mr.special() != null && !mr.special().isBlank()) {
            room.setContent(ContentType.SPECIAL);
        }
        if (mr.special() != null && !mr.special().isBlank()) {
            room.setSpecialText(mr.special());
        }

        if (mr.trap() != null && mr.trap().description() != null) {
            room.setTrapped(true);
            room.setTrapDescription(mr.trap().description());
            room.setTrapDamage(DEFAULT_TRAP_DAMAGE);
        }

        if (mr.treasureGold() > 0) {
            room.setHasTreasure(true);
            room.setTreasureGold(mr.treasureGold());
        }
        if (mr.treasureTrap() != null && mr.treasureTrap().description() != null) {
            room.setTreasureTrapped(true);
            room.setTreasureTrapDescription(mr.treasureTrap().description());
            room.setTreasureTrapDamage(DEFAULT_TRAP_DAMAGE);
        }

        room.setStairsDown(mr.stairsDown());
        room.setStairsUp(mr.stairsUp());
        if (mr.stairsToLevel() != null) {
            room.setStairsDestinationLevel(mr.stairsToLevel());
        }
        if (mr.stairsToRoom() != null) {
            room.setStairsDestinationRoomId(mr.stairsToRoom());
        }
        return room;
    }

    /** Ensures every connection is traversable both ways (modules often key an exit on one side only).
        Package-private: tests exercise this directly without a JSON fixture file. */
    static void makeExitsBidirectional(DungeonLevel level) {
        for (Room room : new ArrayList<>(level.getRooms().values())) {
            for (Exit exit : new ArrayList<>(room.getExits().values())) {
                Room dest = level.room(exit.getDestinationRoomId());
                if (dest == null) {
                    continue;
                }
                Direction back = exit.getDirection().opposite();
                if (!dest.getExits().containsKey(back)) {
                    Exit returnExit = new Exit(back, room.getId(), exit.getDoor(), false);
                    returnExit.setEverLocked(exit.isEverLocked());
                    if (exit.isDoorTrapped()) {
                        returnExit.setDoorTrapped(true);
                        returnExit.setDoorTrapDescription(exit.getDoorTrapDescription());
                        returnExit.setDoorTrapDamage(exit.getDoorTrapDamage());
                    }
                    dest.getExits().put(back, returnExit);
                }
            }
        }
    }

    /** Resolves a module's monster label to a Bestiary name combat can use; falls back with a warning. */
    private static String resolveMonster(String raw, int roomId, List<String> warnings) {
        MonsterType direct = Bestiary.byName(raw);
        if (direct != null) {
            return direct.name();
        }
        String n = raw.toLowerCase(Locale.ROOT).trim();
        String singular = n.endsWith("s") ? n.substring(0, n.length() - 1) : n;
        MonsterType bySingular = Bestiary.byName(singular);
        if (bySingular != null) {
            return bySingular.name();
        }
        String alias = switch (singular) {
            case "rat", "giant rat" -> "Giant Rat";
            case "hobgoblin" -> "Hobgoblin";
            case "bandit", "brigand", "thug" -> "Orc";
            default -> null;
        };
        if (alias != null && Bestiary.byName(alias) != null) {
            return Bestiary.byName(alias).name();
        }
        warnings.add("Room " + roomId + ": monster '" + raw + "' not in the bestiary; using "
                + FALLBACK_MONSTER + " as a stand-in (edit the module JSON to fix).");
        return FALLBACK_MONSTER;
    }

    /** Parses an authored disposition override; unrecognized or absent text means "roll for it". */
    private static MonsterDisposition parseDisposition(String disposition) {
        if (disposition == null || disposition.isBlank()) {
            return null;
        }
        return switch (disposition.toLowerCase(Locale.ROOT).trim()) {
            case "hostile" -> MonsterDisposition.HOSTILE;
            case "neutral" -> MonsterDisposition.NEUTRAL;
            case "friendly" -> MonsterDisposition.FRIENDLY;
            default -> null;
        };
    }

    /** Package-private: tests exercise this directly without a JSON fixture file. */
    static DoorState parseDoor(String door) {
        if (door == null || door.isBlank()) {
            return DoorState.NONE;
        }
        return switch (door.toLowerCase(Locale.ROOT).trim()) {
            case "open", "none", "archway", "passage" -> DoorState.NONE;
            case "ajar" -> DoorState.AJAR;
            case "closed", "door" -> DoorState.CLOSED;
            case "stuck" -> DoorState.STUCK;
            case "locked" -> DoorState.LOCKED;
            case "unlocked" -> DoorState.UNLOCKED;
            case "one-way", "oneway" -> DoorState.ONE_WAY;
            default -> DoorState.NONE;
        };
    }

    private static String stripJson(String fileName) {
        return fileName.substring(0, fileName.length() - ".json".length());
    }

    /** Guards against path traversal in a user-supplied module name. */
    private static String sanitize(String name) {
        return name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9_-]", "");
    }
}
