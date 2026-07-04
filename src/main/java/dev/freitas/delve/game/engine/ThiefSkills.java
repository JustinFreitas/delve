package dev.freitas.delve.game.engine;

/**
 * Thief class skill percentages. Deliberately implements only Remove Traps (needed for treasure-trap
 * disarming) — Open Locks, Pick Pockets, Move Silently, Hide in Shadows, Climb Sheer Surfaces, Hear
 * Noise and Read Languages are out of scope for now, but a per-skill static method keeps adding them
 * later a mechanical addition rather than a redesign.
 */
public final class ThiefSkills {

    private ThiefSkills() {}

    /** Proposed default: classic Moldvay B/X progression — 10% at level 1, +5% per level thereafter. */
    public static int removeTraps(int level) {
        int clampedLevel = Math.max(1, level);
        return Math.min(95, 10 + 5 * (clampedLevel - 1));
    }
}
