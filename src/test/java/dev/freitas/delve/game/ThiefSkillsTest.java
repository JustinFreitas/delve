package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.ThiefSkills;
import org.junit.jupiter.api.Test;

class ThiefSkillsTest {

    @Test
    void removeTrapsImprovesByLevel() {
        assertThat(ThiefSkills.removeTraps(1)).isEqualTo(10);
        assertThat(ThiefSkills.removeTraps(2)).isEqualTo(15);
        assertThat(ThiefSkills.removeTraps(3)).isEqualTo(20);
    }

    @Test
    void clampedAtTheLowAndHighEnds() {
        assertThat(ThiefSkills.removeTraps(0)).isEqualTo(ThiefSkills.removeTraps(1)); // clamped to level 1
        assertThat(ThiefSkills.removeTraps(30)).isLessThanOrEqualTo(95);
    }
}
