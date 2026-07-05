package dev.freitas.delve.game.engine;

import org.springframework.stereotype.Component;

/**
 * Wall-clock time source, in epoch milliseconds, for the engine's few genuinely real-time mechanics
 * (town rest is capped by how much real time has actually passed since the party's last real town
 * visit, not a free-typed number of days — see {@code TownService}). Injected as a Spring bean (backed
 * by {@link System#currentTimeMillis()}). Tests control elapsed time not by faking this clock but by
 * backdating the save's own recorded last-visit timestamp relative to the real clock — see
 * {@code SpellTownTest}'s {@code allowRestDays} helper — so this class stays a plain, single-purpose
 * live clock rather than carrying a second, unexercised fixed-value mode.
 */
@Component
public class GameClock {

    public long nowMillis() {
        return System.currentTimeMillis();
    }
}
