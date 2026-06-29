package dev.freitas.delve.game.engine;

/**
 * A dice-based damage (or quantity) expression such as {@code 1d6} or {@code 2d4+1}. Used for both
 * weapon and monster damage. A successful hit always deals at least 1 point of damage in B/X, so
 * {@link #roll(Dice)} floors at 1.
 */
public record DamageRoll(int count, int sides, int modifier) {

    public DamageRoll(int count, int sides) {
        this(count, sides, 0);
    }

    public int roll(Dice dice) {
        return Math.max(1, dice.roll(count, sides, modifier));
    }

    /** Average result (for sorting/encounter balancing), also floored at 1. */
    public double average() {
        return Math.max(1, count * (sides + 1) / 2.0 + modifier);
    }

    @Override
    public String toString() {
        String mod = modifier == 0 ? "" : (modifier > 0 ? "+" + modifier : String.valueOf(modifier));
        return count + "d" + sides + mod;
    }
}
