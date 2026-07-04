package dev.freitas.delve.game.engine;

/**
 * The two ways a party can light its way, with the classic B/X cost/duration trade-off: a torch is
 * cheap and disposable but short-lived; a lantern costs more up front (and needs oil flasks as fuel)
 * but each fuel unit burns four times as long.
 */
public enum LightSource {
    TORCH(1, 6, 30, "torch"),
    LANTERN(10, 24, 30, "lantern");

    /** Cost of a flask of oil (a lantern's fuel) — separate from the lantern item itself, which is
        bought once and reused. */
    public static final int OIL_FLASK_COST_GP = 2;

    private final int itemCostGp;
    private final int turnsPerUse;
    private final int radiusFeet;
    private final String displayName;

    LightSource(int itemCostGp, int turnsPerUse, int radiusFeet, String displayName) {
        this.itemCostGp = itemCostGp;
        this.turnsPerUse = turnsPerUse;
        this.radiusFeet = radiusFeet;
        this.displayName = displayName;
    }

    /** Cost in gp of one torch, or of the lantern item itself (a one-time purchase, unlike its fuel). */
    public int itemCostGp() {
        return itemCostGp;
    }

    /** Dungeon turns of light from one torch, or from one flask of oil in a lantern. */
    public int turnsPerUse() {
        return turnsPerUse;
    }

    public int radiusFeet() {
        return radiusFeet;
    }

    public String displayName() {
        return displayName;
    }
}
