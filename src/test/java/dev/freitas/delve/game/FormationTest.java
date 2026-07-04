package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.Formation;
import dev.freitas.delve.game.model.Retainer;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormationTest {

    @Test
    void ranksGroupsIntoChunksOfWidth() {
        List<Combatant> order = List.of(alive("A"), alive("B"), alive("C"), alive("D"), alive("E"));
        assertThat(Formation.ranks(order, 3)).hasSize(2);
        assertThat(Formation.ranks(order, 3).get(0)).hasSize(3);
        assertThat(Formation.ranks(order, 3).get(1)).hasSize(2);
        assertThat(Formation.ranks(order, 2)).hasSize(3);
        assertThat(Formation.ranks(order, 1)).hasSize(5);
    }

    @Test
    void nominalRankIsStableRegardlessOfAliveStatus() {
        Retainer a = alive("A");
        Retainer b = dead("B");
        Retainer c = alive("C");
        List<Combatant> order = List.of(a, b, c);
        assertThat(Formation.nominalRank(order, 1, a)).isEqualTo(1);
        assertThat(Formation.nominalRank(order, 1, b)).isEqualTo(2);
        assertThat(Formation.nominalRank(order, 1, c)).isEqualTo(3);
        assertThat(Formation.nominalRank(order, 1, alive("Ghost"))).isEqualTo(-1);
    }

    @Test
    void engagedFrontIsPerColumnNotWholeRank() {
        Retainer a = alive("A"); // rank1 col0
        Retainer b = alive("B"); // rank1 col1
        Retainer c = alive("C"); // rank1 col2
        Retainer d = alive("D"); // rank2 col0
        Retainer e = alive("E"); // rank2 col1
        List<Combatant> order = List.of(a, b, c, d, e);
        int width = 3;

        assertThat(Formation.engagedFront(order, width)).containsExactlyInAnyOrder(a, b, c);

        // Kill column 0's front occupant — only column 0's rank-2 occupant is exposed; others unaffected.
        a.setCurrentHp(0);
        assertThat(Formation.engagedFront(order, width)).containsExactlyInAnyOrder(d, b, c);
        assertThat(Formation.isEngaged(order, width, d)).isTrue();
        assertThat(Formation.isEngaged(order, width, b)).isTrue();
        assertThat(Formation.isEngaged(order, width, e)).isFalse();

        // Column 2 has nobody at rank 2 — killing its front leaves that column empty.
        c.setCurrentHp(0);
        assertThat(Formation.engagedFront(order, width)).containsExactlyInAnyOrder(d, b);
    }

    private Retainer alive(String name) {
        Retainer r = new Retainer();
        r.setName(name);
        r.setMaxHp(10);
        r.setCurrentHp(10);
        return r;
    }

    private Retainer dead(String name) {
        Retainer r = alive(name);
        r.setCurrentHp(0);
        return r;
    }
}
