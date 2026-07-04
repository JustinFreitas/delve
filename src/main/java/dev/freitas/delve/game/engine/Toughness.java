package dev.freitas.delve.game.engine;

import java.util.Comparator;
import java.util.function.ToIntFunction;

/**
 * "Tankiness" ordering: better-armored (lower AC) first, then more hit points. Used as the default
 * marching-order fallback for anyone not explicitly placed via {@code /order} (see
 * {@link dev.freitas.delve.game.model.SaveGame#fullOrder()}) and to rank classes tankiest-first for
 * {@code /hire}'s "smart" bulk-hire mix. Ranks by actual instance stats rather than a hand-maintained
 * per-class table, so it stays correct as gear/ability scores vary — one source of truth.
 */
public final class Toughness {

    private Toughness() {}

    /** Lower {@code armorClass()} first, then higher {@code getMaxHp()}. Ties keep whatever order the
        input list was already in — sort with a stable sort (e.g. {@link java.util.List#sort}) to
        preserve that as the final tiebreak. */
    public static final Comparator<Combatant> BY_TOUGHNESS = byToughness(Combatant::armorClass, Combatant::getMaxHp);

    /** Generic form so a non-{@link Combatant} stand-in (e.g. a per-class AC/HP proxy ranked before any
        instance exists) can share the exact same ranking rule. */
    public static <T> Comparator<T> byToughness(ToIntFunction<T> armorClass, ToIntFunction<T> maxHp) {
        return Comparator.comparingInt(armorClass).thenComparing(Comparator.comparingInt(maxHp).reversed());
    }
}
