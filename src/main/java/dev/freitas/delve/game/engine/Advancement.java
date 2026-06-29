package dev.freitas.delve.game.engine;

import java.util.Map;

/**
 * B/X experience-point requirements and level lookups. Each array is indexed by {@code level - 1};
 * the array length is the class's maximum level (demihumans cap below 14). Prime-requisite XP bonuses
 * are applied when XP is awarded via {@link #adjustedAward}.
 */
public final class Advancement {

    // XP required to *be* a given level (cumulative). Index 0 = level 1 (0 XP).
    private static final Map<CharacterClass, int[]> XP = Map.of(
            CharacterClass.CLERIC, new int[] {
                0, 1500, 3000, 6000, 12000, 25000, 50000, 100000, 200000, 300000, 400000, 500000, 600000, 700000},
            CharacterClass.FIGHTER, new int[] {
                0, 2000, 4000, 8000, 16000, 32000, 64000, 120000, 240000, 360000, 480000, 600000, 720000, 840000},
            CharacterClass.MAGIC_USER, new int[] {
                0, 2500, 5000, 10000, 20000, 40000, 80000, 150000, 300000, 450000, 600000, 750000, 900000, 1050000},
            CharacterClass.THIEF, new int[] {
                0, 1200, 2400, 4800, 9600, 20000, 40000, 80000, 160000, 280000, 400000, 520000, 640000, 760000},
            CharacterClass.DWARF, new int[] {
                0, 2200, 4400, 8800, 17000, 35000, 70000, 140000, 270000, 400000, 530000, 660000},
            CharacterClass.ELF, new int[] {
                0, 4000, 8000, 16000, 32000, 64000, 120000, 250000, 400000, 600000},
            CharacterClass.HALFLING, new int[] {
                0, 2000, 4000, 8000, 16000, 32000, 64000, 120000});

    private Advancement() {}

    public static int maxLevel(CharacterClass characterClass) {
        return XP.get(characterClass).length;
    }

    /** Cumulative XP required to be the given level (clamped to the class's level range). */
    public static int xpForLevel(CharacterClass characterClass, int level) {
        int[] table = XP.get(characterClass);
        int index = Math.max(1, Math.min(level, table.length)) - 1;
        return table[index];
    }

    /** Highest level the given XP total qualifies for (capped at the class maximum). */
    public static int levelForXp(CharacterClass characterClass, int xp) {
        int[] table = XP.get(characterClass);
        int level = 1;
        for (int i = 0; i < table.length; i++) {
            if (xp >= table[i]) {
                level = i + 1;
            } else {
                break;
            }
        }
        return level;
    }

    /** Applies a prime-requisite XP bonus/penalty percentage to an XP award. */
    public static int adjustedAward(int award, int bonusPercent) {
        return (int) Math.floor(award * (100 + bonusPercent) / 100.0);
    }
}
