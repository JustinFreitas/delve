package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.LightSource;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.LightingService;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LightingServiceTest {

    private final Dice dice = new Dice(new Random(7));
    private final CharacterFactory characterFactory = new CharacterFactory(dice);
    private final RetainerFactory retainerFactory = new RetainerFactory(dice);
    private final LightingService lighting = new LightingService();

    @Test
    void picksAFreeHandedRetainerOverTheShieldedPc() {
        SaveGame save = shieldedFighterSave();
        Retainer thief = retainerFactory.create("Nessa", CharacterClass.THIEF, 1, 9); // 1 hand free
        save.getRetainers().add(thief);

        assertThat(lighting.pickEligibleBearer(save)).isEqualTo("Nessa");
    }

    @Test
    void picksThePcWhenNoRetainerHasAFreeHand() {
        SaveGame save = shieldedFighterSave();
        save.getCharacter().setShield(false); // now the PC has a free hand
        Retainer otherFighter = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 9); // 0 hands free
        save.getRetainers().add(otherFighter);

        assertThat(lighting.pickEligibleBearer(save)).isEqualTo(SaveGame.PLAYER_SLOT);
    }

    @Test
    void nobodyIsEligibleWhenEveryonesHandsAreFull() {
        SaveGame save = shieldedFighterSave(); // sword + shield, 0 free
        Retainer otherFighter = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 9); // 0 free
        save.getRetainers().add(otherFighter);

        assertThat(lighting.pickEligibleBearer(save)).isNull();
    }

    @Test
    void lightUpConsumesATorchAndAssignsAnEligibleBearer() {
        SaveGame save = shieldedFighterSave();
        Retainer thief = retainerFactory.create("Nessa", CharacterClass.THIEF, 1, 9);
        save.getRetainers().add(thief);
        save.getCharacter().setTorches(6);

        ExplorationResult result = new ExplorationResult();
        boolean lit = lighting.lightUp(save, LightSource.TORCH, result);

        assertThat(lit).isTrue();
        assertThat(save.getCharacter().getTorches()).isEqualTo(5);
        assertThat(save.getSession().getActiveLight()).isEqualTo(LightSource.TORCH);
        assertThat(save.getSession().getLightTurnsRemaining()).isEqualTo(LightSource.TORCH.turnsPerUse());
        assertThat(save.getSession().getLightBearer()).isEqualTo("Nessa");
        assertThat(save.getSession().isInDarkness()).isFalse();
    }

    @Test
    void lightUpFailsIntoDarknessWhenNobodyHasAFreeHand() {
        SaveGame save = shieldedFighterSave();
        save.getCharacter().setTorches(6);

        ExplorationResult result = new ExplorationResult();
        boolean lit = lighting.lightUp(save, LightSource.TORCH, result);

        assertThat(lit).isFalse();
        assertThat(save.getSession().isInDarkness()).isTrue();
        assertThat(save.getSession().getActiveLight()).isNull();
        assertThat(save.getCharacter().getTorches()).isEqualTo(6); // nothing consumed
    }

    @Test
    void lightUpFailsWhenNoFuelIsOwned() {
        SaveGame save = shieldedFighterSave();
        save.getCharacter().setShield(false);
        save.getCharacter().setTorches(0);

        ExplorationResult result = new ExplorationResult();
        boolean lit = lighting.lightUp(save, LightSource.TORCH, result);

        assertThat(lit).isFalse();
        assertThat(save.getSession().getActiveLight()).isNull();
    }

    @Test
    void reconcileTrustsAnAlreadyAssignedLivingBearerWithoutRecheckingHands() {
        SaveGame save = shieldedFighterSave(); // PC has 0 free hands
        save.getSession().setActiveLight(LightSource.TORCH);
        save.getSession().setLightBearer(SaveGame.PLAYER_SLOT); // assigned directly, bypassing validation

        ExplorationResult result = new ExplorationResult();
        lighting.reconcileBearer(save, result);

        assertThat(save.getSession().getLightBearer()).isEqualTo(SaveGame.PLAYER_SLOT);
        assertThat(save.getSession().isInDarkness()).isFalse();
        assertThat(result.text()).isBlank();
    }

    @Test
    void reconcilePicksAReplacementWhenTheBearerLeavesTheParty() {
        SaveGame save = shieldedFighterSave();
        save.getCharacter().setShield(false);
        Retainer thief = retainerFactory.create("Nessa", CharacterClass.THIEF, 1, 9);
        save.getRetainers().add(thief);
        save.getSession().setActiveLight(LightSource.TORCH);
        save.getSession().setLightBearer("Nessa");

        save.getRetainers().remove(thief); // dismissed/deserted

        ExplorationResult result = new ExplorationResult();
        lighting.reconcileBearer(save, result);

        assertThat(save.getSession().getLightBearer()).isEqualTo(SaveGame.PLAYER_SLOT);
        assertThat(result.text()).contains("takes up the light");
    }

    @Test
    void reconcileFallsIntoDarknessWhenNoReplacementIsEligible() {
        SaveGame save = shieldedFighterSave(); // solo, shielded, 0 free hands
        Retainer thief = retainerFactory.create("Nessa", CharacterClass.THIEF, 1, 9);
        save.getRetainers().add(thief);
        save.getSession().setActiveLight(LightSource.TORCH);
        save.getSession().setLightBearer("Nessa");

        save.getRetainers().remove(thief);

        ExplorationResult result = new ExplorationResult();
        lighting.reconcileBearer(save, result);

        assertThat(save.getSession().getLightBearer()).isNull();
        assertThat(save.getSession().isInDarkness()).isTrue();
        assertThat(result.text()).contains("darkness falls");
    }

    @Test
    void assignBearerRejectsACandidateWithNoFreeHand() {
        SaveGame save = shieldedFighterSave();
        Retainer otherFighter = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 9); // 0 free
        save.getRetainers().add(otherFighter);

        String failure = lighting.assignBearer(save, "Bryn");

        assertThat(failure).contains("hands are already full");
        assertThat(save.getSession().getLightBearer()).isNull();
    }

    @Test
    void assignBearerSucceedsForAFreeHandedCandidate() {
        SaveGame save = shieldedFighterSave();
        Retainer thief = retainerFactory.create("Nessa", CharacterClass.THIEF, 1, 9);
        save.getRetainers().add(thief);

        String failure = lighting.assignBearer(save, "Nessa");

        assertThat(failure).isNull();
        assertThat(save.getSession().getLightBearer()).isEqualTo("Nessa");
    }

    private SaveGame shieldedFighterSave() {
        Character hero = characterFactory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(13, 9, 9, 12, 12, 12));
        SaveGame save = new SaveGame();
        save.setCharacter(hero);
        return save;
    }
}
