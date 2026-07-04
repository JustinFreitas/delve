package dev.freitas.delve.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.CharacterFactory;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SellCommandTest {

    private final Dice dice = new Dice(new Random(13));
    private final SellCommand sellCommand = new SellCommand(dice);
    private final CharacterFactory factory = new CharacterFactory(dice);

    @Test
    void haggleBonusIsNeverNegativeAndReachesEveryBand() {
        // Neutral CHA (0 modifier) so the raw 2d6 range maps directly onto every band, including the
        // lowest ("roll <= 2") — a high CHA modifier would make that band unreachable.
        Character pc = factory.create("Trader", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            int bonus = sellCommand.haggleBonusPercent(pc);
            assertThat(bonus).isGreaterThanOrEqualTo(0);
            seen.add(bonus);
        }
        assertThat(seen).containsExactlyInAnyOrder(0, 5, 10, 15, 25);
    }

    @Test
    void higherCharismaShiftsTheBonusUpward() {
        Character charismatic = factory.create("Smooth", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 18));
        Character awkward = factory.create("Blunt", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 3));

        int trials = 300;
        long charismaticTotal = 0;
        long awkwardTotal = 0;
        for (int i = 0; i < trials; i++) {
            charismaticTotal += sellCommand.haggleBonusPercent(charismatic);
            awkwardTotal += sellCommand.haggleBonusPercent(awkward);
        }
        assertThat(charismaticTotal).isGreaterThan(awkwardTotal);
    }
}
