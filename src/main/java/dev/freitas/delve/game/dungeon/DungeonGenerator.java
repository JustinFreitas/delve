package dev.freitas.delve.game.dungeon;

import dev.freitas.delve.game.Bestiary;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.ContentType;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.DungeonLevel;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.MonsterType;
import dev.freitas.delve.game.model.Room;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Generates a multi-level B/X dungeon: each level is a connected graph of rooms (a random spanning
 * tree guarantees every room is reachable without searching, plus a few extra edges for loops and
 * secret doors), stocked with the Moldvay-style table — monster / trap / special / empty, each with a
 * chance of treasure. Monsters are drawn depth-appropriately from the {@link Bestiary}.
 *
 * <p>Authored set-pieces can be slotted into generated levels later (Milestone 7); the procedural
 * path stands alone.
 */
@Component
public class DungeonGenerator {

    private static final String[] SIZES = {"a cramped", "a small", "a modest", "a large", "a vast"};
    private static final String[] FEATURES = {
        "chamber with a low ceiling",
        "hall lined with crumbling pillars",
        "cave of damp, dripping stone",
        "room strewn with rubble",
        "crypt thick with dust",
        "gallery of faded murals",
        "chamber half-flooded with stagnant water",
        "vault with an arched ceiling"
    };
    private static final String[] SPECIALS = {
        "A dry stone fountain dominates the room.",
        "Strange runes glow faintly on one wall.",
        "A weathered statue of a forgotten god stands here.",
        "A cold draft rises from a crack in the floor.",
        "Old bones are arranged in a deliberate spiral."
    };
    private static final String[] TRAPS = {
        "a concealed pit",
        "a dart trap in the doorway",
        "a collapsing-ceiling trigger",
        "a scything blade",
        "a poisoned needle on the latch"
    };

    private final Dice dice;

    public DungeonGenerator(Dice dice) {
        this.dice = dice;
    }

    public Dungeon generate(int numLevels, int roomsPerLevel) {
        Dungeon dungeon = new Dungeon();
        for (int depth = 1; depth <= numLevels; depth++) {
            dungeon.addLevel(generateLevel(depth, roomsPerLevel));
        }
        linkStairs(dungeon);
        stampSetPiece(dungeon);
        return dungeon;
    }

