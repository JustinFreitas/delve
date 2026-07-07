package dev.freitas.delve.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.RetainerFactory;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.RetainerRules;
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
    private final HireCommand hireCommand = new HireCommand(retainerFactory, dice, new dev.freitas.delve.config.GameProps());

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
        HireCommand command = new HireCommand(factory, seeded, new dev.freitas.delve.config.GameProps());
        SaveGame save = new SaveGame();
        Character pc = pc(1000);
        save.setCharacter(pc);

        List<Retainer> hired = command.bulkHire(save, pc, HireCommand.BulkMode.RANDOM, null, 5);

        // Regression-locked against seed 11: the class roll, name-pool roll, banked-gold roll, and the
        // (reroll-floored) hit-die roll inside retainerFactory.create(...) all interleave on the same
        // Dice, so this exact sequence is only reproducible by re-running the real bulkHire path (not by
        // hand-simulating the rolls).
        assertThat(hired.stream().map(Retainer::getCharacterClass).collect(Collectors.toList()))
                .containsExactly(CharacterClass.FIGHTER, CharacterClass.ELF, CharacterClass.MAGIC_USER,
                        CharacterClass.DWARF, CharacterClass.FIGHTER);
    }

    @Test
    void bulkHireNeverProducesDuplicateNamesWithinOneBatch() {
        for (int seed = 0; seed < 5; seed++) {
            Dice seeded = new Dice(new Random(seed));
            RetainerFactory factory = new RetainerFactory(seeded);
            HireCommand command = new HireCommand(factory, seeded, new dev.freitas.delve.config.GameProps());
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
        // 60 - 2*25 = 10, plus each hire's 3d6 banked gold (3-18 per hire) joining the party's purse.
        assertThat(poorPc.getGold()).isBetween(10 + 2 * 3, 10 + 2 * 18);

        SaveGame slotLimited = new SaveGame();
        Character richPc = pc(1000);
        slotLimited.setCharacter(richPc);
        int slotLimitedCount = Math.min(HireCommand.slotsAvailable(2, 0), HireCommand.affordableHires(richPc.getGold()));
        List<Retainer> slotLimitedHires =
                hireCommand.bulkHire(slotLimited, richPc, HireCommand.BulkMode.SMART, null, slotLimitedCount);
        assertThat(slotLimitedHires).hasSize(2);
        // 1000 - 2*25 = 950, plus each hire's 3d6 banked gold (3-18 per hire) joining the party's purse.
        assertThat(richPc.getGold()).isBetween(950 + 2 * 3, 950 + 2 * 18);
    }

    @Test
    void bulkHireBanksEachRetainersStartingGoldIntoThePartysPurse() {
        SaveGame save = new SaveGame();
        Character pc = pc(1000);
        save.setCharacter(pc);
        int goldBefore = pc.getGold();

        hireCommand.bulkHire(save, pc, HireCommand.BulkMode.SMART, null, 3);

        // Net change: -3*HIRING_FEE (fees paid) + banked gold (3-18 per hire, 3 hires).
        assertThat(pc.getGold()).isBetween(
                goldBefore - 3 * HireCommand.HIRING_FEE + 3 * 3,
                goldBefore - 3 * HireCommand.HIRING_FEE + 3 * 18);
    }

    @Test
    void hireOneDeductsGoldAndSeedsLoyaltyFromTheNamedPayerNotTheFirstRolledPc() {
        SaveGame save = new SaveGame();
        Character leader = pc(1000); // low CHA, plenty of gold
        save.setCharacter(leader);
        Character charismatic = pc(200);
        charismatic.setName("Charismatic");
        charismatic.setAbilities(new AbilityScores(9, 9, 9, 9, 9, 18)); // CHA 18 -> max loyalty adjustment
        save.addCharacter(charismatic);

        Retainer hired = hireCommand.hireOne(save, charismatic, CharacterClass.FIGHTER, "Test");

        assertThat(hired.getLoyalty()).isEqualTo(RetainerRules.baseLoyalty(18));
        assertThat(charismatic.getGold()).isLessThan(200); // fee deducted from the named payer...
        assertThat(leader.getGold()).isEqualTo(1000); // ...not from the party's first-rolled PC
        assertThat(save.getRetainers()).containsExactly(hired);
        assertThat(hired.getOwner()).isEqualTo(charismatic.getName());
    }

    @Test
    void fillPartyDoesNotLetOnePcsOwnedRetainersBlockAnothersOwnCap() {
        SaveGame save = new SaveGame();
        Character a = pc(1000);
        a.setName("A");
        a.setAbilities(new AbilityScores(9, 9, 9, 9, 9, 8)); // CHA 8 -> cap 3
        save.setCharacter(a);
        Character b = pc(1000);
        b.setName("B");
        b.setAbilities(new AbilityScores(9, 9, 9, 9, 9, 5)); // CHA 5 -> cap 2
        save.addCharacter(b);

        // Fill A up to A's own cap (3) first, simulating retainers A already hired individually.
        hireCommand.hireOne(save, a, CharacterClass.FIGHTER, "A1");
        hireCommand.hireOne(save, a, CharacterClass.FIGHTER, "A2");
        hireCommand.hireOne(save, a, CharacterClass.FIGHTER, "A3");

        // Under the old whole-party-total cap check, A's 3 owned retainers would already meet/exceed
        // B's own cap of 2 too, wrongly blocking B from ever being picked. B must still be eligible.
        List<HireCommand.PartyHire> hires = hireCommand.fillParty(save, 2 + 3 + 1);

        assertThat(hires).hasSize(1);
        assertThat(hires.get(0).payer()).isEqualTo(b);
    }

    @Test
    void fillPartyPicksTheRichestEligiblePayerEachHireAndSwitchesOnceTheyHitTheirCap() {
        SaveGame save = new SaveGame();
        Character rich = pc(100_000);
        rich.setName("Rich");
        rich.setAbilities(new AbilityScores(9, 9, 9, 9, 9, 8)); // CHA 8 -> cap 3
        save.setCharacter(rich);
        Character charismatic = pc(1000);
        charismatic.setName("Charismatic");
        charismatic.setAbilities(new AbilityScores(9, 9, 9, 9, 9, 18)); // CHA 18 -> cap 7
        save.addCharacter(charismatic);

        List<HireCommand.PartyHire> hires = hireCommand.fillParty(save, 9); // 2 PCs + 7 retainers = 9

        assertThat(hires).hasSize(7);
        assertThat(hires.subList(0, 3)).allMatch(h -> h.payer() == rich);
        assertThat(hires.subList(3, 7)).allMatch(h -> h.payer() == charismatic);
        assertThat(save.getRetainers()).hasSize(7);
    }

    @Test
    void fillPartyStopsShortWhenNoPcCanAffordOrAuthorizeMore() {
        SaveGame save = new SaveGame();
        Character poor = pc(20); // can't afford even one hire (fee 25)
        poor.setAbilities(new AbilityScores(9, 9, 9, 9, 9, 3)); // CHA 3 -> cap 1
        save.setCharacter(poor);

        List<HireCommand.PartyHire> hires = hireCommand.fillParty(save, 9);

        assertThat(hires).isEmpty();
        assertThat(save.getRetainers()).isEmpty();
    }

    @Test
    void fillPartyReturnsEmptyWhenAlreadyAtOrAboveTarget() {
        SaveGame save = new SaveGame();
        Character pc = pc(1000);
        save.setCharacter(pc);

        List<HireCommand.PartyHire> hires = hireCommand.fillParty(save, 1); // already 1 member, target 1

        assertThat(hires).isEmpty();
        assertThat(save.getRetainers()).isEmpty();
    }

    private Character pc(int gold) {
        Character c = new Character();
        c.setName("Hero");
        c.setCharacterClass(CharacterClass.FIGHTER);
        c.setAbilities(new AbilityScores(13, 9, 9, 12, 12, 12));
        c.setGold(gold);
        c.setMaxHp(10);
        c.setCurrentHp(10); // alive -- fillParty filters payers via save.livingCharacters()
        return c;
    }
}
