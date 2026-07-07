package dev.freitas.delve.game.engine;

import java.util.List;
import java.util.Map;

/**
 * B/X saving-throw target numbers (roll d20 >= target to save), as level-banded tables per class.
 * Demihuman classes (Dwarf, Halfling) share a table, as do thieves with the cleric-style die for
 * some categories — the values below are transcribed per class from the B/X tables.
 */
public final class SavingThrows {

    /** The five B/X saving-throw categories. */
    public record Saves(int deathPoison, int wands, int paralysisPetrify, int breath, int spells) {}

    /** A saving-throw row that applies up to and including {@code maxLevel}. */
    private record Band(int maxLevel, Saves saves) {}

    private static final Map<CharacterClass, List<Band>> TABLES = Map.ofEntries(
            Map.entry(CharacterClass.CLERIC, List.of(
                    new Band(4, new Saves(11, 12, 14, 16, 15)),
                    new Band(8, new Saves(9, 10, 12, 14, 12)),
                    new Band(12, new Saves(6, 7, 9, 11, 9)),
                    new Band(Integer.MAX_VALUE, new Saves(3, 5, 7, 8, 7)))),
            Map.entry(CharacterClass.FIGHTER, List.of(
                    new Band(3, new Saves(12, 13, 14, 15, 16)),
                    new Band(6, new Saves(10, 11, 12, 13, 14)),
                    new Band(9, new Saves(8, 9, 10, 10, 12)),
                    new Band(12, new Saves(6, 7, 8, 8, 10)),
                    new Band(Integer.MAX_VALUE, new Saves(4, 5, 6, 5, 8)))),
            Map.entry(CharacterClass.MAGIC_USER, List.of(
                    new Band(5, new Saves(13, 14, 13, 16, 15)),
                    new Band(10, new Saves(11, 12, 11, 14, 12)),
                    new Band(Integer.MAX_VALUE, new Saves(8, 9, 8, 11, 8)))),
            Map.entry(CharacterClass.THIEF, List.of(
                    new Band(4, new Saves(13, 14, 13, 16, 15)),
                    new Band(8, new Saves(12, 13, 11, 14, 13)),
                    new Band(12, new Saves(10, 11, 9, 12, 10)),
                    new Band(Integer.MAX_VALUE, new Saves(8, 9, 7, 10, 8)))),
            Map.entry(CharacterClass.DWARF, demihumanBands()),
            Map.entry(CharacterClass.HALFLING, demihumanBands()),
            Map.entry(CharacterClass.ELF, List.of(
                    new Band(3, new Saves(12, 13, 13, 15, 15)),
                    new Band(6, new Saves(10, 11, 11, 13, 12)),
                    new Band(9, new Saves(8, 9, 9, 10, 10)),
                    new Band(Integer.MAX_VALUE, new Saves(6, 7, 7, 8, 8)))),
            // gygax75-rules custom classes.
            Map.entry(CharacterClass.BARBARIAN, List.of(
                    new Band(3, new Saves(10, 13, 12, 15, 16)),
                    new Band(6, new Saves(8, 11, 10, 13, 13)),
                    new Band(9, new Saves(6, 9, 8, 10, 10)),
                    new Band(12, new Saves(4, 7, 6, 8, 7)),
                    new Band(Integer.MAX_VALUE, new Saves(3, 5, 4, 5, 5)))),
            Map.entry(CharacterClass.DRUID, List.of(
                    new Band(4, new Saves(11, 12, 14, 16, 15)),
                    new Band(8, new Saves(9, 10, 12, 14, 12)),
                    new Band(12, new Saves(6, 7, 9, 11, 9)),
                    new Band(Integer.MAX_VALUE, new Saves(3, 5, 7, 8, 7)))),
            Map.entry(CharacterClass.KNIGHT, List.of(
                    new Band(3, new Saves(12, 13, 14, 15, 16)),
                    new Band(6, new Saves(10, 11, 12, 13, 14)),
                    new Band(9, new Saves(8, 9, 10, 8, 10)),
                    new Band(12, new Saves(6, 7, 8, 8, 10)),
                    new Band(Integer.MAX_VALUE, new Saves(4, 5, 6, 5, 8)))),
            Map.entry(CharacterClass.WARDEN, List.of(
                    new Band(3, new Saves(12, 13, 14, 15, 16)),
                    new Band(6, new Saves(10, 11, 12, 13, 14)),
                    new Band(9, new Saves(8, 9, 10, 10, 12)),
                    new Band(12, new Saves(6, 7, 8, 8, 10)),
                    new Band(Integer.MAX_VALUE, new Saves(4, 5, 6, 5, 8)))),
            Map.entry(CharacterClass.GNOME, List.of(
                    new Band(5, new Saves(8, 9, 10, 14, 11)),
                    new Band(Integer.MAX_VALUE, new Saves(6, 7, 8, 11, 9)))),
            Map.entry(CharacterClass.HALF_ORC, List.of(
                    new Band(4, new Saves(13, 14, 13, 16, 15)),
                    new Band(Integer.MAX_VALUE, new Saves(12, 13, 11, 14, 13)))),
            Map.entry(CharacterClass.WOOD_ELF, List.of(
                    new Band(3, new Saves(12, 13, 13, 15, 15)),
                    new Band(6, new Saves(10, 11, 11, 13, 12)),
                    new Band(9, new Saves(8, 9, 9, 10, 10)),
                    new Band(Integer.MAX_VALUE, new Saves(6, 7, 8, 8, 8)))));

    private static List<Band> demihumanBands() {
        return List.of(
                new Band(3, new Saves(8, 9, 10, 13, 12)),
                new Band(6, new Saves(6, 7, 8, 10, 10)),
                new Band(9, new Saves(4, 5, 6, 7, 8)),
                new Band(Integer.MAX_VALUE, new Saves(2, 3, 4, 4, 6)));
    }

    private SavingThrows() {}

    public static Saves forCharacter(CharacterClass characterClass, int level) {
        for (Band band : TABLES.get(characterClass)) {
            if (level <= band.maxLevel()) {
                return band.saves();
            }
        }
        throw new IllegalStateException("No saving-throw band for " + characterClass + " level " + level);
    }
}
