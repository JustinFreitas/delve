package dev.freitas.delve.game.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * A modest B/X magic-item table for outfitting pregenerated characters. The goal is to make a
 * level-N pregen feel like it has "been around" — not to hand out artifacts. Items are returned as
 * display strings (and potions of healing are surfaced separately so the caller can stock the
 * character's quaffable supply). Generosity scales with level and is easy to retune here.
 */
public final class MagicItemTable {

    /** Result of rolling a pregen's magic loot: descriptive items plus a count of healing potions. */
    public record Loot(List<String> items, int healingPotions) {}

    private static final String[] GENERAL = {
        "Potion of healing",
        "Potion of healing",
        "Scroll of protection from undead",
        "Ring of protection +1",
        "Elven cloak",
        "Elven boots",
    };

    private MagicItemTable() {}

    /** Roughly one roll per three levels above 1st, biased toward a signature item for martial types. */
    public static Loot roll(CharacterClass characterClass, int level, Dice dice) {
        List<String> items = new ArrayList<>();
        int potions = 0;

        int rolls = Math.max(0, (level - 1) / 3) + 1; // L1-3 -> 1, L4-6 -> 2, L7-9 -> 3 ...
        boolean martial = characterClass == CharacterClass.FIGHTER
                || characterClass == CharacterClass.DWARF
                || characterClass == CharacterClass.ELF
                || characterClass == CharacterClass.HALFLING;

        // A mid-level martial pregen carries a signature enchanted weapon or armor.
        if (martial && level >= 3) {
            if (dice.d(2) == 1) {
                items.add("+1 " + signatureWeapon(characterClass).toLowerCase());
            } else {
                items.add("+1 armor");
            }
            rolls--;
        }

        for (int i = 0; i < rolls; i++) {
            String pick = GENERAL[dice.d(GENERAL.length) - 1];
            if (pick.equals("Potion of healing")) {
                potions++;
            } else {
                items.add(pick);
            }
        }
        return new Loot(items, potions);
    }

    private static String signatureWeapon(CharacterClass characterClass) {
        return switch (characterClass) {
            case DWARF -> "Battle axe";
            case HALFLING -> "Short sword";
            default -> "Sword";
        };
    }
}
