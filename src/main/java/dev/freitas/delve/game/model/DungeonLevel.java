package dev.freitas.delve.game.model;

import java.util.HashMap;
import java.util.Map;

/** One dungeon level: a graph of rooms keyed by id, with a designated entrance. */
public class DungeonLevel {

    private int depth;
    private Map<Integer, Room> rooms = new HashMap<>();
    private int entranceRoomId;

    public DungeonLevel() {}

    public DungeonLevel(int depth) {
        this.depth = depth;
    }

    public Room room(int id) {
        return rooms.get(id);
    }

    public void addRoom(Room room) {
        rooms.put(room.getId(), room);
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public Map<Integer, Room> getRooms() {
        return rooms;
    }

    public void setRooms(Map<Integer, Room> rooms) {
        this.rooms = rooms;
    }

    public int getEntranceRoomId() {
        return entranceRoomId;
    }

    public void setEntranceRoomId(int entranceRoomId) {
        this.entranceRoomId = entranceRoomId;
    }
}
