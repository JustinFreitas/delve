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

    /** As the 3-arg version, but with a one-handed off-hand weapon (two-weapon fighting) costing one
        more hand. */
    public static int used(String weaponName, boolean shield, boolean holdingLight, boolean offHand) {
        return used(weaponName, shield, holdingLight) + (offHand ? 1 : 0);
    }

    public static int free(String weaponName, boolean shield, boolean holdingLight, boolean offHand) {
        return Math.max(0, TOTAL - used(weaponName, shield, holdingLight, offHand));
    }

    public static boolean fits(String weaponName, boolean shield, boolean holdingLight, boolean offHand) {
        return used(weaponName, shield, holdingLight, offHand) <= TOTAL;
    }

    /** As the 3-arg version, but with {@code heldSacks} — the count of currently-held (not worn)
        {@link dev.freitas.delve.game.model.Container}s, each costing one more hand, same as a shield or
        the party's light. A worn backpack or worn small sack costs no hand and isn't counted here. For
        retainers, which have no off-hand-weapon concept; see the 5-arg version for PCs. */
    public static int used(String weaponName, boolean shield, boolean holdingLight, int heldSacks) {
        return used(weaponName, shield, holdingLight) + heldSacks;
    }

    public static int free(String weaponName, boolean shield, boolean holdingLight, int heldSacks) {
        return Math.max(0, TOTAL - used(weaponName, shield, holdingLight, heldSacks));
    }

    public static boolean fits(String weaponName, boolean shield, boolean holdingLight, int heldSacks) {
        return used(weaponName, shield, holdingLight, heldSacks) <= TOTAL;
    }

    /** As the 4-arg off-hand version, but with {@code heldSacks} too — the full PC budget: weapon,
        shield, light, off-hand weapon, and held (not worn) containers. */
    public static int used(String weaponName, boolean shield, boolean holdingLight, boolean offHand, int heldSacks) {
        return used(weaponName, shield, holdingLight, offHand) + heldSacks;
    }

    public static int free(String weaponName, boolean shield, boolean holdingLight, boolean offHand, int heldSacks) {
        return Math.max(0, TOTAL - used(weaponName, shield, holdingLight, offHand, heldSacks));
    }

    public static boolean fits(String weaponName, boolean shield, boolean holdingLight, boolean offHand, int heldSacks) {
        return used(weaponName, shield, holdingLight, offHand, heldSacks) <= TOTAL;
    }
}
