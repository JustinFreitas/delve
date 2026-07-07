package dev.freitas.delve.game.engine;

import java.util.Map;

/**
 * Spells-per-day by class and level, returned as an array indexed by spell level minus one
 * (e.g. {@code [2, 1]} means two 1st-level and one 2nd-level slot). Every caster gets its own entry
 * here, keyed off {@link CharacterClass#tradition()} — the single source of truth for which classes
 * cast at all, replacing what used to be a hardcoded 2-tradition (Arcane/Divine) boolean split that
 * had no way to express a 3rd/4th tradition (Nature, Illusion).
 */
public final class SpellTables {

    private static final int[][] ARCANE = {
        {1},
        {2},
        {2, 1},
        {2, 2},
        {2, 2, 1},
        {2, 2, 2},
    };

    private static final int[][] DIVINE = {
        {},
        {1},
        {2},
        {2, 1},
        {2, 2},
        {2, 2, 1},
    };

    // gygax75-rules "Druid Spell Progression" -- shared as-is by Wood Elf ("wood elves have the same
    // spell selection as druids").
    private static final int[][] NATURE = {
        {1},
        {2},
        {2, 1},
        {2, 2},
        {2, 2, 1, 1},
        {2, 2, 2, 1, 1},
        {3, 3, 2, 2, 1},
        {3, 3, 3, 2, 2},
        {4, 4, 3, 3, 2},
        {4, 4, 4, 3, 3},
        {5, 5, 4, 4, 3},
        {5, 5, 5, 4, 4},
        {6, 5, 5, 5, 4},
        {6, 6, 5, 5, 5},
    };

    // gygax75-rules "Gnome Spell Progression".
    private static final int[][] ILLUSION = {
        {1},
        {2},
        {2, 1},
        {2, 2},
        {2, 2, 1},
        {2, 2, 2},
        {3, 2, 2, 1},
        {3, 3, 2, 2},
    };

    private static final Map<CharacterClass, int[][]> SLOT_TABLES = Map.of(
            CharacterClass.MAGIC_USER, ARCANE,
            CharacterClass.ELF, ARCANE,
            CharacterClass.CLERIC, DIVINE,
            CharacterClass.DRUID, NATURE,
            CharacterClass.WOOD_ELF, NATURE,
            CharacterClass.GNOME, ILLUSION);

    private SpellTables() {}

    public static boolean isCaster(CharacterClass characterClass) {
        return characterClass.tradition() != null;
    }

    public static Spell.Tradition tradition(CharacterClass characterClass) {
        return characterClass.tradition();
    }

    /** Slots available at the given class level (indexed by spell level - 1); empty if a non-caster. */
    public static int[] slots(CharacterClass characterClass, int level) {
        int[][] table = SLOT_TABLES.get(characterClass);
        if (table == null) {
            return new int[0];
        }
        int index = Math.max(1, Math.min(level, table.length)) - 1;
        return table[index];
    }

    /** Number of slots at a specific spell level. */
    public static int slotsAt(CharacterClass characterClass, int level, int spellLevel) {
        int[] slots = slots(characterClass, level);
        return spellLevel >= 1 && spellLevel <= slots.length ? slots[spellLevel - 1] : 0;
    }
}
