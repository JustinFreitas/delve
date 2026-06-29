package dev.freitas.delve.game.engine;

import org.springframework.stereotype.Component;

/**
 * Resolves a single B/X attack throw against a descending Armor Class.
 *
 * <p>The number needed on the d20 is {@code THAC0 - targetAC}. A natural 20 always hits and a natural
 * 1 always misses (the common B/X convention); otherwise the modified roll must meet the target
 * number. The pure {@link #resolve} method keeps the logic deterministically testable, while
 * {@link #attack} rolls the die.
 */
@Component
public class AttackResolver {

    /** Outcome of one attack throw. {@code targetNumber} is the d20 value needed before modifiers. */
    public record AttackResult(
            int naturalRoll, int total, int targetNumber, boolean hit, boolean critical, boolean fumble) {}

    private final Dice dice;

    public AttackResolver(Dice dice) {
        this.dice = dice;
    }

    /** The d20 result required to hit: {@code THAC0 - targetAC} (descending AC). */
    public static int targetNumber(int thac0, int targetArmorClass) {
        return thac0 - targetArmorClass;
    }

    public static AttackResult resolve(int naturalRoll, int toHitModifier, int thac0, int targetArmorClass) {
        int needed = targetNumber(thac0, targetArmorClass);
        int total = naturalRoll + toHitModifier;
        boolean critical = naturalRoll == 20;
        boolean fumble = naturalRoll == 1;
        boolean hit = critical || (!fumble && total >= needed);
        return new AttackResult(naturalRoll, total, needed, hit, critical, fumble);
    }

    public AttackResult attack(int toHitModifier, int thac0, int targetArmorClass) {
        return resolve(dice.d20(), toHitModifier, thac0, targetArmorClass);
    }
}
