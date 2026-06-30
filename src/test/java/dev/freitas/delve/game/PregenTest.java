package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Advancement;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.CombatTables;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Leveling;
import dev.freitas.delve.game.engine.SpellTables;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.session.SpellService;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PregenTest {

    private final Dice dice = new Dice(new Random(2024));
    private final SpellService spellService = new SpellService(dice);
    private final CharacterFactory characterFactory = new CharacterFactory(dice);
    private final PregenService pregen = new PregenService(dice, characterFactory, spellService);

    // --- Leveling.advanceTo ------------------------------------------------

    @Test
    void advanceToReachesExactLevelWithThresholdXpAndAccumulatedHp() {
        Character fighter = characterFactory.create("Conan", CharacterClass.FIGHTER,
                new AbilityScores(13, 9, 9, 12, 13, 9));
        int level1Hp = fighter.getMaxHp();

        Leveling.advanceTo(fighter, 5, dice);

        assertThat(fighter.getLevel()).isEqualTo(5);
        assertThat(fighter.getXp()).isEqualTo(Advancement.xpForLevel(CharacterClass.FIGHTER, 5));
        assertThat(fighter.getMaxHp()).isGreaterThan(level1Hp); // gained HP each level
        assertThat(fighter.getCurrentHp()).isEqualTo(fighter.getMaxHp());
    }

    @Test
    void advanceToIsCappedAtClassMaximum() {
        Character halfling = characterFactory.create("Bilbo", CharacterClass.HALFLING,
                new AbilityScores(12, 9, 9, 13, 12, 9));
        Leveling.advanceTo(halfling, 99, dice);
        assertThat(halfling.getLevel()).isEqualTo(Advancement.maxLevel(CharacterClass.HALFLING)); // 8
    }

    // --- PregenService -----------------------------------------------------

    @Test
    void levelFiveFighterHasCorrectDerivedStatsAndKit() {
        Character f = pregen.create("Seeded Fighter", CharacterClass.FIGHTER, 5);

        assertThat(f.getLevel()).isEqualTo(5);
        assertThat(f.thac0()).isEqualTo(CombatTables.classThac0(CharacterClass.FIGHTER, 5)); // 17
        assertThat(f.getMaxHp()).isBetween(5, 60); // 5 hit dice (+/- CON), comfortably bounded
        assertThat(f.getArmor()).isEqualTo(Armor.PLATE_MAIL); // martial upgrade by mid level
        assertThat(f.getGold()).isGreaterThan(200); // accumulated wealth
        // A mid-level fighter carries a signature magic item.
        assertThat(f.getInventory().stream().anyMatch(i -> i.startsWith("+1 "))).isTrue();
    }

    @Test
    void pregennedAbilitiesAlwaysMeetClassRequirements() {
        // Dwarves need CON 9+, halflings DEX 9 & CON 9, elves INT 9 — all must be satisfied.
        for (CharacterClass cls : List.of(CharacterClass.DWARF, CharacterClass.HALFLING, CharacterClass.ELF)) {
            for (int i = 0; i < 20; i++) {
                Character c = pregen.create("Test", cls, 5);
                assertThat(cls.meetsRequirements(c.getAbilities()))
                        .as("%s pregen meets requirements", cls)
                        .isTrue();
            }
        }
    }

    @Test
    void levelFiveMagicUserArrivesWithPreparedSpells() {
        Character mu = pregen.create("Seeded Mage", CharacterClass.MAGIC_USER, 5);
        assertThat(mu.getLevel()).isEqualTo(5);
        // A level-5 magic-user has 2+2+1 = 5 slots; pregen prepares from the (book-derived) list.
        int slots = 0;
        for (int sl = 1; sl <= 3; sl++) {
            slots += SpellTables.slotsAt(CharacterClass.MAGIC_USER, 5, sl);
        }
        assertThat(slots).isEqualTo(5);
        assertThat(mu.getMemorizedSpells()).isNotEmpty();
        assertThat(mu.getSpellbook()).contains("Read Magic");
    }

    @Test
    void batchGenerationProducesRequestedParty() {
        List<Character> party = pregen.createBatch(4, 5, null, null);
        assertThat(party).hasSize(4);
        assertThat(party).allSatisfy(c -> {
            assertThat(c.getLevel()).isEqualTo(5);
            assertThat(c.getName()).isNotBlank();
            assertThat(c.getCharacterClass()).isNotNull();
        });

        List<Character> clerics = pregen.createBatch(3, 6, CharacterClass.CLERIC, null);
        assertThat(clerics).allSatisfy(c -> assertThat(c.getCharacterClass()).isEqualTo(CharacterClass.CLERIC));
    }
}
