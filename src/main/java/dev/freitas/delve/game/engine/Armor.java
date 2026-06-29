package dev.freitas.delve.game.engine;

/**
 * Body armor and its B/X (descending) base Armor Class. Lower AC is better. A shield improves AC by
 * a further 1 and is tracked separately on the character.
 */
public enum Armor {
    NONE("Unarmored", 9),
    LEATHER("Leather armor", 7),
    CHAIN_MAIL("Chain mail", 5),
    PLATE_MAIL("Plate mail", 3);

    private final String displayName;
    private final int baseArmorClass;

    Armor(String displayName, int baseArmorClass) {
        this.displayName = displayName;
        this.baseArmorClass = baseArmorClass;
    }

    public String displayName() {
        return displayName;
    }

    public int baseArmorClass() {
        return baseArmorClass;
    }
}
