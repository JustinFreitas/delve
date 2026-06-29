package dev.freitas.delve.game.engine;

import java.util.EnumMap;
import java.util.Map;

/**
 * A character's six ability scores plus the B/X ability-modifier lookup. Serialized to JSON by full
 * ability name (e.g. {@code "STRENGTH"}), so it round-trips cleanly through the save blob.
 */
public class AbilityScores {

    private int strength;
    private int intelligence;
    private int wisdom;
    private int dexterity;
    private int constitution;
    private int charisma;

    /** No-arg constructor for Jackson. */
    public AbilityScores() {}

    public AbilityScores(int strength, int intelligence, int wisdom, int dexterity, int constitution, int charisma) {
        this.strength = strength;
        this.intelligence = intelligence;
        this.wisdom = wisdom;
        this.dexterity = dexterity;
        this.constitution = constitution;
        this.charisma = charisma;
    }

    /** Rolls a fresh set of 3d6-in-order scores. */
    public static AbilityScores roll(Dice dice) {
        return new AbilityScores(
                dice.roll3d6(), dice.roll3d6(), dice.roll3d6(), dice.roll3d6(), dice.roll3d6(), dice.roll3d6());
    }

    /**
     * The B/X ability-score modifier table (applies uniformly to all six abilities).
     *
     * <pre>
     *  3      -3
     *  4-5    -2
     *  6-8    -1
     *  9-12    0
     *  13-15  +1
     *  16-17  +2
     *  18     +3
     * </pre>
     */
    public static int modifier(int score) {
        if (score <= 3) {
            return -3;
        } else if (score <= 5) {
            return -2;
        } else if (score <= 8) {
            return -1;
        } else if (score <= 12) {
            return 0;
        } else if (score <= 15) {
            return 1;
        } else if (score <= 17) {
            return 2;
        } else {
            return 3;
        }
    }

    public int score(Ability ability) {
        return switch (ability) {
            case STR -> strength;
            case INT -> intelligence;
            case WIS -> wisdom;
            case DEX -> dexterity;
            case CON -> constitution;
            case CHA -> charisma;
        };
    }

    public int modifier(Ability ability) {
        return modifier(score(ability));
    }

    /** All scores as an ordered map, convenient for rendering the character sheet. */
    public Map<Ability, Integer> asMap() {
        Map<Ability, Integer> map = new EnumMap<>(Ability.class);
        for (Ability a : Ability.values()) {
            map.put(a, score(a));
        }
        return map;
    }

    public int getStrength() {
        return strength;
    }

    public void setStrength(int strength) {
        this.strength = strength;
    }

    public int getIntelligence() {
        return intelligence;
    }

    public void setIntelligence(int intelligence) {
        this.intelligence = intelligence;
    }

    public int getWisdom() {
        return wisdom;
    }

    public void setWisdom(int wisdom) {
        this.wisdom = wisdom;
    }

    public int getDexterity() {
        return dexterity;
    }

    public void setDexterity(int dexterity) {
        this.dexterity = dexterity;
    }

    public int getConstitution() {
        return constitution;
    }

    public void setConstitution(int constitution) {
        this.constitution = constitution;
    }

    public int getCharisma() {
        return charisma;
    }

    public void setCharisma(int charisma) {
        this.charisma = charisma;
    }
}
