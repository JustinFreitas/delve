package dev.freitas.delve.game.engine;

/**
 * True coin-weight encumbrance (gygax75-rules): every coin, potion, and item weighs something in
 * "cns" (1 coin = 1 cn, 10 cn = 1 lb — see {@link GearCatalog}/{@link WeaponCatalog}/{@link Armor} for
 * per-item weights), and total carried weight alone picks a character's movement rate off one table —
 * not their armor tier the way the old model worked. A hard 2400-cn cap means carrying more than that
 * leaves them unable to move at all. Group movement (see {@code CombatService.groupEncounterRate}) is
 * the slowest member's rate, same B/X rule as before.
 *
 * <p>Callers compute a total weight via {@code Character.carriedWeightCns()}/
 * {@code Retainer.carriedWeightCns()} and pass it in here — this class only holds the universal rules
 * (bands, cap, flat per-unit weights for things with no catalog entry of their own), not any
 * character-specific summing.
 */
public final class Encumbrance {

    /** Hard carrying-capacity cap ("Characters carrying more than this cannot move"). */
    public static final int MAX_CARRY_CNS = 2400;

    /** Flat weights for carried things with no catalog entry of their own (a shield isn't a
        {@link GearCatalog} item — it's a boolean on the character — and light/potion supplies are
        tracked as dedicated counts rather than {@code inventory} strings). */
    public static final int SHIELD_WEIGHT_CNS = 100;
    public static final int TORCH_WEIGHT_CNS = 20;
    public static final int LANTERN_WEIGHT_CNS = 30;
    public static final int OIL_FLASK_WEIGHT_CNS = 10;
    public static final int POTION_WEIGHT_CNS = 10;
    public static final int COIN_WEIGHT_CNS = 1;

    private Encumbrance() {}

    /** Exploration movement rate in feet per turn for a given total carried weight: the four gygax75
        bands, 0 (cannot move) past the hard cap. */
    public static int movementRate(int totalWeightCns) {
        if (totalWeightCns <= 400) {
            return 120;
        }
        if (totalWeightCns <= 800) {
            return 90;
        }
        if (totalWeightCns <= 1200) {
            return 60;
        }
        if (totalWeightCns <= MAX_CARRY_CNS) {
            return 30;
        }
        return 0;
    }

    /** Encounter (combat) movement rate — one third of the exploration rate. */
    public static int encounterRate(int totalWeightCns) {
        return movementRate(totalWeightCns) / 3;
    }

    /** How much more weight could still be carried before hitting the hard cap — may already be zero
        (e.g. a heavily-geared character), in which case they can't take on any more right now. */
    public static int capacityRemaining(int totalWeightCns) {
        return Math.max(0, MAX_CARRY_CNS - totalWeightCns);
    }

    /** Whether this weight is past the hard cap — carrying this much, they simply cannot move. */
    public static boolean overloaded(int totalWeightCns) {
        return totalWeightCns > MAX_CARRY_CNS;
    }

    public static String descriptor(int totalWeightCns) {
        if (overloaded(totalWeightCns)) {
            return "overloaded (cannot move)";
        }
        int rate = movementRate(totalWeightCns);
        if (rate >= 120) {
            return "unencumbered";
        }
        if (rate >= 90) {
            return "lightly burdened";
        }
        if (rate >= 60) {
            return "encumbered";
        }
        return "heavily encumbered";
    }
}
