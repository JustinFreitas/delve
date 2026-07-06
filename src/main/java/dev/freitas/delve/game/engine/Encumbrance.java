package dev.freitas.delve.game.engine;

/**
 * B/X movement rates using the simpler "encumbrance by armor and load" option: base speed depends on
 * the armor worn, dropping a step when hauling a heavy load of treasure. Encounter movement is a third
 * of the exploration rate.
 */
public final class Encumbrance {

    private static final int HEAVY_LOAD_GOLD = 800; // a sack of coin heavy enough to slow you down

    /** A hard carrying-capacity cap, from gygax75-rules' full coin-weight system ("Maximum load: 2400
        cns... cannot move" — 1 coin = 1 cn there, same unit {@link dev.freitas.delve.game.model.Mule}'s
        own capacity already uses). Scoped narrowly to gold specifically for now (recovering a fallen
        mule's cargo, see {@code CombatService}) rather than a full item-weight rewrite of this class —
        {@link #heavyLoad} above stays the only thing that gates ordinary movement/treasure pickup. */
    public static final int MAX_CARRY_GOLD = 2400;

    private Encumbrance() {}

    public static boolean heavyLoad(int gold) {
        return gold >= HEAVY_LOAD_GOLD;
    }

    /** How much more gold this PC could still carry before hitting the hard cap — may already be zero
        if they're carrying more than that (e.g. a pregen'd character's starting wealth), in which case
        they simply can't pick up any more right now. */
    public static int capacityRemaining(int gold) {
        return Math.max(0, MAX_CARRY_GOLD - gold);
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
