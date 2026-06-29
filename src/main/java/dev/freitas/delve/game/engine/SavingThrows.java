package dev.freitas.delve.game.engine;

import java.util.Map;

/**
 * B/X saving-throw target numbers (roll d20 >= target to save). This milestone provides the level-1
 * row for each class; the per-level progression is filled in alongside advancement (Milestone 7).
 */
public final class SavingThrows {

    /** The five B/X saving-throw categories. */
    public record Saves(int deathPoison, int wands, int paralysisPetrify, int breath, int spells) {}

    // Level-1 (lowest tier) saving throws per class.
    private static final Map<CharacterClass, Saves> LEVEL_1 = Map.of(
            CharacterClass.CLERIC, new Saves(11, 12, 14, 16, 15),
            CharacterClass.FIGHTER, new Saves(12, 13, 14, 15, 16),
            CharacterClass.MAGIC_USER, new Saves(13, 14, 13, 16, 15),
            CharacterClass.THIEF, new Saves(13, 14, 13, 16, 15),
            CharacterClass.DWARF, new Saves(8, 9, 10, 13, 12),
            CharacterClass.ELF, new Saves(12, 13, 13, 15, 15),
            CharacterClass.HALFLING, new Saves(8, 9, 10, 13, 12));

    private SavingThrows() {}

    public static Saves forCharacter(CharacterClass characterClass, int level) {
        // Only the level-1 row exists for now; higher levels reuse it until Milestone 7.
        return LEVEL_1.get(characterClass);
    }
}
