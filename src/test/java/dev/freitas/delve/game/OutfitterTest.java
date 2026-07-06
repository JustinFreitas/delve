package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import java.util.Random;
import org.junit.jupiter.api.Test;

class OutfitterTest {

    private final CharacterFactory factory = new CharacterFactory(new Dice(new Random(5)));

    @Test
    void aWellFundedFighterGetsTheFullClassKit() {
        Character fighter = bareCharacter(CharacterClass.FIGHTER, 200);

        String summary = Outfitter.outfit(fighter);

        assertThat(fighter.getMainWeapon()).isEqualTo("Sword");
        // Plate mail is the first tier tried now (60gp after the gygax75 price reconciliation, easily
        // affordable here) -- chain mail is only a fallback for when plate isn't affordable.
        assertThat(fighter.getArmor()).isEqualTo(Armor.PLATE_MAIL);
        assertThat(fighter.isShield()).isTrue();
        assertThat(fighter.getTorches()).isEqualTo(6);
        assertThat(fighter.getGold()).isEqualTo(200 - 10 - 60 - 10 - 6); // sword + plate mail + shield + torches
        assertThat(summary).contains("outfitted with");
    }

    @Test
    void aPoorFighterDowngradesArmorInsteadOfGoingWithoutAWeapon() {
        // 15 gp: sword (10) leaves 5 -- too little for plate (60), chain mail (40), or even leather
        // (20), or a shield (10); the rest goes to torches. Still ends up armed, which is the point of
        // "best effort" over an all-or-nothing shopping list.
        Character fighter = bareCharacter(CharacterClass.FIGHTER, 15);

        Outfitter.outfit(fighter);

        assertThat(fighter.getMainWeapon()).isEqualTo("Sword");
        assertThat(fighter.getArmor()).isEqualTo(Armor.NONE);
        assertThat(fighter.isShield()).isFalse();
        assertThat(fighter.getTorches()).isEqualTo(5); // 5 gp left after the sword, 1 gp per torch
        assertThat(fighter.getGold()).isEqualTo(0);
    }

    @Test
    void magicUserNeverBuysArmorOrAShield() {
        Character mu = bareCharacter(CharacterClass.MAGIC_USER, 100);

        Outfitter.outfit(mu);

        assertThat(mu.getMainWeapon()).isEqualTo("Dagger");
        assertThat(mu.getArmor()).isEqualTo(Armor.NONE);
        assertThat(mu.isShield()).isFalse();
        assertThat(mu.getTorches()).isEqualTo(6);
    }

    @Test
    void aThiefNeverGetsAShieldEvenWhenFlushWithGold() {
        Character thief = bareCharacter(CharacterClass.THIEF, 500);

        Outfitter.outfit(thief);

        assertThat(thief.getMainWeapon()).isEqualTo("Sword");
        assertThat(thief.getArmor()).isEqualTo(Armor.LEATHER); // never chain mail/plate for a thief
        assertThat(thief.isShield()).isFalse();
    }

    @Test
    void aDestituteCharacterBuysNothingAndSpendsNothing() {
        Character broke = bareCharacter(CharacterClass.FIGHTER, 0);

        String summary = Outfitter.outfit(broke);

        assertThat(broke.getGold()).isEqualTo(0);
        assertThat(broke.getArmor()).isEqualTo(Armor.NONE);
        assertThat(broke.isShield()).isFalse();
        assertThat(broke.getTorches()).isEqualTo(0);
        assertThat(summary).contains("couldn't afford");
    }

    @Test
    void neverSpendsMoreGoldThanTheCharacterStartedWith() {
        for (CharacterClass cls : CharacterClass.values()) {
            for (int gold = 0; gold <= 200; gold += 7) {
                Character c = bareCharacter(cls, gold);
                Outfitter.outfit(c);
                assertThat(c.getGold()).as(cls + " with " + gold + " gp").isBetween(0, gold);
            }
        }
    }

    private Character bareCharacter(CharacterClass cls, int gold) {
        Character c = factory.createBare("Test", cls, new AbilityScores(12, 12, 12, 12, 12, 12));
        c.setGold(gold);
        return c;
    }
}
