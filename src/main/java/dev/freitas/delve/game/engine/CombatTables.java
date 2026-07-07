package dev.freitas.delve.game.engine;

/**
 * B/X "attacks as" tables expressed as THAC0 (to-hit Armor Class 0). Classes share three combat
 * progressions; monsters step by Hit Dice.
 */
public final class CombatTables {

    private CombatTables() {}

    /**
     * Character THAC0 by class and level. Fighters, dwarves, elves and halflings use the fighter
     * progression; clerics and thieves a slower one; magic-users the slowest.
     */
    public static int classThac0(CharacterClass characterClass, int level) {
        return switch (characterClass) {
            case FIGHTER, DWARF, ELF, HALFLING, BARBARIAN, KNIGHT, WARDEN, WOOD_ELF -> fighterThac0(level);
            case CLERIC, THIEF, DRUID, HALF_ORC -> clericThiefThac0(level);
            case MAGIC_USER, GNOME -> magicUserThac0(level);
        };
    }

    private static int fighterThac0(int level) {
        if (level <= 3) return 19;
        if (level <= 6) return 17;
        if (level <= 9) return 14;
        if (level <= 12) return 12;
        if (level <= 15) return 10;
        return 8;
    }

    private static int clericThiefThac0(int level) {
        if (level <= 4) return 19;
        if (level <= 8) return 17;
        if (level <= 12) return 14;
        if (level <= 16) return 12;
        return 10;
    }

    private static int magicUserThac0(int level) {
        if (level <= 5) return 19;
        if (level <= 10) return 17;
        if (level <= 15) return 14;
        return 12;
    }

    /**
     * Monster THAC0 by Hit Dice. B/X groups attacks roughly as {@code 20 - effective HD}, where any
     * "+" bonus on the Hit Dice bumps the monster into the next band (e.g. 1+1 HD attacks as 2 HD).
     * Clamped to a sane floor for very high-HD creatures.
     */
    public static int monsterThac0(int hitDiceCount, int hitDiceBonus) {
        int effectiveHd = Math.max(1, hitDiceCount + (hitDiceBonus > 0 ? 1 : 0));
        return Math.max(5, 20 - effectiveHd);
    }
}
