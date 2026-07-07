package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Spell;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CharacterCreationTest {

    private final Dice seededDice = new Dice(new Random(42));
    private final CharacterFactory factory = new CharacterFactory(seededDice);

    @Test
    void abilityModifierTableMatchesBx() {
        assertThat(AbilityScores.modifier(3)).isEqualTo(-3);
        assertThat(AbilityScores.modifier(4)).isEqualTo(-2);
        assertThat(AbilityScores.modifier(5)).isEqualTo(-2);
        assertThat(AbilityScores.modifier(6)).isEqualTo(-1);
        assertThat(AbilityScores.modifier(8)).isEqualTo(-1);
        assertThat(AbilityScores.modifier(9)).isEqualTo(0);
        assertThat(AbilityScores.modifier(12)).isEqualTo(0);
        assertThat(AbilityScores.modifier(13)).isEqualTo(1);
        assertThat(AbilityScores.modifier(15)).isEqualTo(1);
        assertThat(AbilityScores.modifier(16)).isEqualTo(2);
        assertThat(AbilityScores.modifier(17)).isEqualTo(2);
        assertThat(AbilityScores.modifier(18)).isEqualTo(3);
    }

    @Test
    void classRequirementsAreEnforced() {
        // Dwarf requires CON 9+.
        AbilityScores weakCon = new AbilityScores(12, 10, 10, 10, 7, 10);
        assertThat(CharacterClass.DWARF.meetsRequirements(weakCon)).isFalse();
        assertThat(CharacterClass.DWARF.unmetRequirements(weakCon)).contains("CON 9+");

        AbilityScores fineCon = new AbilityScores(12, 10, 10, 10, 9, 10);
        assertThat(CharacterClass.DWARF.meetsRequirements(fineCon)).isTrue();
        // Fighter has no minimums.
        assertThat(CharacterClass.FIGHTER.meetsRequirements(weakCon)).isTrue();

        // Warden (gygax75) requires CON 9+ and WIS 9+ -- a dual minimum, unlike Dwarf's single one.
        AbilityScores weakWis = new AbilityScores(12, 10, 7, 10, 9, 10); // WIS 7, CON 9
        assertThat(CharacterClass.WARDEN.meetsRequirements(weakWis)).isFalse();
        assertThat(CharacterClass.WARDEN.unmetRequirements(weakWis)).contains("WIS 9+");
        AbilityScores meetsBoth = new AbilityScores(12, 10, 10, 10, 9, 9);
        assertThat(CharacterClass.WARDEN.meetsRequirements(meetsBoth)).isTrue();
        // Half-Orc (gygax75) has no minimums at all.
        assertThat(CharacterClass.HALF_ORC.meetsRequirements(weakWis)).isTrue();
    }

    @Test
    void classParsingAcceptsCommonForms() {
        assertThat(CharacterClass.parse("magic-user")).isEqualTo(CharacterClass.MAGIC_USER);
        assertThat(CharacterClass.parse("MagicUser")).isEqualTo(CharacterClass.MAGIC_USER);
        assertThat(CharacterClass.parse("mu")).isEqualTo(CharacterClass.MAGIC_USER);
        assertThat(CharacterClass.parse("Fighter")).isEqualTo(CharacterClass.FIGHTER);
        assertThat(CharacterClass.parse("hobbit")).isEqualTo(CharacterClass.HALFLING);
        assertThat(CharacterClass.parse("bard")).isNull();

        // gygax75 custom classes parse the same way.
        assertThat(CharacterClass.parse("barbarian")).isEqualTo(CharacterClass.BARBARIAN);
        assertThat(CharacterClass.parse("Half-Orc")).isEqualTo(CharacterClass.HALF_ORC);
        assertThat(CharacterClass.parse("wood elf")).isEqualTo(CharacterClass.WOOD_ELF);
        assertThat(CharacterClass.parse("WoodElf")).isEqualTo(CharacterClass.WOOD_ELF);
    }

    @Test
    void onlyTheSevenGygax75ClassesAreFlaggedCustom() {
        for (CharacterClass cls : CharacterClass.values()) {
            boolean expectedCustom = switch (cls) {
                case BARBARIAN, DRUID, KNIGHT, WARDEN, GNOME, HALF_ORC, WOOD_ELF -> true;
                default -> false;
            };
            assertThat(cls.isCustom()).as(cls.toString()).isEqualTo(expectedCustom);
        }
    }

    @Test
    void newClassesGetTheRightSpellTraditionOrNoneAtAll() {
        assertThat(CharacterClass.DRUID.tradition()).isEqualTo(Spell.Tradition.NATURE);
        assertThat(CharacterClass.WOOD_ELF.tradition()).isEqualTo(Spell.Tradition.NATURE);
        assertThat(CharacterClass.GNOME.tradition()).isEqualTo(Spell.Tradition.ILLUSION);
        assertThat(CharacterClass.BARBARIAN.tradition()).isNull();
        assertThat(CharacterClass.KNIGHT.tradition()).isNull();
        assertThat(CharacterClass.WARDEN.tradition()).isNull();
        assertThat(CharacterClass.HALF_ORC.tradition()).isNull();
        // Neither Nature nor Illusion is Arcane/Divine -- the derived booleans stay false for them.
        assertThat(CharacterClass.DRUID.isArcaneCaster()).isFalse();
        assertThat(CharacterClass.DRUID.isDivineCaster()).isFalse();
        assertThat(CharacterClass.GNOME.isArcaneCaster()).isFalse();
    }

    @Test
    void hitPointsNeverDropBelowOne() {
        // CON 3 gives a -3 modifier; on a small hit die HP must still floor at 1.
        AbilityScores frail = new AbilityScores(10, 16, 10, 10, 3, 10);
        for (int i = 0; i < 50; i++) {
            Character mu = factory.create("Test", CharacterClass.MAGIC_USER, frail);
            assertThat(mu.getMaxHp()).isGreaterThanOrEqualTo(1);
            assertThat(mu.getCurrentHp()).isEqualTo(mu.getMaxHp());
        }
    }

    @Test
    void fighterGetsExpectedPackageAndArmorClass() {
        AbilityScores scores = new AbilityScores(15, 9, 9, 13, 12, 9); // DEX 13 => +1
        Character fighter = factory.create("Conan", CharacterClass.FIGHTER, scores);

        assertThat(fighter.getLevel()).isEqualTo(1);
        assertThat(fighter.getXp()).isZero();
        assertThat(fighter.getArmor()).isEqualTo(Armor.CHAIN_MAIL);
        assertThat(fighter.isShield()).isTrue();
        // Chain (5) - shield (1) - DEX mod (1) = 3 descending; ascending 16.
        assertThat(fighter.armorClass()).isEqualTo(3);
        assertThat(fighter.ascendingArmorClass()).isEqualTo(16);
        // The equipped weapon (Sword) and torches live on dedicated fields (mainWeapon, torches), not
        // duplicated into inventory -- inventory holds only exempt items (see ContainerRules.isExempt);
        // everything else, including the Dagger/Sling, is auto-stowed in the starting backpack instead.
        assertThat(fighter.getMainWeapon()).isEqualTo("Sword");
        assertThat(fighter.getTorches()).isEqualTo(6);
        assertThat(fighter.getInventory()).contains("Waterskin").doesNotContain("Sword", "6 Torches", "Dagger");
        assertThat(fighter.getContainers()).hasSize(1);
        assertThat(fighter.getContainers().get(0).getItems()).contains("Dagger", "Sling & 30 stones");
        assertThat(fighter.getGold()).isBetween(30, 180);
        assertThat(fighter.getSpellbook()).isEmpty();
    }

    @Test
    void barbarianAgileFightingBonusScalesWithLevelButOnlyForBarbarians() {
        AbilityScores scores = new AbilityScores(15, 9, 9, 9, 9, 9); // DEX 9 -> no DEX mod, isolates the bonus
        Character barbarian = factory.create("Conan", CharacterClass.BARBARIAN, scores);
        int baseAc = barbarian.armorClass();

        barbarian.setLevel(3);
        assertThat(barbarian.armorClass()).isEqualTo(baseAc); // no bonus yet
        barbarian.setLevel(4);
        assertThat(barbarian.armorClass()).isEqualTo(baseAc - 1);
        barbarian.setLevel(6);
        assertThat(barbarian.armorClass()).isEqualTo(baseAc - 2);
        barbarian.setLevel(8);
        assertThat(barbarian.armorClass()).isEqualTo(baseAc - 3);
        barbarian.setLevel(10);
        assertThat(barbarian.armorClass()).isEqualTo(baseAc - 4);

        // A Fighter with the same gear/level never gets this bonus.
        Character fighter = factory.create("Bram", CharacterClass.FIGHTER, scores);
        fighter.setLevel(10);
        assertThat(fighter.armorClass()).isEqualTo(baseAc);
    }

    @Test
    void woodElfGetsAPlusOneMissileBonusOtherClassesDoNot() {
        AbilityScores scores = new AbilityScores(9, 9, 9, 9, 9, 9); // DEX 9 -> +0 base
        Character woodElf = factory.create("Sylvan", CharacterClass.WOOD_ELF, scores);
        Character fighter = factory.create("Bram", CharacterClass.FIGHTER, scores);

        assertThat(woodElf.missileToHitModifier()).isEqualTo(1);
        assertThat(fighter.missileToHitModifier()).isEqualTo(0);
    }

    @Test
    void magicUserStartsWithReadMagicAndOneSpell() {
        AbilityScores scores = new AbilityScores(9, 16, 9, 12, 12, 9);
        Character mu = factory.create("Raistlin", CharacterClass.MAGIC_USER, scores);
        assertThat(mu.getSpellbook()).hasSize(2).contains("Read Magic");
        assertThat(mu.getArmor()).isEqualTo(Armor.NONE);
    }

    @Test
    void gnomeStartsWithOneIllusionSpellButNoReadMagicSinceItIsNotAnArcaneCaster() {
        AbilityScores scores = new AbilityScores(9, 12, 9, 14, 9, 9);
        Character gnome = factory.create("Pip", CharacterClass.GNOME, scores);
        assertThat(gnome.getSpellbook()).hasSize(1).doesNotContain("Read Magic");
        Spell seeded = Spell.parse(gnome.getSpellbook().get(0));
        assertThat(seeded).isNotNull();
        assertThat(seeded.tradition()).isEqualTo(Spell.Tradition.ILLUSION);
    }

    @Test
    void druidNeedsNoStartingSpellbookSincePrayerBasedLikeCleric() {
        AbilityScores scores = new AbilityScores(9, 9, 9, 9, 15, 9);
        Character druid = factory.create("Robin", CharacterClass.DRUID, scores);
        assertThat(druid.getSpellbook()).isEmpty();
    }

    @Test
    void createBareSkipsTheFreeStartingKitButStillRollsGoldAndHp() {
        AbilityScores scores = new AbilityScores(15, 9, 9, 13, 12, 9);
        Character fighter = factory.createBare("Conan", CharacterClass.FIGHTER, scores);

        assertThat(fighter.getLevel()).isEqualTo(1);
        assertThat(fighter.getArmor()).isEqualTo(Armor.NONE);
        assertThat(fighter.isShield()).isFalse();
        assertThat(fighter.getOffHandWeapon()).isNull();
        assertThat(fighter.getInventory()).isEmpty();
        assertThat(fighter.getTorches()).isZero();
        assertThat(fighter.getGold()).isBetween(30, 180); // still 3d6 x 10, just unspent
        assertThat(fighter.getMaxHp()).isGreaterThanOrEqualTo(1);
        assertThat(fighter.getCurrentHp()).isEqualTo(fighter.getMaxHp());

        // The ordinary create(...) path (pregen/roster/NPC tools) is completely unaffected.
        Character equipped = factory.create("Conan", CharacterClass.FIGHTER, scores);
        assertThat(equipped.getArmor()).isEqualTo(Armor.CHAIN_MAIL);
        assertThat(equipped.getInventory()).isNotEmpty();
    }

    @Test
    void saveGameRoundTripsThroughJackson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AbilityScores scores = new AbilityScores(15, 9, 9, 13, 12, 9);
        Character fighter = factory.create("Conan", CharacterClass.FIGHTER, scores);
        SaveGame save = new SaveGame();
        save.setCharacter(fighter);

        String json = mapper.writeValueAsString(save);
        SaveGame restored = mapper.readValue(json, SaveGame.class);

        assertThat(restored.getCharacter().getName()).isEqualTo("Conan");
        assertThat(restored.getCharacter().getCharacterClass()).isEqualTo(CharacterClass.FIGHTER);
        assertThat(restored.getCharacter().armorClass()).isEqualTo(fighter.armorClass());
        assertThat(restored.getCharacter().getInventory()).isEqualTo(fighter.getInventory());
    }
}
