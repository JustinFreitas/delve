package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Encumbrance;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** True coin-weight encumbrance (gygax75-rules: 1 coin = 1 cn) — the movement-band table itself, and
    {@link Character#carriedWeightCns()}/{@link Retainer#carriedWeightCns()} summing real carried
    weight (armor, weapons, light supplies, potions, inventory, gold) into it. */
class EncumbranceTest {

    private final Dice dice = new Dice(new Random(21));
    private final CharacterFactory characterFactory = new CharacterFactory(dice);
    private final RetainerFactory retainerFactory = new RetainerFactory(dice);

    // --- Movement bands ------------------------------------------------------

    @Test
    void movementRateStepsThroughEveryBandBoundary() {
        assertThat(Encumbrance.movementRate(0)).isEqualTo(120);
        assertThat(Encumbrance.movementRate(400)).isEqualTo(120);
        assertThat(Encumbrance.movementRate(401)).isEqualTo(90);
        assertThat(Encumbrance.movementRate(800)).isEqualTo(90);
        assertThat(Encumbrance.movementRate(801)).isEqualTo(60);
        assertThat(Encumbrance.movementRate(1200)).isEqualTo(60);
        assertThat(Encumbrance.movementRate(1201)).isEqualTo(30);
        assertThat(Encumbrance.movementRate(2400)).isEqualTo(30);
        assertThat(Encumbrance.movementRate(2401)).isZero(); // past the hard cap: cannot move
    }

    @Test
    void encounterRateIsAThirdOfMovementRate() {
        assertThat(Encumbrance.encounterRate(0)).isEqualTo(40);
        assertThat(Encumbrance.encounterRate(1201)).isEqualTo(10);
        assertThat(Encumbrance.encounterRate(2401)).isZero();
    }

    @Test
    void capacityRemainingNeverGoesNegative() {
        assertThat(Encumbrance.capacityRemaining(0)).isEqualTo(2400);
        assertThat(Encumbrance.capacityRemaining(2400)).isZero();
        assertThat(Encumbrance.capacityRemaining(3000)).isZero();
    }

    @Test
    void overloadedOnlyPastTheHardCap() {
        assertThat(Encumbrance.overloaded(2400)).isFalse();
        assertThat(Encumbrance.overloaded(2401)).isTrue();
    }

    @Test
    void descriptorReflectsEachBandIncludingOverloaded() {
        assertThat(Encumbrance.descriptor(0)).isEqualTo("unencumbered");
        assertThat(Encumbrance.descriptor(500)).isEqualTo("lightly burdened");
        assertThat(Encumbrance.descriptor(900)).isEqualTo("encumbered");
        assertThat(Encumbrance.descriptor(1300)).isEqualTo("heavily encumbered");
        assertThat(Encumbrance.descriptor(2500)).isEqualTo("overloaded (cannot move)");
    }

    // --- Character.carriedWeightCns() -----------------------------------------

    @Test
    void carriedWeightSumsArmorShieldWeaponAndGold() {
        Character c = bareCharacter();
        c.setArmor(Armor.CHAIN_MAIL); // 400
        c.setShield(true);            // 100
        c.setMainWeapon("Sword");     // 60
        c.setGold(500);                // 500
        // torches default to 6 * 20 = 120

        assertThat(c.carriedWeightCns()).isEqualTo(400 + 100 + 60 + 500 + 120);
    }

    @Test
    void carriedWeightIncludesOffHandWeaponLightSuppliesPotionsAndInventory() {
        Character c = bareCharacter();
        c.setMainWeapon("Dagger");        // 10
        c.setOffHandWeapon("Dagger");     // 10
        c.setTorches(0);
        c.setLanterns(2);                  // 2*30 = 60
        c.setOilFlasks(3);                 // 3*10 = 30
        c.setHealingPotions(2);            // 2*10 = 20
        c.setGold(0);
        c.setInventory(java.util.List.of("Backpack", "Rope 50'")); // 20 + 50

        assertThat(c.carriedWeightCns()).isEqualTo(10 + 10 + 60 + 30 + 20 + 20 + 50);
    }

    @Test
    void carriedWeightIgnoresUnrecognizedInventoryStrings() {
        Character c = bareCharacter();
        c.setTorches(0);
        c.setGold(0);
        c.setInventory(java.util.List.of("A Weird Artifact Nobody Priced"));

        // The placeholder "Weapon" main-weapon name still falls back to WeaponCatalog's default 30cn
        // (every character always wields *something*) -- only the unrecognized inventory item is
        // ignored (contributing 0, not an error or a guessed value).
        assertThat(c.carriedWeightCns()).isEqualTo(30);
    }

    @Test
    void aFreshFighterFromCharacterFactoryIsWellUnderCapButNotWeightless() {
        Character fighter = characterFactory.create("Conan", CharacterClass.FIGHTER,
                new AbilityScores(15, 9, 9, 13, 12, 9));

        int weight = fighter.carriedWeightCns();

        assertThat(weight).isGreaterThan(0); // armor+weapon+gear+torches+gold all contribute
        assertThat(weight).isLessThan(Encumbrance.MAX_CARRY_CNS); // never overloaded just from starting kit
    }

    // --- Retainer.carriedWeightCns() ------------------------------------------

    @Test
    void retainerCarriedWeightIsArmorShieldAndBothWeaponsOnly() {
        Retainer r = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 9);
        r.setArmor(Armor.CHAIN_MAIL); // 400
        r.setShield(true);             // 100
        r.setMainWeapon("Sword");      // 60
        r.setSecondaryWeapon("Sling"); // 20

        assertThat(r.carriedWeightCns()).isEqualTo(400 + 100 + 60 + 20);
    }

    @Test
    void retainerWithNoSecondaryWeaponDoesNotCountOne() {
        Retainer r = retainerFactory.create("Bryn", CharacterClass.MAGIC_USER, 1, 9);
        r.setArmor(Armor.NONE);
        r.setShield(false);
        r.setMainWeapon("Dagger"); // 10
        r.setSecondaryWeapon(null);

        assertThat(r.carriedWeightCns()).isEqualTo(10);
    }

    private Character bareCharacter() {
        Character c = new Character();
        c.setName("Test");
        c.setCharacterClass(CharacterClass.FIGHTER);
        c.setAbilities(new AbilityScores(12, 12, 12, 12, 12, 12));
        c.setMaxHp(10);
        c.setCurrentHp(10);
        c.setArmor(Armor.NONE);
        c.setShield(false);
        return c;
    }
}
