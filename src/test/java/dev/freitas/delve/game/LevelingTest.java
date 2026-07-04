package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Leveling;
import dev.freitas.delve.game.model.Character;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LevelingTest {

    private final Dice dice = new Dice(new Random(1));

    @Test
    void terseAwardOmitsTheRawAndPercentBreakdown() {
        Character c = fighterWithStr(8); // -10% prime-requisite bracket
        List<String> messages = Leveling.awardXp(c, 23, dice);

        assertThat(messages.get(0)).isEqualTo("Gained **20 XP** (total 20).");
    }

    @Test
    void verboseAwardShowsTheRawAmountAndPrimeRequisitePercent() {
        Character c = fighterWithStr(8); // -10% prime-requisite bracket
        List<String> messages = Leveling.awardXp(c, 23, dice, true);

        assertThat(messages.get(0)).isEqualTo("Gained **20 XP** (23 × 90%, total 20).");
    }

    @Test
    void verboseAwardReflectsAPositivePrimeRequisiteBonus() {
        Character c = fighterWithStr(16); // +10% prime-requisite bracket

        List<String> messages = Leveling.awardXp(c, 23, dice, true);

        assertThat(messages.get(0)).isEqualTo("Gained **25 XP** (23 × 110%, total 25).");
    }

    private Character fighterWithStr(int str) {
        Character c = new Character();
        c.setName("Tester");
        c.setCharacterClass(CharacterClass.FIGHTER);
        c.setLevel(1);
        c.setXp(0);
        c.setAbilities(new AbilityScores(str, 9, 9, 9, 9, 9));
        return c;
    }
}
