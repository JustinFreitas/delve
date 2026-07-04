package dev.freitas.delve.game.engine;

/** The five B/X-style reaction-roll bands (2d6 + CHA modifier), from most to least hostile. */
public enum ReactionTier {
    ATTACKS,
    HOSTILE,
    UNCERTAIN,
    INDIFFERENT,
    FRIENDLY;

    /** Whether this tier starts combat immediately. */
    public boolean isHostile() {
        return this == ATTACKS || this == HOSTILE;
    }
}
