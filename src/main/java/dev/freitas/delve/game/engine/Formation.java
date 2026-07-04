package dev.freitas.delve.game.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Marching-order rank/column math for a party (Gygax75: up to 3 abreast, narrower in tight corridors).
 * A "rank" is a contiguous chunk of {@code width} marching-order slots; a "column" (file) is every
 * slot at the same offset within its rank. Breaking is per-column: if a column's front occupant falls
 * or that slot was never filled, the next living occupant down that same column becomes exposed —
 * other columns are unaffected. Operates on the full order (living and dead) so column indices stay
 * stable as people fall; see {@link dev.freitas.delve.game.model.SaveGame#fullOrder()}.
 */
public final class Formation {

    private Formation() {}

    /** Groups the full order into ranks of at most {@code width}; the last rank may be shorter. */
    public static List<List<Combatant>> ranks(List<Combatant> fullOrder, int width) {
        List<List<Combatant>> ranks = new ArrayList<>();
        for (int i = 0; i < fullOrder.size(); i += width) {
            ranks.add(fullOrder.subList(i, Math.min(i + width, fullOrder.size())));
        }
        return ranks;
    }

    /** 1-based rank by fixed marching-order position (unaffected by deaths), or -1 if not present. */
    public static int nominalRank(List<Combatant> fullOrder, int width, Combatant who) {
        int index = fullOrder.indexOf(who);
        return index < 0 ? -1 : index / width + 1;
    }

    /**
     * The front-most living occupant of each column: what monsters can strike in melee, and who can
     * melee "as front-line" regardless of weapon class. A column with no living occupant contributes
     * nothing (it simply isn't targetable/eligible).
     */
    public static List<Combatant> engagedFront(List<Combatant> fullOrder, int width) {
        List<Combatant> front = new ArrayList<>();
        for (int column = 0; column < width; column++) {
            for (int index = column; index < fullOrder.size(); index += width) {
                Combatant occupant = fullOrder.get(index);
                if (occupant.isAlive()) {
                    front.add(occupant);
                    break;
                }
            }
        }
        return front;
    }

    /** Whether {@code who} is alive and is their column's current front occupant. */
    public static boolean isEngaged(List<Combatant> fullOrder, int width, Combatant who) {
        return who.isAlive() && engagedFront(fullOrder, width).contains(who);
    }
}
