package dev.freitas.delve.game.engine;

/**
 * B/X pack-animal rules for the party's mule. Economy figures are from gygax75-rules: 30gp to buy,
 * carries up to 2000 cns at 120'/turn or up to 4000 cns at 60'/turn (1 coin = 1 cn, so this is expressed
 * directly in gold — see {@link dev.freitas.delve.game.model.Mule#getCarriedGold()}), and roughly
 * 5sp/day to stable and feed. {@code UPKEEP_GP_PER_WEEK} is a flat weekly abstraction of that daily
 * figure, the same rounding liberty {@code TownService.RETAINER_UPKEEP} already takes rather than
 * simulating per-location prices. Combat stats are the OSE Referee's Tome bestiary entry: AC 7 [12],
 * HD 2 (avg. 9hp), Att 1 kick (1d4), THAC0 18 [+1], MV 120' (40') — matching the unencumbered movement
 * rate above, since an unloaded mule is exactly as fast as the bestiary says.
 */
public final class MuleRules {

    public static final int PURCHASE_COST_GP = 30;
    public static final int CAPACITY_LIGHT_CNS = 2000;
    public static final int CAPACITY_MAX_CNS = 4000;
    public static final int UPKEEP_GP_PER_WEEK = 4;

    public static final int ARMOR_CLASS = 7;
    public static final int THAC0 = 18;
    public static final int HIT_DICE_COUNT = 2;
    public static final int HIT_DIE_SIDES = 8;
    public static final DamageRoll KICK_DAMAGE = new DamageRoll(1, 4);

    private MuleRules() {}

    /** Exploration movement rate in feet per turn for the given load: 120' up to the light threshold,
        60' up to the max, capped there — a mule can't be loaded any heavier. */
    public static int movementRate(int carriedGold) {
        if (carriedGold <= CAPACITY_LIGHT_CNS) {
            return 120;
        }
        return 60;
    }

    /** Encounter (combat) movement rate — one third of the exploration rate, same convention as
        {@link Encumbrance#encounterRate}. */
    public static int encounterRate(int carriedGold) {
        return movementRate(carriedGold) / 3;
    }

    /** How much more gold the mule can still carry before hitting its hard cap. */
    public static int capacityRemaining(int carriedGold) {
        return Math.max(0, CAPACITY_MAX_CNS - carriedGold);
    }

    public static String descriptor(int carriedGold) {
        return carriedGold <= CAPACITY_LIGHT_CNS ? "lightly loaded" : "heavily loaded";
    }
}
