package dev.freitas.delve.game.engine;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * Dice roller for the B/X engine. Injected as a Spring bean (thread-safe, backed by
 * {@link ThreadLocalRandom}); tests construct it with a seeded {@link RandomGenerator} for
 * deterministic results.
 */
@Component
public class Dice {

    // When non-null, a fixed generator (tests). When null, ThreadLocalRandom is used per call so the
    // single shared bean is safe across the virtual threads that run commands.
    private final RandomGenerator fixedRng;

    public Dice() {
        this.fixedRng = null;
    }

    public Dice(RandomGenerator fixedRng) {
        this.fixedRng = fixedRng;
    }

    private RandomGenerator rng() {
        return fixedRng != null ? fixedRng : ThreadLocalRandom.current();
    }

    /** Rolls a single die with the given number of sides (1..sides). */
    public int d(int sides) {
        if (sides < 1) {
            throw new IllegalArgumentException("A die needs at least 1 side, got " + sides);
        }
        return rng().nextInt(sides) + 1;
    }

    /** Rolls {@code count} dice of {@code sides} and returns the sum. */
    public int roll(int count, int sides) {
        return roll(count, sides, 0);
    }

    /** Rolls {@code count}d{@code sides} and adds a flat modifier. */
    public int roll(int count, int sides, int modifier) {
        int sum = modifier;
        for (int i = 0; i < count; i++) {
            sum += d(sides);
        }
        return sum;
    }

    /** Convenience for the ubiquitous 2d6 reaction/morale check. */
    public int roll2d6() {
        return roll(2, 6);
    }

    /** Convenience for the 3d6 ability-score roll. */
    public int roll3d6() {
        return roll(3, 6);
    }

    /** A d20, the to-hit and saving-throw die. */
    public int d20() {
        return d(20);
    }
}
