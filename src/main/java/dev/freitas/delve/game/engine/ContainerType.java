package dev.freitas.delve.game.engine;

/** A carried container's capacity and own weight in cns (gygax75-rules — matches {@link GearCatalog}'s
    "backpack"/"small sack"/"large sack" prices), and whether it can be worn (no hands) rather than held
    (costs a hand — see {@link Hands}). A backpack is always worn; a small sack may be worn (at most
    one) or held; a large sack is always held. Own weight lives directly on the enum (not looked up via
    {@link GearCatalog} by name) — same precedent as {@link Armor#weightCns()}. */
public enum ContainerType {
    BACKPACK("Backpack", 400, 20, true),
    SMALL_SACK("Small sack", 200, 1, true),
    LARGE_SACK("Large sack", 600, 5, false);

    private final String displayName;
    private final int capacityCns;
    private final int weightCns;
    private final boolean canBeWorn;

    ContainerType(String displayName, int capacityCns, int weightCns, boolean canBeWorn) {
        this.displayName = displayName;
        this.capacityCns = capacityCns;
        this.weightCns = weightCns;
        this.canBeWorn = canBeWorn;
    }

    public String displayName() {
        return displayName;
    }

    public int capacityCns() {
        return capacityCns;
    }

    /** The container's own weight, empty — separate from {@link #capacityCns()}, how much it can hold. */
    public int weightCns() {
        return weightCns;
    }

    public boolean canBeWorn() {
        return canBeWorn;
    }
}
