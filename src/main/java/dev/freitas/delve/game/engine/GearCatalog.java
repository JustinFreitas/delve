package dev.freitas.delve.game.engine;

import java.util.List;
import java.util.Locale;

/**
 * A gp price list for weapons, armor, a shield, and common adventuring gear — the "guided shopping"
 * counterpart to {@link CharacterFactory#create}'s free starting kit, used by real player character
 * creation ({@code /buy}) and reselling ({@code /sell}). Classic B/X-flavored prices; representative,
 * not gospel. Covers exactly the item names {@link WeaponCatalog}/{@code CharacterFactory}/
 * {@code RetainerFactory} already grant or recognize, plus the compound "weapon & ammo" bundle strings
 * those classes use (e.g. {@code "Sling & 30 stones"}) — matched case-insensitively by substring, same
 * style as {@link WeaponCatalog#classify}, most-specific needle first.
 */
public final class GearCatalog {

    private record Entry(String needle, int priceGp) {}

    private static final List<Entry> ENTRIES = List.of(
            // Compound "weapon & ammo" bundles, checked before their bare weapon name.
            new Entry("sling & 30 stones", 2),
            new Entry("short bow & 20 arrows", 30),
            new Entry("long bow & 20 arrows", 45),
            new Entry("light crossbow & 20 bolts", 35),
            new Entry("crossbow & 20 bolts", 35),
            // Weapons — "short sword"/"battle axe" before their shorter substrings ("sword"/"axe" isn't
            // a separate entry here, but "short bow" must still precede nothing since it has none).
            new Entry("short sword", 8),
            new Entry("battle axe", 7),
            new Entry("dagger", 3),
            new Entry("sword", 10),
            new Entry("mace", 5),
            new Entry("halberd", 7),
            new Entry("pike", 5),
            new Entry("lance", 4),
            new Entry("javelin", 1),
            new Entry("spear", 3),
            new Entry("sling", 2),
            new Entry("short bow", 25),
            new Entry("long bow", 40),
            new Entry("light crossbow", 30),
            new Entry("crossbow", 30),
            // Armor and shield.
            new Entry("plate mail", 400),
            new Entry("chain mail", 60),
            new Entry("leather", 20),
            new Entry("shield", 10),
            // Common adventuring gear.
            new Entry("backpack", 5),
            new Entry("tinderbox", 3),
            new Entry("rations", 15),
            new Entry("waterskin", 1),
            new Entry("rope", 1),
            new Entry("holy symbol", 25),
            new Entry("spellbook", 25),
            new Entry("thieves' tools", 25),
            new Entry("spike", 1));

    private GearCatalog() {}

    /** Price in gp for a recognized item name (case-insensitive substring match), or -1 if unrecognized
        — callers must handle "can't appraise that" themselves rather than guessing a value. */
    public static int priceGp(String itemName) {
        if (itemName == null) {
            return -1;
        }
        String lower = itemName.toLowerCase(Locale.ROOT);
        for (Entry entry : ENTRIES) {
            if (lower.contains(entry.needle())) {
                return entry.priceGp();
            }
        }
        return -1;
    }
}
