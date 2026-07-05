package dev.freitas.delve.game.model;

/** The state of a connection between two rooms. */
public enum DoorState {
    /** An open archway/passage — always passable. */
    NONE,
    OPEN,
    /** Passable, left standing open — a flavor/authoring-only state signaling recent disturbance
        (never procedurally generated, never swings shut). */
    AJAR,
    CLOSED,
    /** Swollen/jammed shut; can be forced (B/X open-doors check). */
    STUCK,
    /** Needs a key or a thief's lockpicking. */
    LOCKED,
    /** Was {@link #LOCKED}, picked open by a Thief — shut again like {@link #CLOSED} (still needs a
        push), but kept distinct so it's never mistaken for still needing a key. */
    UNLOCKED;

    public boolean isPassable() {
        return this == NONE || this == OPEN || this == AJAR;
    }
}
