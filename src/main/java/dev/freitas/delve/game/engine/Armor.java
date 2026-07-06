package dev.freitas.delve.game.engine;

/**
 * Body armor and its B/X (descending) base Armor Class. Lower AC is better. A shield improves AC by
 * a further 1 and is tracked separately on the character. Weight in cns is gygax75-rules' own armor
 * table (1 coin = 1 cn) — feeds {@code Character}/{@code Retainer}'s encumbrance.
 */
public enum Armor {
    NONE("Unarmored", 9, 0),
    LEATHER("Leather armor", 7, 200),
    CHAIN_MAIL("Chain mail", 5, 400),
    PLATE_MAIL("Plate mail", 3, 500);

    private final String displayName;
    private final int baseArmorClass;
    private final int weightCns;

    Armor(String displayName, int baseArmorClass, int weightCns) {
        this.displayName = displayName;
        this.baseArmorClass = baseArmorClass;
        this.weightCns = weightCns;
    }

    public String displayName() {
        return displayName;
    }

    public int baseArmorClass() {
        return baseArmorClass;
    }

    public int weightCns() {
        return weightCns;
    }
}