    /** Stamps one authored set-piece into the deepest level, over a non-entrance, non-stairs room. */
    private void stampSetPiece(Dungeon dungeon) {
        List<SetPiece> setPieces = SetPieceLoader.load();
        if (setPieces.isEmpty()) {
            return;
        }
        DungeonLevel deepest = dungeon.level(dungeon.levelCount() - 1);
        List<Room> candidates = new ArrayList<>();
        for (Room r : deepest.getRooms().values()) {
            if (r.getId() != deepest.getEntranceRoomId() && !r.isStairsUp() && !r.isStairsDown()) {
                candidates.add(r);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        SetPiece piece = setPieces.get(dice.d(setPieces.size()) - 1);
        Room room = candidates.get(dice.d(candidates.size()) - 1);
        room.setDescription(piece.description());
        room.setSpecialText(piece.specialText());
        if (piece.guardianMonster() != null && piece.guardianCount() > 0) {
            room.setContent(ContentType.MONSTER);
            room.setMonsterName(piece.guardianMonster());
            room.setMonsterCount(piece.guardianCount());
            room.setCleared(false);
        } else {
            room.setContent(ContentType.SPECIAL);
        }
        if (piece.treasureGold() > 0) {
            room.setHasTreasure(true);
            room.setTreasureGold(piece.treasureGold());
        }
    }

    private DungeonLevel generateLevel(int depth, int roomCount) {
        DungeonLevel level = new DungeonLevel(depth);
        List<Room> rooms = new ArrayList<>();
        Map<Integer, Set<Direction>> free = new java.util.HashMap<>();
        for (int i = 0; i < roomCount; i++) {
            Room room = new Room(i);
            room.setDescription(describe());
            rooms.add(room);
            level.addRoom(room);
            free.put(i, EnumSet.allOf(Direction.class));
        }

        // Spanning tree: connect each new room to an earlier one (every room reachable without searching).
        for (int i = 1; i < roomCount; i++) {
            connectToEarlier(rooms, free, i, false);
        }
        // Extra edges add loops; these may be locked or secret since they are never the sole path.
        int extra = roomCount / 3;
        for (int k = 0; k < extra; k++) {
            int a = dice.d(roomCount) - 1;
            int b = dice.d(roomCount) - 1;
            if (a != b) {
                tryConnect(rooms, free, a, b, true);
            }
        }

        level.setEntranceRoomId(0);
        rooms.get(0).setVisited(true);
        // Stock every room except the entrance.
        for (int i = 1; i < roomCount; i++) {
            stock(rooms.get(i), depth);
        }
        return level;
    }

    private void connectToEarlier(List<Room> rooms, Map<Integer, Set<Direction>> free, int i, boolean allowSpecial) {
        List<Integer> earlier = new ArrayList<>();
        for (int j = 0; j < i; j++) {
            earlier.add(j);
        }
        shuffle(earlier);
        for (int j : earlier) {
            if (tryConnect(rooms, free, j, i, allowSpecial)) {
                return;
            }
        }
        // Fallback: force-connect to room 0 on any pair of directions to preserve connectivity.
        forceConnect(rooms, free, 0, i);
    }

    private boolean tryConnect(
            List<Room> rooms, Map<Integer, Set<Direction>> free, int a, int b, boolean allowSpecial) {
        if (rooms.get(a).getExits().values().stream().anyMatch(e -> e.getDestinationRoomId() == b)) {
            return false; // already directly connected
        }
        for (Direction d : shuffledDirections()) {
            if (free.get(a).contains(d) && free.get(b).contains(d.opposite())) {
                connect(rooms, free, a, b, d, allowSpecial);
                return true;
            }
        }
        return false;
    }

    private void forceConnect(List<Room> rooms, Map<Integer, Set<Direction>> free, int a, int b) {
        for (Direction d : Direction.values()) {
            if (free.get(a).contains(d) && free.get(b).contains(d.opposite())) {
                connect(rooms, free, a, b, d, false);
                return;
            }
        }
    }

    private void connect(
            List<Room> rooms, Map<Integer, Set<Direction>> free, int a, int b, Direction d, boolean allowSpecial) {
        DoorState door = rollDoor(allowSpecial);
        boolean secret = allowSpecial && dice.d(6) == 1; // secret doors only on optional (extra) edges
        if (secret) {
            door = DoorState.CLOSED;
        }
        Room ra = rooms.get(a);
        Room rb = rooms.get(b);
        ra.getExits().put(d, new Exit(d, b, door, secret));
        rb.getExits().put(d.opposite(), new Exit(d.opposite(), a, door, secret));
        free.get(a).remove(d);
        free.get(b).remove(d.opposite());
    }

    private DoorState rollDoor(boolean allowLocked) {
        int r = dice.d(10);
        if (r <= 5) {
            return DoorState.NONE; // open passage
        } else if (r <= 8) {
            return DoorState.CLOSED;
        } else if (r == 9) {
            return DoorState.STUCK;
        } else {
            return allowLocked ? DoorState.LOCKED : DoorState.CLOSED;
        }
    }

    /** Moldvay-style stocking: contents on 1d6, then a content-dependent treasure chance. */
    private void stock(Room room, int depth) {
        int contents = dice.d(6);
        if (contents <= 2) {
            room.setContent(ContentType.MONSTER);
            MonsterType type = pickMonster(depth);
            room.setMonsterName(type.name());
            room.setMonsterCount(Math.max(1, Math.min(depth + 2, rollNumberAppearing(type.numberAppearing()))));
            if (dice.d(6) <= 3) {
                addTreasure(room, depth);
            }
        } else if (contents == 3) {
            room.setContent(ContentType.TRAP);
            room.setTrapped(true);
            room.setTrapDescription(TRAPS[dice.d(TRAPS.length) - 1]);
            room.setTrapDamage(new DamageRoll(1, 6));
            if (dice.d(6) <= 2) {
                addTreasure(room, depth);
            }
        } else if (contents == 4) {
            room.setContent(ContentType.SPECIAL);
            room.setSpecialText(SPECIALS[dice.d(SPECIALS.length) - 1]);
        } else {
            room.setContent(ContentType.EMPTY);
            if (dice.d(6) == 1) {
                addTreasure(room, depth);
            }
        }
    }

    private void addTreasure(Room room, int depth) {
        room.setHasTreasure(true);
        room.setTreasureGold(dice.roll(2, 6) * 10 * depth);
    }

    private MonsterType pickMonster(int depth) {
        List<MonsterType> eligible = new ArrayList<>();
        for (MonsterType type : Bestiary.all()) {
            int effectiveHd = type.hitDiceCount() + (type.hitDiceBonus() > 0 ? 1 : 0);
            if (effectiveHd <= depth + 1) {
                eligible.add(type);
            }
        }
        if (eligible.isEmpty()) {
            eligible = Bestiary.all();
        }
        return eligible.get(dice.d(eligible.size()) - 1);
    }

    /** Parses and rolls a simple {@code XdY} expression (the monster's number appearing). */
    private int rollNumberAppearing(String expr) {
        try {
            String[] parts = expr.toLowerCase().split("d");
            int count = Integer.parseInt(parts[0].trim());
            int sides = Integer.parseInt(parts[1].trim());
            return dice.roll(count, sides);
        } catch (RuntimeException e) {
            return 1;
        }
    }

    /** Links each level's down-stairs room to the next level's entrance (and back up). */
    private void linkStairs(Dungeon dungeon) {
        for (int i = 0; i < dungeon.levelCount() - 1; i++) {
            DungeonLevel current = dungeon.level(i);
            DungeonLevel next = dungeon.level(i + 1);
            Room downRoom = pickStairRoom(current);
            downRoom.setStairsDown(true);
            downRoom.setStairsDestinationLevel(i + 1);
            downRoom.setStairsDestinationRoomId(next.getEntranceRoomId());

            Room upRoom = next.room(next.getEntranceRoomId());
            upRoom.setStairsUp(true);
            upRoom.setStairsDestinationLevel(i);
            upRoom.setStairsDestinationRoomId(downRoom.getId());
        }
    }

    /** Prefers a stocked room that isn't the entrance for the down-stairs. */
    private Room pickStairRoom(DungeonLevel level) {
        List<Room> candidates = new ArrayList<>();
        for (Room r : level.getRooms().values()) {
            if (r.getId() != level.getEntranceRoomId() && !r.isStairsDown()) {
                candidates.add(r);
            }
        }
        if (candidates.isEmpty()) {
            return level.room(level.getEntranceRoomId());
        }
        return candidates.get(dice.d(candidates.size()) - 1);
    }

    private String describe() {
        return SIZES[dice.d(SIZES.length) - 1] + " " + FEATURES[dice.d(FEATURES.length) - 1];
    }

    private List<Direction> shuffledDirections() {
        List<Direction> dirs = new ArrayList<>(List.of(Direction.values()));
        shuffle(dirs);
        return dirs;
    }

    private <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = dice.d(i + 1) - 1;
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }
}
