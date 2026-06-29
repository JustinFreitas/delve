package dev.freitas.delve.game.model;

/** The four cardinal exits from a room. */
public enum Direction {
    NORTH,
    SOUTH,
    EAST,
    WEST;

    public Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }

    public String lower() {
        return name().toLowerCase();
    }

    /** Parses {@code n/north}, {@code s/south}, {@code e/east}, {@code w/west}; null if unrecognized. */
    public static Direction parse(String input) {
        if (input == null) {
            return null;
        }
        return switch (input.trim().toLowerCase()) {
            case "n", "north" -> NORTH;
            case "s", "south" -> SOUTH;
            case "e", "east" -> EAST;
            case "w", "west" -> WEST;
            default -> null;
        };
    }
}
