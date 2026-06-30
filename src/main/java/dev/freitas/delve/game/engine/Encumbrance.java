package dev.freitas.delve.game.engine;

/**
 * B/X movement rates using the simpler "encumbrance by armor and load" option: base speed depends on
 * the armor worn, dropping a step when hauling a heavy load of treasure. Encounter movement is a third
 * of the exploration rate.
 */
public final class Encumbrance {

    private static final int HEAVY_LOAD_GOLD = 800; // a sack of coin heavy enough to slow you down

    private Encumbrance() {}

    public static boolean heavyLoad(int gold) {
        return gold >= HEAVY_LOAD_GOLD;
    }

    /** Exploration movement rate in feet per turn. */
    public static int movementRate(Armor armor, boolean heavyLoad) {
        int base = switch (armor) {
            case NONE -> 120;
            case LEATHER -> 90;
            case CHAIN_MAIL -> 60;
            case PLATE_MAIL -> 30;
        };
        return heavyLoad ? Math.max(30, base - 30) : base;
    }

    /** Encounter (combat) movement rate — one third of the exploration rate. */
    public static int encounterRate(Armor armor, boolean heavyLoad) {
        return movementRate(armor, heavyLoad) / 3;
    }

    public static String descriptor(Armor armor, boolean heavyLoad) {
        int rate = movementRate(armor, heavyLoad);
        if (rate >= 120) return "unencumbered";
        if (rate >= 90) return "lightly burdened";
        if (rate >= 60) return "encumbered";
        return "heavily encumbered";
    }
}
