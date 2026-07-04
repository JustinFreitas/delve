package dev.freitas.delve.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.RetainerFactory;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HireCommandTest {

    private final Dice dice = new Dice(new Random(11));
    private final RetainerFactory retainerFactory = new RetainerFactory(dice);
    private final HireCommand hireCommand = new HireCommand(retainerFactory, dice);

    @Test
    void smartHireOrderIsFighterDwarfClericElfHalflingThiefMagicUser() {
        assertThat(HireCommand.SMART_HIRE_ORDER).containsExactly(
                CharacterClass.FIGHTER, CharacterClass.DWARF, CharacterClass.CLERIC,
                CharacterClass.ELF, CharacterClass.HALFLING, CharacterClass.THIEF, CharacterClass.MAGIC_USER);
    }

    @Test
    void bulkHireSingleModeFillsEveryAvailableSlotWithTheGivenClass() {
        SaveGame save = new SaveGame();
        Character pc = pc(1000);
        save.setCharacter(pc);

        List<Retainer> hired = hireCommand.bulkHire(save, pc, HireCommand.BulkMode.SINGLE, CharacterClass.CLERIC, 4);

        assertThat(hired).hasSize(4);
        assertThat(hired).allMatch(r -> r.getCharacterClass() == CharacterClass.CLERIC);
        assertThat(save.getRetainers()).containsExactlyElementsOf(hired);
    }

    @Test
    void bulkHireSmartModeHiresTankiestClassesFirstWhenSlotsAreLimited() {
        SaveGame save = new SaveGame();
        Character pc = pc(1000);
        save.setCharacter(pc);

        List<Retainer> hired = hireCommand.bulkHire(save, pc, HireCommand.BulkMode.SMART, null, 3);

        assertThat(hired.stream().map(Retainer::getCharacterClass).collect(Collectors.toList()))
                .containsExactly(CharacterClass.FIGHTER, CharacterClass.DWARF, CharacterClass.CLERIC);
    }

    @Test
    void bulkHireSmartModeHiresOneOfEachClassWhenFillingAllSevenSlots() {
        SaveGame save = new SaveGame();
        Character pc = pc(1000);
        save.setCharacter(pc);

        List<Retainer> hired = hireCommand.bulkHire(save, pc, HireCommand.BulkMode.SMART, null, 7);

        assertThat(hired.stream().map(Retainer::getCharacterClass).collect(Collectors.toList()))
                .containsExactlyElementsOf(HireCommand.SMART_HIRE_ORDER);
    }

    @Test
    void bulkHireRandomModeProducesTheExactSeededSequence() {
        Dice seeded = new Dice(new Random(11));
        RetainerFactory factory = new RetainerFactory(seeded);
        HireCommand command = new HireCommand(factory, seeded);
        SaveGame save = new SaveGame();
        Character pc = pc(1000);
        save.setCharacter(pc);

        List<Retainer> hired = command.bulkHire(save, pc, HireCommand.BulkMode.RANDOM, null, 5);

        // Regression-locked against seed 11: the class roll, name-pool roll, and the hit-die roll
        // inside retainerFactory.create(...) all interleave on the same Dice, so this exact sequence
        // is only reproducible by re-running the real bulkHire path (not by hand-simulating the rolls).
        assertThat(hired.stream().map(Retainer::getCharacterClass).collect(Collectors.toList()))
                .containsExactly(CharacterClass.FIGHTER, CharacterClass.THIEF, CharacterClass.CLERIC,
                        CharacterClass.FIGHTER, CharacterClass.HALFLING);
    }

    @Test
    void bulkHireNeverProducesDuplicateNamesWithinOneBatch() {
        for (int seed = 0; seed < 5; seed++) {
            Dice seeded = new Dice(new Random(seed));
            RetainerFactory factory = new RetainerFactory(seeded);
            HireCommand command = new HireCommand(factory, seeded);
            SaveGame save = new SaveGame();
            Character pc = pc(1000);
            save.setCharacter(pc);

            List<Retainer> hired = command.bulkHire(save, pc, HireCommand.BulkMode.SMART, null, 7);

            assertThat(hired.stream().map(Retainer::getName).distinct().count())
                    .as("seed " + seed)
                    .isEqualTo(hired.size());
        }
    }

    @Test
    void bulkHireDeductsGoldPerHireAndRespectsBothCapsSimultaneously() {
        assertThat(HireCommand.slotsAvailable(7, 2)).isEqualTo(5);
        assertThat(HireCommand.affordableHires(120)).isEqualTo(4); // 120 / 25 = 4, remainder untouched

        SaveGame goldLimited = new SaveGame();
        Character poorPc = pc(60); // affords 2 (60/25), CHA cap allows far more
        goldLimited.setCharacter(poorPc);
        int goldLimitedCount = Math.min(HireCommand.slotsAvailable(7, 0), HireCommand.affordableHires(poorPc.getGold()));
        List<Retainer> goldLimitedHires =
                hireCommand.bulkHire(goldLimited, poorPc, HireCommand.BulkMode.SMART, null, goldLimitedCount);
        assertThat(goldLimitedHires).hasSize(2);
        assertThat(poorPc.getGold()).isEqualTo(10); // 60 - 2*25

        SaveGame slotLimited = new SaveGame();
        Character richPc = pc(1000);
        slotLimited.setCharacter(richPc);
        int slotLimitedCount = Math.min(HireCommand.slotsAvailable(2, 0), HireCommand.affordableHires(richPc.getGold()));
        List<Retainer> slotLimitedHires =
                hireCommand.bulkHire(slotLimited, richPc, HireCommand.BulkMode.SMART, null, slotLimitedCount);
        assertThat(slotLimitedHires).hasSize(2);
        assertThat(richPc.getGold()).isEqualTo(950); // 1000 - 2*25
    }

    private Character pc(int gold) {
        Character c = new Character();
        c.setName("Hero");
        c.setCharacterClass(CharacterClass.FIGHTER);
        c.setAbilities(new AbilityScores(13, 9, 9, 12, 12, 12));
        c.setGold(gold);
        return c;
    }
}
