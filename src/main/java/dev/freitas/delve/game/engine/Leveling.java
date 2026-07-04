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
        return awardXp(c, rawXp, dice, false);
    }

    /** As {@link #awardXp(Advanceable, int, Dice)}, but with {@code verbose} spelling out the raw
        award and the prime-requisite percentage behind the adjusted total (used by {@code /autodelve}'s
        verbose log detail, where the terse total alone can look like a miscalculation). */
    public static List<String> awardXp(Advanceable c, int rawXp, Dice dice, boolean verbose) {
        List<String> messages = new ArrayList<>();
        CharacterClass cls = c.getCharacterClass();
        int bonusPercent = cls.xpBonusPercent(c.getAbilities());
        int adjusted = Advancement.adjustedAward(rawXp, bonusPercent);
        c.setXp(c.getXp() + adjusted);
        String suffix = verbose
                ? " (" + rawXp + " × " + (100 + bonusPercent) + "%, total " + c.getXp() + ")."
                : " (total " + c.getXp() + ").";
        messages.add("Gained **" + adjusted + " XP**" + suffix);

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

    /**
     * Advances a character directly to {@code targetLevel}, rolling hit points for each level gained
     * and setting XP to that level's threshold. Unlike {@link #awardXp}, this does not run the
     * prime-requisite award adjustment (which would overshoot on a multi-level jump), so it produces
     * an exact, reproducible level-N character — the basis for pregeneration. Returns one message per
     * level gained.
     */
    public static List<String> advanceTo(Advanceable c, int targetLevel, Dice dice) {
        List<String> messages = new ArrayList<>();
        CharacterClass cls = c.getCharacterClass();
        int capped = Math.min(targetLevel, Advancement.maxLevel(cls));
        while (c.getLevel() < capped) {
            c.setLevel(c.getLevel() + 1);
            int gain = hitPointGain(c, dice);
            c.setMaxHp(c.getMaxHp() + gain);
            c.setCurrentHp(c.getCurrentHp() + gain);
            messages.add("Advanced to level " + c.getLevel() + " " + cls.displayName()
                    + " (+" + gain + " HP, now " + c.getMaxHp() + ").");
        }
        c.setXp(Advancement.xpForLevel(cls, c.getLevel()));
        return messages;
    }

    /**
     * Energy drain: permanently drops one level, rolling off one hit die of HP and resetting XP to the
     * new (lower) level's threshold so it isn't immediately regained by the next XP award. THAC0 and
     * saving throws need no separate adjustment — both are already looked up live from
     * {@code (characterClass, level)} everywhere they're used. A spellcaster's prepared spells are lost;
     * they'll simply re-prepare fewer next time, capped by the new level's slot table. Draining a
     * level-1 combatant instead risks death — see {@code CombatService}'s save-vs-death handling, which
     * calls this only for level 2+.
     */
    public static String drainLevel(Advanceable c, Dice dice) {
        int loss = dice.d(c.getCharacterClass().hitDie());
        c.setLevel(c.getLevel() - 1);
        c.setMaxHp(Math.max(1, c.getMaxHp() - loss));
        c.setCurrentHp(Math.min(c.getCurrentHp(), c.getMaxHp()));
        c.setXp(Advancement.xpForLevel(c.getCharacterClass(), c.getLevel()));
        if (c instanceof dev.freitas.delve.game.model.Character character) {
            character.getMemorizedSpells().clear();
        }
        return c.getName() + " is drained of a level! Now level " + c.getLevel() + " (-" + loss + " HP).";
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
