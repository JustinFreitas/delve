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
    UNLOCKED,
    /** Permanently impassable from this side — "whether mechanical or magical, only passable one way"
        (gygax75-rules). Never forced, picked, or spiked open; never procedurally generated (authoring-
        only, like {@link #AJAR}) — a DM keys this on the blocked side's {@code Exit} only, leaving the
        forward direction's own {@code Exit} with its own ordinary door state. */
    ONE_WAY;

    public boolean isPassable() {
        return this == NONE || this == OPEN || this == AJAR;
    }
}
