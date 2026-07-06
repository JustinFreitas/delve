package dev.freitas.delve.game.engine;

import java.util.List;
import java.util.Locale;

/**
 * A gp price + cn weight list for weapons, armor, a shield, and common adventuring gear — the "guided
 * shopping" counterpart to {@link CharacterFactory#create}'s free starting kit, used by real player
 * character creation ({@code /buy}), reselling ({@code /sell}), and encumbrance
 * ({@link Character#carriedWeightCns()}/{@link dev.freitas.delve.game.model.Retainer#carriedWeightCns()}).
 * Covers exactly the item names {@link WeaponCatalog}/{@code CharacterFactory}/{@code RetainerFactory}
 * already grant or recognize, plus the compound "weapon & ammo" bundle strings those classes use (e.g.
 * {@code "Sling & 30 stones"}) — matched case-insensitively by substring, same style as
 * {@link WeaponCatalog#classify}, most-specific needle first.
 *
 * <p>Prices and weights are gygax75-rules' own tables (1 coin = 1 cn), reconciled against this
 * project's earlier, differently-sourced prices where a clean 1:1 name match exists (dagger 3->4gp,
 * lance 4->5gp, short bow 25->7gp, chain mail 60->40gp, plate mail 400->60gp, rations 15->1gp). A few
 * delve-only names (short sword, halberd, pike) and the compound ammo bundles have no gygax75 entry, so
 * their prices are left as delve's own pre-existing values — representative, not gospel, same as
 * before. Spellbook's gygax75 price (0gp) looks like a source-document quirk (read there as DM-awarded,
 * not shop-bought) rather than a real intended price, so it's deliberately not applied.
 */
public final class GearCatalog {

    private record Entry(String needle, int priceGp, int weightCns) {}

    private static final List<Entry> ENTRIES = List.of(
            // Compound "weapon & ammo" bundles, checked before their bare weapon name. gygax75 has no
            // equivalent bundle pricing; weight is the weapon's own weight plus a standard ammo load.
            new Entry("sling & 30 stones", 2, 30),
            new Entry("short bow & 20 arrows", 30, 40),
            new Entry("long bow & 20 arrows", 45, 40),
            new Entry("light crossbow & 20 bolts", 35, 60),
            new Entry("crossbow & 20 bolts", 35, 60),
            // Rations: the free-kit's "Rations (1 week)" bundle gets its own entry (7x the per-unit
            // price/weight) ahead of the generic "rations" needle below, which is what /buy's per-unit
            // purchases match.
            new Entry("rations (1 week)", 7, 210),
            // Weapons — "short sword"/"battle axe" before their shorter substrings ("sword"/"axe" isn't
            // a separate entry here, but "short bow" must still precede nothing since it has none).
            new Entry("short sword", 8, 40),
            new Entry("battle axe", 7, 30),
            new Entry("dagger", 4, 10),
            new Entry("sword", 10, 60),
            new Entry("mace", 5, 30),
            new Entry("halberd", 7, 150),
            new Entry("pike", 5, 150),
            new Entry("lance", 5, 120),
            new Entry("javelin", 1, 20),
            new Entry("spear", 3, 30),
            new Entry("sling", 2, 20),
            new Entry("short bow", 7, 30),
            new Entry("long bow", 40, 30),
            new Entry("light crossbow", 30, 50),
            new Entry("crossbow", 30, 50),
            // Armor and shield.
            new Entry("plate mail", 60, 500),
            new Entry("chain mail", 40, 400),
            new Entry("leather", 20, 200),
            new Entry("shield", 10, 100),
            // Common adventuring gear.
            new Entry("backpack", 5, 20),
            new Entry("small sack", 1, 1),
            new Entry("large sack", 2, 5),
            new Entry("tinderbox", 3, 5),
            new Entry("rations", 1, 30),
            new Entry("waterskin", 1, 35),
            new Entry("rope", 1, 50),
            new Entry("holy symbol", 25, 1),
            new Entry("spellbook", 25, 300),
            new Entry("thieves' tools", 25, 10),
            new Entry("spike", 1, 5));

    private GearCatalog() {}

    /** Price in gp for a recognized item name (case-insensitive substring match), or -1 if unrecognized
        — callers must handle "can't appraise that" themselves rather than guessing a value. */
    public static int priceGp(String itemName) {
        Entry entry = find(itemName);
        return entry == null ? -1 : entry.priceGp();
    }

    /** Weight in cns for a recognized item name (case-insensitive substring match), or 0 if unrecognized
        — an item that doesn't exist can't weigh anything, so callers can sum this freely without
        special-casing unmatched names. */
    public static int weightCns(String itemName) {
        Entry entry = find(itemName);
        return entry == null ? 0 : entry.weightCns();
    }

    private static Entry find(String itemName) {
        if (itemName == null) {
            return null;
        }
        String lower = itemName.toLowerCase(Locale.ROOT);
        for (Entry entry : ENTRIES) {
            if (lower.contains(entry.needle())) {
                return entry;
            }
        }
        return null;
    }
}
