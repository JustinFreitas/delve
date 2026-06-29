package dev.freitas.delve.game.model;

/** The state of a connection between two rooms. */
public enum DoorState {
    /** An open archway/passage — always passable. */
    NONE,
    OPEN,
    CLOSED,
    /** Swollen/jammed shut; can be forced (B/X open-doors check). */
    STUCK,
    /** Needs a key or a thief's lockpicking. */
    LOCKED;

    public boolean isPassable() {
        return this == NONE || this == OPEN;
    }
}
