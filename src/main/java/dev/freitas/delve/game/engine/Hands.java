package dev.freitas.delve.game.engine;

/**
 * Every combatant has two hands. A wielded weapon costs 1 or 2 (see {@link WeaponCatalog#handsRequired}),
 * a shield costs 1, and physically holding the party's lit torch/lantern costs 1 more — the budget this
 * class enforces is that these can never add up to more than 2 for one combatant.
 */
public final class Hands {

    private static final int TOTAL = 2;

    private Hands() {}

    public static int used(String weaponName, boolean shield, boolean holdingLight) {
        return WeaponCatalog.handsRequired(weaponName) + (shield ? 1 : 0) + (holdingLight ? 1 : 0);
    }

    public static int free(String weaponName, boolean shield, boolean holdingLight) {
        return Math.max(0, TOTAL - used(weaponName, shield, holdingLight));
    }

    public static boolean fits(String weaponName, boolean shield, boolean holdingLight) {
        return used(weaponName, shield, holdingLight) <= TOTAL;
    }
}
