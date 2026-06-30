package dev.freitas.delve.game.engine;

/**
 * B/X spells-per-day by class and level, returned as an array indexed by spell level minus one
 * (e.g. {@code [2, 1]} means two 1st-level and one 2nd-level slot). Magic-users and elves use the
 * arcane progression; clerics the divine one (no spells at level 1); other classes never cast.
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

    private SpellTables() {}

    public static boolean isCaster(CharacterClass characterClass) {
        return characterClass == CharacterClass.MAGIC_USER
                || characterClass == CharacterClass.ELF
                || characterClass == CharacterClass.CLERIC;
    }

    public static Spell.Tradition tradition(CharacterClass characterClass) {
        return characterClass == CharacterClass.CLERIC ? Spell.Tradition.DIVINE : Spell.Tradition.ARCANE;
    }

    /** Slots available at the given class level (indexed by spell level - 1); empty if a non-caster. */
    public static int[] slots(CharacterClass characterClass, int level) {
        if (!isCaster(characterClass)) {
            return new int[0];
        }
        int[][] table = characterClass == CharacterClass.CLERIC ? DIVINE : ARCANE;
        int index = Math.max(1, Math.min(level, table.length)) - 1;
        return table[index];
    }

    /** Number of slots at a specific spell level. */
    public static int slotsAt(CharacterClass characterClass, int level, int spellLevel) {
        int[] slots = slots(characterClass, level);
        return spellLevel >= 1 && spellLevel <= slots.length ? slots[spellLevel - 1] : 0;
    }
}
