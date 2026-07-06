package dev.freitas.delve.game.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.CharacterFactory;
import dev.freitas.delve.game.MuleFactory;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Encumbrance;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Mule;
import dev.freitas.delve.game.model.SaveGame;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@link CombatService#recoverMuleCargo} — recovering a fallen mule's carried gold, capacity-gated by
    {@link Encumbrance#capacityRemaining} (gygax75-rules' hard 2400gp carry limit) rather than always
    handing it over. */
class CombatServiceTest {

    private final Dice dice = new Dice(new Random(41));
    private final CombatService combat = new CombatService(dice, new SpellService(dice));
    private final CharacterFactory characterFactory = new CharacterFactory(dice);
    private final MuleFactory muleFactory = new MuleFactory(dice);

    @Test
    void fullyRecoversWhenThePcHasRoomToSpare() {
        SaveGame save = new SaveGame();
        Character pc = pc(0);
        save.setCharacter(pc);
        Mule mule = muleFactory.create("Mule");
        mule.setCarriedGold(500);

        ExplorationResult result = new ExplorationResult();
        combat.recoverMuleCargo(save, mule, result);

        assertThat(pc.getGold()).isEqualTo(500);
        assertThat(result.text()).contains("You recover **500 gp**");
        assertThat(result.text()).doesNotContain("left behind");
    }

    @Test
    void leavesTheRestBehindWhenCapacityRunsOut() {
        SaveGame save = new SaveGame();
        Character pc = pc(Encumbrance.MAX_CARRY_GOLD - 100); // only 100 gp of room left
        save.setCharacter(pc);
        Mule mule = muleFactory.create("Mule");
        mule.setCarriedGold(500);

        ExplorationResult result = new ExplorationResult();
        combat.recoverMuleCargo(save, mule, result);

        assertThat(pc.getGold()).isEqualTo(Encumbrance.MAX_CARRY_GOLD);
        assertThat(result.text()).contains("You recover **100 gp**");
        assertThat(result.text()).contains("400 gp** is left behind");
    }

    @Test
    void nobodyRecoversAnyIfEveryPcIsAlreadyAtCapacity() {
        SaveGame save = new SaveGame();
        Character pc = pc(Encumbrance.MAX_CARRY_GOLD); // no room at all
        save.setCharacter(pc);
        Mule mule = muleFactory.create("Mule");
        mule.setCarriedGold(500);

        ExplorationResult result = new ExplorationResult();
        combat.recoverMuleCargo(save, mule, result);

        assertThat(pc.getGold()).isEqualTo(Encumbrance.MAX_CARRY_GOLD); // unchanged
        assertThat(result.text()).contains("nobody has room to recover it");
    }

    @Test
    void spillsAcrossMultiplePcsUntilFullyRecovered() {
        SaveGame save = new SaveGame();
        Character full = pc(Encumbrance.MAX_CARRY_GOLD - 100); // 100 gp of room
        save.setCharacter(full);
        Character roomy = pc(0); // plenty of room
        save.addCharacter(roomy);
        Mule mule = muleFactory.create("Mule");
        mule.setCarriedGold(1000);

        ExplorationResult result = new ExplorationResult();
        combat.recoverMuleCargo(save, mule, result);

        assertThat(full.getGold()).isEqualTo(Encumbrance.MAX_CARRY_GOLD);
        assertThat(roomy.getGold()).isEqualTo(900); // the first PC's shortfall spills to the second
        assertThat(result.text()).contains("recovers **1000 gp**");
        assertThat(result.text()).doesNotContain("left behind");
    }

    @Test
    void noCargoMeansNoMessageAtAll() {
        SaveGame save = new SaveGame();
        Character pc = pc(0);
        save.setCharacter(pc);
        Mule mule = muleFactory.create("Mule"); // never loaded

        ExplorationResult result = new ExplorationResult();
        combat.recoverMuleCargo(save, mule, result);

        assertThat(result.text()).isBlank();
        assertThat(pc.getGold()).isZero();
    }

    private Character pc(int gold) {
        Character c = characterFactory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
        c.setGold(gold);
        return c;
    }
}
