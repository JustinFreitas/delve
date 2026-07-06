package dev.freitas.delve.game.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.MuleFactory;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Encumbrance;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Mule;
import dev.freitas.delve.game.model.SaveGame;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@link CombatService#recoverMuleCargo} — recovering a fallen mule's carried gold, capacity-gated by
    {@link Encumbrance#capacityRemaining} against the PC's real total {@link Character#carriedWeightCns()}
    (gear weight counts, not just gold) rather than always handing it over. */
class CombatServiceTest {

    // A bare test PC's own baseline weight before gold: no armor/shield, torches zeroed, and the
    // unset mainWeapon field ("Weapon") falls back to WeaponCatalog's default 30cn (every character
    // always wields *something*). Each test's gold is chosen relative to this so the resulting
    // carriedWeightCns() lands on a clean boundary.
    private static final int BASELINE_WEIGHT_CNS = 30;

    private final Dice dice = new Dice(new Random(41));
    private final CombatService combat = new CombatService(dice, new SpellService(dice));
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
        // gold chosen so carriedWeightCns() = 2300, leaving exactly 100 cns of room.
        Character pc = pc(Encumbrance.MAX_CARRY_CNS - BASELINE_WEIGHT_CNS - 100);
        save.setCharacter(pc);
        Mule mule = muleFactory.create("Mule");
        mule.setCarriedGold(500);

        ExplorationResult result = new ExplorationResult();
        combat.recoverMuleCargo(save, mule, result);

        assertThat(pc.carriedWeightCns()).isEqualTo(Encumbrance.MAX_CARRY_CNS); // now exactly at the cap
        assertThat(result.text()).contains("You recover **100 gp**");
        assertThat(result.text()).contains("400 gp** is left behind");
    }

    @Test
    void nobodyRecoversAnyIfEveryPcIsAlreadyAtCapacity() {
        SaveGame save = new SaveGame();
        // gold chosen so carriedWeightCns() is already exactly at the hard cap -- no room at all.
        Character pc = pc(Encumbrance.MAX_CARRY_CNS - BASELINE_WEIGHT_CNS);
        save.setCharacter(pc);
        Mule mule = muleFactory.create("Mule");
        mule.setCarriedGold(500);
        int goldBefore = pc.getGold();

        ExplorationResult result = new ExplorationResult();
        combat.recoverMuleCargo(save, mule, result);

        assertThat(pc.getGold()).isEqualTo(goldBefore); // unchanged
        assertThat(result.text()).contains("nobody has room to recover it");
    }

    @Test
    void spillsAcrossMultiplePcsUntilFullyRecovered() {
        SaveGame save = new SaveGame();
        Character full = pc(Encumbrance.MAX_CARRY_CNS - BASELINE_WEIGHT_CNS - 100); // 100 cns of room
        save.setCharacter(full);
        Character roomy = pc(0); // plenty of room
        save.addCharacter(roomy);
        Mule mule = muleFactory.create("Mule");
        mule.setCarriedGold(1000);

        ExplorationResult result = new ExplorationResult();
        combat.recoverMuleCargo(save, mule, result);

        assertThat(full.carriedWeightCns()).isEqualTo(Encumbrance.MAX_CARRY_CNS); // fully topped up
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
        Character c = new Character();
        c.setName("Hero");
        c.setCharacterClass(CharacterClass.FIGHTER);
        c.setAbilities(new AbilityScores(9, 9, 9, 9, 9, 9));
        c.setMaxHp(10);
        c.setCurrentHp(10);
        c.setArmor(Armor.NONE);
        c.setShield(false);
        c.setTorches(0);
        c.setGold(gold);
        return c;
    }
}
