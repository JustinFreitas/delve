package dev.freitas.delve.game.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Awards experience and applies B/X level advancement. XP is adjusted by the prime-requisite bonus,
 * then the character levels up while it crosses thresholds: each new level rolls a hit die plus the
 * CON modifier (minimum 1) up to name level 9; beyond that a fixed amount with no CON bonus.
 */
public final class Leveling {

    private Leveling() {}

    public static List<String> awardXp(Advanceable c, int rawXp, Dice dice) {
        List<String> messages = new ArrayList<>();
        CharacterClass cls = c.getCharacterClass();
        int adjusted = Advancement.adjustedAward(rawXp, cls.xpBonusPercent(c.getAbilities()));
        c.setXp(c.getXp() + adjusted);
        messages.add("Gained **" + adjusted + " XP** (total " + c.getXp() + ").");

        int maxLevel = Advancement.maxLevel(cls);
        while (c.getLevel() < maxLevel
                && c.getXp() >= Advancement.xpForLevel(cls, c.getLevel() + 1)) {
            c.setLevel(c.getLevel() + 1);
            int gain = hitPointGain(c, dice);
            c.setMaxHp(c.getMaxHp() + gain);
            c.setCurrentHp(c.getCurrentHp() + gain);
            messages.add("**Level up!** " + c.getName() + " is now a level " + c.getLevel() + " "
                    + cls.displayName() + " (+" + gain + " HP, " + c.getCurrentHp() + "/" + c.getMaxHp() + ").");
        }
        return messages;
    }

    private static int hitPointGain(Advanceable c, Dice dice) {
        CharacterClass cls = c.getCharacterClass();
        if (c.getLevel() <= 9) {
            return Math.max(1, dice.d(cls.hitDie()) + c.getAbilities().modifier(Ability.CON));
        }
        // Post-name-level: fixed hit points, no CON bonus (fighters/dwarves gain more).
        return switch (cls) {
            case FIGHTER, DWARF, ELF, HALFLING -> 2;
            default -> 1;
        };
    }
}
