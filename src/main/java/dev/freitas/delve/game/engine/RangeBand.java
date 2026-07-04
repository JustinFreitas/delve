package dev.freitas.delve.game.engine;

/** Standard B/X/OSE missile to-hit modifiers by range band. */
public enum RangeBand {
    MELEE(0),
    SHORT(1),
    MEDIUM(0),
    LONG(-1);

    private final int toHitModifier;

    RangeBand(int toHitModifier) {
        this.toHitModifier = toHitModifier;
    }

    public int toHitModifier() {
        return toHitModifier;
    }
}
