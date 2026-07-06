package dev.freitas.delve.game.engine;

import java.util.List;
import java.util.Locale;

/**
 * Classifies a weapon by name (case-insensitive substring match against {@code mainWeapon}, since this
 * codebase has no structured Equipment/Item type — weapons are plain display strings). Proposed default
 * table; range increments and the reach/melee keyword lists are adjustable.
 */
public final class WeaponCatalog {

    /** A weapon's combat classification, plus its range table if it's a missile weapon. */
    public record WeaponProfile(WeaponClass weaponClass, MissileRangeTable rangeTable) {
        WeaponProfile(WeaponClass weaponClass) {
            this(weaponClass, null);
        }
    }

    private record Entry(String needle, WeaponClass weaponClass, MissileRangeTable rangeTable) {}

    // Checked in order; more specific needles first (e.g. "light crossbow" before the generic "crossbow").
    private static final List<Entry> ENTRIES = List.of(
            new Entry("short bow", WeaponClass.MISSILE, new MissileRangeTable(50, 100, 150)),
            new Entry("long bow", WeaponClass.MISSILE, new MissileRangeTable(70, 140, 210)),
            new Entry("sling", WeaponClass.MISSILE, new MissileRangeTable(40, 80, 160)),
            new Entry("light crossbow", WeaponClass.MISSILE, new MissileRangeTable(60, 120, 180)),
            new Entry("crossbow", WeaponClass.MISSILE, new MissileRangeTable(80, 160, 240)),
            new Entry("javelin", WeaponClass.MISSILE, new MissileRangeTable(30, 60, 90)),
            new Entry("spear", WeaponClass.REACH, null),
            new Entry("polearm", WeaponClass.REACH, null),
            new Entry("halberd", WeaponClass.REACH, null),
            new Entry("pike", WeaponClass.REACH, null),
            new Entry("lance", WeaponClass.REACH, null));

    private static final WeaponProfile DEFAULT_MELEE = new WeaponProfile(WeaponClass.MELEE);

    // Needs both hands to use at all (draw a bow, brace a polearm); unmatched weapons default to 1.
    private static final List<String> TWO_HANDED_NEEDLES = List.of(
            "short bow", "long bow", "crossbow", "polearm", "halberd", "pike", "lance");

    private record DamageEntry(String needle, DamageRoll damage) {}

    // Also checked most-specific-first; unmatched weapons default to 1d6.
    private static final List<DamageEntry> DAMAGE_ENTRIES = List.of(
            new DamageEntry("dagger", new DamageRoll(1, 4)),
            new DamageEntry("sling", new DamageRoll(1, 4)),
            new DamageEntry("halberd", new DamageRoll(1, 10)),
            new DamageEntry("battle axe", new DamageRoll(1, 8)),
            new DamageEntry("sword", new DamageRoll(1, 8)));
    private static final DamageRoll DEFAULT_DAMAGE = new DamageRoll(1, 6);

    private record WeightEntry(String needle, int weightCns) {}

    // Weight in cns (gygax75-rules, 1 coin = 1 cn), most-specific-first. Covers every weapon name
    // CharacterFactory/RetainerFactory grant or GearCatalog/{@code /buy} recognize -- a superset of
    // WeaponCatalog's own classify/damage needles, since plain melee weapons (sword, mace, dagger...)
    // need no special classification but still need a weight. Halberd/pike have no gygax75 entry;
    // mapped to Polearm's weight since WeaponCatalog already classifies them the same way (REACH).
    private static final List<WeightEntry> WEIGHT_ENTRIES = List.of(
            new WeightEntry("short sword", 40),
            new WeightEntry("battle axe", 30),
            new WeightEntry("dagger", 10),
            new WeightEntry("sword", 60),
            new WeightEntry("mace", 30),
            new WeightEntry("halberd", 150),
            new WeightEntry("pike", 150),
            new WeightEntry("polearm", 150),
            new WeightEntry("lance", 120),
            new WeightEntry("javelin", 20),
            new WeightEntry("spear", 30),
            new WeightEntry("sling", 20),
            new WeightEntry("short bow", 30),
            new WeightEntry("long bow", 30),
            new WeightEntry("light crossbow", 50),
            new WeightEntry("crossbow", 50));

    // A real weapon always weighs something -- unmatched names fall back to a representative average
    // rather than 0, same spirit as DEFAULT_DAMAGE's 1d6 fallback.
    private static final int DEFAULT_WEIGHT_CNS = 30;

    private WeaponCatalog() {}

    /** Classifies a weapon name; unrecognized/blank names default to {@code MELEE}. */
    public static WeaponProfile classify(String weaponName) {
        if (weaponName == null) {
            return DEFAULT_MELEE;
        }
        String lower = weaponName.toLowerCase(Locale.ROOT);
        for (Entry entry : ENTRIES) {
            if (lower.contains(entry.needle())) {
                return new WeaponProfile(entry.weaponClass(), entry.rangeTable());
            }
        }
        return DEFAULT_MELEE;
    }

    /** Hands needed to use a weapon (1 or 2) — spears/slings/javelins/daggers/swords/maces/axes are
        one-handed; bows, crossbows, and polearm-family weapons need both hands. */
    public static int handsRequired(String weaponName) {
        if (weaponName == null) {
            return 1;
        }
        String lower = weaponName.toLowerCase(Locale.ROOT);
        for (String needle : TWO_HANDED_NEEDLES) {
            if (lower.contains(needle)) {
                return 2;
            }
        }
        return 1;
    }

    /** Damage roll for a recognized weapon name (used by {@code /wield}); unmatched names default to 1d6. */
    public static DamageRoll damageFor(String weaponName) {
        if (weaponName != null) {
            String lower = weaponName.toLowerCase(Locale.ROOT);
            for (DamageEntry entry : DAMAGE_ENTRIES) {
                if (lower.contains(entry.needle())) {
                    return entry.damage();
                }
            }
        }
        return DEFAULT_DAMAGE;
    }

    /** Weight in cns for a recognized weapon name (used by {@code Character}/{@code Retainer}'s
        encumbrance); unmatched names default to {@link #DEFAULT_WEIGHT_CNS}. */
    public static int weightCns(String weaponName) {
        if (weaponName != null) {
            String lower = weaponName.toLowerCase(Locale.ROOT);
            for (WeightEntry entry : WEIGHT_ENTRIES) {
                if (lower.contains(entry.needle())) {
                    return entry.weightCns();
                }
            }
        }
        return DEFAULT_WEIGHT_CNS;
    }
}
