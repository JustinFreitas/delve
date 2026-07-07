package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.GameClock;
import dev.freitas.delve.game.engine.LodgingTier;
import dev.freitas.delve.game.engine.Spell;
import dev.freitas.delve.game.engine.SpellTables;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.ContentType;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.DungeonLevel;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.MonsterType;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.SpellService;
import dev.freitas.delve.game.session.TownService;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SpellTownTest {

    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private final Dice dice = new Dice(new Random(31));
    private final SpellService spells = new SpellService(dice);
    private final CombatService combat = new CombatService(dice, spells);
    private final TownService town = new TownService(spells, dice, new GameClock(), new dev.freitas.delve.config.GameProps());
    private final CharacterFactory factory = new CharacterFactory(dice);
    private final RetainerFactory retainerFactory = new RetainerFactory(dice);

    /** Backdates a save's last-real-town-visit clock so {@link TownService}'s real-time rest cap
        doesn't bind in tests that want to exercise a specific number of healing days -- a 1-minute
        buffer keeps the elapsed-day floor from flaking due to test execution time. */
    private static void allowRestDays(SaveGame save, int days) {
        save.setLastTownVisitMillis(System.currentTimeMillis() - days * DAY_MILLIS - 60_000);
    }

    // --- Spell tables ------------------------------------------------------

    @Test
    void spellSlotsFollowBxProgression() {
        assertThat(SpellTables.slotsAt(CharacterClass.MAGIC_USER, 1, 1)).isEqualTo(1);
        assertThat(SpellTables.slotsAt(CharacterClass.MAGIC_USER, 3, 2)).isEqualTo(1);
        assertThat(SpellTables.slotsAt(CharacterClass.CLERIC, 1, 1)).isEqualTo(0); // no spells at level 1
        assertThat(SpellTables.slotsAt(CharacterClass.CLERIC, 2, 1)).isEqualTo(1);
        assertThat(SpellTables.isCaster(CharacterClass.FIGHTER)).isFalse();
        assertThat(SpellTables.isCaster(CharacterClass.ELF)).isTrue();

        // gygax75 custom casters: Druid and Wood Elf share the Nature table, Gnome has its own Illusion one.
        assertThat(SpellTables.isCaster(CharacterClass.DRUID)).isTrue();
        assertThat(SpellTables.isCaster(CharacterClass.WOOD_ELF)).isTrue();
        assertThat(SpellTables.isCaster(CharacterClass.GNOME)).isTrue();
        assertThat(SpellTables.isCaster(CharacterClass.BARBARIAN)).isFalse();
        assertThat(SpellTables.isCaster(CharacterClass.KNIGHT)).isFalse();
        assertThat(SpellTables.isCaster(CharacterClass.WARDEN)).isFalse();
        assertThat(SpellTables.isCaster(CharacterClass.HALF_ORC)).isFalse();
        assertThat(SpellTables.slotsAt(CharacterClass.DRUID, 1, 1)).isEqualTo(1);
        assertThat(SpellTables.slotsAt(CharacterClass.WOOD_ELF, 1, 1))
                .isEqualTo(SpellTables.slotsAt(CharacterClass.DRUID, 1, 1)); // literally the same table
        assertThat(SpellTables.slotsAt(CharacterClass.GNOME, 1, 1)).isEqualTo(1);
        assertThat(SpellTables.tradition(CharacterClass.DRUID)).isEqualTo(Spell.Tradition.NATURE);
        assertThat(SpellTables.tradition(CharacterClass.GNOME)).isEqualTo(Spell.Tradition.ILLUSION);
    }

    @Test
    void druidAndWoodElfPrayForTheirFullNatureListLikeAClericPraysForDivine() {
        Character druid = factory.create("Robin", CharacterClass.DRUID, new AbilityScores(9, 9, 9, 9, 15, 9));
        List<Spell> available = spells.available(druid);

        assertThat(available).isNotEmpty();
        assertThat(available).allMatch(s -> s.tradition() == Spell.Tradition.NATURE);
        assertThat(available).contains(Spell.ENTANGLE, Spell.CALL_LIGHTNING);
        // No spellbook needed -- it's prayer-based, just like Cleric.
        assertThat(druid.getSpellbook()).isEmpty();

        Character woodElf = factory.create("Sylvan", CharacterClass.WOOD_ELF, new AbilityScores(9, 9, 9, 9, 15, 9));
        assertThat(spells.available(woodElf)).containsExactlyInAnyOrderElementsOf(available);
    }

    @Test
    void gnomeSpellbookIsGatedJustLikeAnArcaneCastersIs() {
        Character gnome = factory.create("Pip", CharacterClass.GNOME, new AbilityScores(9, 12, 9, 14, 9, 9));
        List<Spell> available = spells.available(gnome);

        // Only the one seeded starting spell is known/available -- not the whole Illusion list.
        assertThat(available).hasSize(1);
        assertThat(available.get(0).tradition()).isEqualTo(Spell.Tradition.ILLUSION);

        gnome.getSpellbook().add("Mirror Image");
        assertThat(spells.available(gnome)).extracting(Spell::displayName).contains("Mirror Image");
    }

    @Test
    void nonCastersHaveNoAvailableSpellsAtAll() {
        Character barbarian = factory.create("Conan", CharacterClass.BARBARIAN, new AbilityScores(15, 9, 9, 9, 9, 9));
        assertThat(spells.available(barbarian)).isEmpty();
    }

    @Test
    void magicUserPreparesAndCastsMagicMissileInCombat() {
        SaveGame save = combatSave(CharacterClass.MAGIC_USER, Bestiary.GIANT_RAT, 1);
        Character mu = save.getCharacter();
        // Ensure Magic Missile is in the book and prepared.
        mu.getSpellbook().add("Magic Missile");
        spells.autoPrepare(mu);
        assertThat(mu.getMemorizedSpells()).isNotEmpty();

        // If Magic Missile got prepared, casting it should consume the slot and damage the foe.
        if (spells.isMemorized(mu, Spell.MAGIC_MISSILE)) {
            combat.startCombat(save);
            // Isolate spellcasting from the (separately tested) surprise mechanic, which can otherwise
            // skip the player's round-1 action depending on the seed.
            save.getSession().getCombat().setPartySurprised(false);
            int before = save.getSession().getCombat().aliveMonsters().get(0).getCurrentHp();
            combat.castRound(save, Spell.MAGIC_MISSILE, 1);
            boolean damagedOrDead = save.getSession().getCombat() == null
                    || save.getSession().getCombat().getMonsters().get(0).getCurrentHp() < before;
            assertThat(damagedOrDead).isTrue();
            assertThat(spells.isMemorized(mu, Spell.MAGIC_MISSILE)).isFalse(); // slot spent
        }
    }

    @Test
    void clericHealsOutOfCombat() {
        Character cleric = factory.create("Brother", CharacterClass.CLERIC, new AbilityScores(10, 9, 13, 10, 12, 10));
        cleric.setLevel(2); // clerics get a spell slot at level 2
        cleric.setMaxHp(12);
        cleric.setCurrentHp(4);
        cleric.getMemorizedSpells().add(Spell.CURE_LIGHT_WOUNDS.name());

        var result = spells.castOutOfCombat(cleric, Spell.CURE_LIGHT_WOUNDS, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(cleric.getCurrentHp()).isGreaterThan(4);
        assertThat(spells.isMemorized(cleric, Spell.CURE_LIGHT_WOUNDS)).isFalse();
    }

    @Test
    void damageSpellsCannotBeCastOutOfCombat() {
        Character mu = factory.create("Mage", CharacterClass.MAGIC_USER, new AbilityScores(9, 16, 9, 12, 12, 9));
        mu.getMemorizedSpells().add(Spell.MAGIC_MISSILE.name());
        var result = spells.castOutOfCombat(mu, Spell.MAGIC_MISSILE, null);
        assertThat(result.isSuccess()).isFalse();
        assertThat(spells.isMemorized(mu, Spell.MAGIC_MISSILE)).isTrue(); // not consumed on failure
    }

    // --- Town --------------------------------------------------------------

    @Test
    void townRestHealsThePartyAndRefreshesSpells() {
        SaveGame save = new SaveGame();
        Character mu = factory.create("Mage", CharacterClass.MAGIC_USER, new AbilityScores(9, 16, 9, 12, 12, 12));
        mu.setMaxHp(6);
        mu.setCurrentHp(1);
        mu.setGold(500);
        save.setCharacter(mu);

        Retainer hench = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 9);
        hench.setMaxHp(6); // small, known gap so the default week of rest is guaranteed to cap it
        hench.setCurrentHp(1);
        save.getRetainers().add(hench);
        // Put the party in a dungeon to prove /town abandons it.
        save.getSession().setDungeon(simpleDungeon());
        save.getSession().setState(SessionState.EXPLORING);
        allowRestDays(save, 7); // a full week has actually passed in real time

        int goldBefore = mu.getGold();
        town.returnToTown(save); // defaults to a full week's rest

        assertThat(save.getSession().getState()).isEqualTo(SessionState.IN_TOWN);
        assertThat(save.getSession().getDungeon()).isNull();
        // A week of rest (min 1 hp/day) guarantees at least +7 hp — enough to cap both at max from a
        // 5-hp gap, unlike a single short stay (see restDaysPartiallyHealAShortStay below).
        assertThat(mu.getCurrentHp()).isEqualTo(mu.getMaxHp());
        assertThat(hench.getCurrentHp()).isEqualTo(hench.getMaxHp());
        // One retainer's upkeep, plus a full week's lodging at the default LodgingTier.ROOM (10 gp/day).
        assertThat(mu.getGold()).isEqualTo(goldBefore - 10 - LodgingTier.ROOM.gpPerDay() * 7);
        assertThat(mu.getMemorizedSpells()).isNotEmpty(); // spells re-prepared
    }

    @Test
    void restDaysPartiallyHealAShortStay() {
        // A single day's rest (1d3 hp) should almost never fully close a large HP gap.
        int trials = 200;
        int fullyHealed = 0;
        for (int seed = 0; seed < trials; seed++) {
            Dice localDice = new Dice(new Random(seed));
            TownService localTown = new TownService(new SpellService(localDice), localDice, new GameClock(), new dev.freitas.delve.config.GameProps());
            SaveGame save = new SaveGame();
            Character hero = new CharacterFactory(localDice)
                    .create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
            hero.setMaxHp(50);
            hero.setCurrentHp(1);
            save.setCharacter(hero);
            allowRestDays(save, 1);

            localTown.returnToTown(save, 1);
            if (hero.getCurrentHp() >= hero.getMaxHp()) {
                fullyHealed++;
            }
        }
        assertThat(fullyHealed).isZero(); // 1d3 can never close a 49-hp gap in one day
    }

    @Test
    void manyRestDaysEventuallyCapAtMaxHp() {
        SaveGame save = new SaveGame();
        Character hero = factory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
        hero.setMaxHp(6);
        hero.setCurrentHp(1);
        save.setCharacter(hero);
        allowRestDays(save, 10);

        town.returnToTown(save, 10); // way more than the 5-hp gap needs even at 1 hp/day

        assertThat(hero.getCurrentHp()).isEqualTo(hero.getMaxHp());
    }

    @Test
    void fledRetainersRiskPermanentLossOnReturnToTown() {
        // Statistical: a fled retainer has only a 3-in-6 chance of making it back; across many seeds
        // both outcomes (survives, lost for good) should occur.
        int survived = 0;
        int lost = 0;
        for (int seed = 0; seed < 100; seed++) {
            Dice localDice = new Dice(new Random(seed));
            TownService localTown = new TownService(new SpellService(localDice), localDice, new GameClock(), new dev.freitas.delve.config.GameProps());
            SaveGame save = new SaveGame();
            Character hero = new CharacterFactory(localDice)
                    .create("Hero", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
            save.setCharacter(hero);
            Retainer coward = new RetainerFactory(localDice).create("Mott", CharacterClass.THIEF, 1, 7);
            coward.setFled(true);
            save.getRetainers().add(coward);

            localTown.returnToTown(save, 1);
            if (save.getRetainers().contains(coward)) {
                assertThat(coward.isFled()).isFalse(); // survivors rejoin, no longer sitting out
                survived++;
            } else {
                lost++;
            }
        }
        assertThat(survived).isGreaterThan(0);
        assertThat(lost).isGreaterThan(0);
    }

    @Test
    void unpaidRetainersLoseLoyalty() {
        SaveGame save = new SaveGame();
        Character pc = factory.create("Pauper", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
        pc.setGold(0); // can't pay upkeep
        save.setCharacter(pc);
        Retainer hench = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 8);
        save.getRetainers().add(hench);
        allowRestDays(save, 1); // upkeep is only charged on a real (>0-day) visit

        town.returnToTown(save);
        // Either loyalty dropped, or (if already low) they quit.
        boolean penalized = save.getRetainers().isEmpty() || hench.getLoyalty() == 7;
        assertThat(penalized).isTrue();
    }

    @Test
    void multiPcUpkeepChargesEachOwnersOwnGoldForTheirOwnRetainers() {
        SaveGame save = new SaveGame();
        Character anna = factory.create("Anna", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
        anna.setGold(10); // exactly covers her own one retainer's upkeep
        save.setCharacter(anna);
        Character bram = factory.create("Bram", CharacterClass.CLERIC, new AbilityScores(9, 9, 9, 9, 9, 9));
        bram.setGold(10); // exactly covers his own one retainer's upkeep
        save.addCharacter(bram);
        Retainer annaHench = retainerFactory.create("AnnaHench", CharacterClass.FIGHTER, 1, 9);
        annaHench.setOwner("Anna");
        Retainer bramHench = retainerFactory.create("BramHench", CharacterClass.CLERIC, 1, 9);
        bramHench.setOwner("Bram");
        save.getRetainers().add(annaHench);
        save.getRetainers().add(bramHench);
        allowRestDays(save, 1); // upkeep is only charged on a real (>0-day) visit

        town.returnToTown(save);

        assertThat(anna.getGold()).isZero();
        assertThat(bram.getGold()).isZero();
        // Both fully paid from their own owner's gold -- nobody quits.
        assertThat(save.getRetainers()).containsExactlyInAnyOrder(annaHench, bramHench);
    }

    @Test
    void unpaidRetainersOnlyPenalizeTheirOwnOwnersRetainers() {
        SaveGame save = new SaveGame();
        Character rich = factory.create("Rich", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
        rich.setGold(1000);
        save.setCharacter(rich);
        Character broke = factory.create("Broke", CharacterClass.CLERIC, new AbilityScores(9, 9, 9, 9, 9, 9));
        broke.setGold(0);
        save.addCharacter(broke);
        Retainer richHench = retainerFactory.create("RichHench", CharacterClass.FIGHTER, 1, 8);
        richHench.setOwner("Rich");
        Retainer brokeHench = retainerFactory.create("BrokeHench", CharacterClass.CLERIC, 1, 8);
        brokeHench.setOwner("Broke");
        save.getRetainers().add(richHench);
        save.getRetainers().add(brokeHench);
        allowRestDays(save, 1); // upkeep is only charged on a real (>0-day) visit

        town.returnToTown(save);

        // Rich fully pays -- their retainer is completely unaffected by Broke's inability to pay.
        assertThat(richHench.getLoyalty()).isEqualTo(8);
        boolean brokeHenchPenalized = !save.getRetainers().contains(brokeHench) || brokeHench.getLoyalty() < 8;
        assertThat(brokeHenchPenalized).isTrue();
    }

    @Test
    void multiPcRestHealsEveryPcNotJustThePrimary() {
        SaveGame save = new SaveGame();
        Character anna = factory.create("Anna", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
        anna.setMaxHp(6);
        anna.setCurrentHp(1);
        save.setCharacter(anna);
        Character bram = factory.create("Bram", CharacterClass.CLERIC, new AbilityScores(9, 9, 9, 9, 9, 9));
        bram.setMaxHp(6);
        bram.setCurrentHp(1);
        save.addCharacter(bram);
        allowRestDays(save, 10);

        town.returnToTown(save, 10); // way more than the 5-hp gap needs even at 1 hp/day

        assertThat(anna.getCurrentHp()).isEqualTo(anna.getMaxHp());
        assertThat(bram.getCurrentHp()).isEqualTo(bram.getMaxHp()); // this PC used to never get healed
    }

    @Test
    void freshSaveGrantsNoBacklogHealingOnItsVeryFirstTownVisit() {
        SaveGame save = new SaveGame();
        Character hero = factory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
        hero.setMaxHp(50);
        hero.setCurrentHp(1);
        save.setCharacter(hero);
        // lastTownVisitMillis is null -- no real town visit has ever resolved for this save.

        ExplorationResult result = town.returnToTown(save);

        assertThat(hero.getCurrentHp()).isEqualTo(1); // no real time has passed yet, so no healing
        assertThat(result.text()).contains("No real time has passed");
    }

    @Test
    void zeroDayVisitChargesNoRetainerUpkeepEither() {
        // A rapid repeat /town (e.g. a double-submit) that resolves to 0 real days shouldn't bill
        // upkeep a second time for a "visit" that granted no benefit.
        SaveGame save = new SaveGame();
        Character pc = factory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
        pc.setGold(100);
        save.setCharacter(pc);
        Retainer hench = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 9);
        save.getRetainers().add(hench);
        // lastTownVisitMillis is null -- no real town visit has ever resolved for this save.

        ExplorationResult result = town.returnToTown(save);

        assertThat(pc.getGold()).isEqualTo(100);
        assertThat(result.text()).doesNotContain("gp in retainer upkeep");
    }

    @Test
    void restIsCappedByRealElapsedTimeNotTheRequestedDayCount() {
        SaveGame save = new SaveGame();
        Character hero = factory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
        hero.setMaxHp(200);
        hero.setCurrentHp(1);
        save.setCharacter(hero);
        allowRestDays(save, 2); // only 2 real days have actually passed

        ExplorationResult result = town.returnToTown(save, 30); // ask for far more than that

        assertThat(hero.getCurrentHp()).isLessThanOrEqualTo(1 + 2 * 3); // at most 2 days of 1d3 healing
        assertThat(result.text()).contains("Only 2 days have passed");
    }

    @Test
    void restBanksLeftoverRealTimeAcrossMultipleVisits() {
        SaveGame save = new SaveGame();
        Character hero = factory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
        hero.setMaxHp(200);
        hero.setCurrentHp(1);
        save.setCharacter(hero);
        allowRestDays(save, 10); // 10 real days available

        town.returnToTown(save, 3); // only spend 3 of the 10 available days
        int hpAfterFirstVisit = hero.getCurrentHp();
        // No real time has passed since that call, but ~7 days should still be banked from before.
        ExplorationResult second = town.returnToTown(save, 100);

        assertThat(second.text()).doesNotContain("No real time has passed");
        assertThat(hero.getCurrentHp()).isGreaterThan(hpAfterFirstVisit);
    }

    // --- Set-pieces --------------------------------------------------------

    @Test
    void setPiecesAreLoadedFromContent() {
        var setPieces = dev.freitas.delve.game.dungeon.SetPieceLoader.load();
        assertThat(setPieces).isNotEmpty();
        assertThat(setPieces).anyMatch(sp -> sp.name().equals("The Sealed Crypt"));
    }

    // --- helpers -----------------------------------------------------------

    private Dungeon simpleDungeon() {
        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room room = new Room(0);
        level.addRoom(room);
        level.setEntranceRoomId(0);
        dungeon.addLevel(level);
        return dungeon;
    }

    private SaveGame combatSave(CharacterClass cls, MonsterType type, int count) {
        Character hero = factory.create("Hero", cls, new AbilityScores(12, 13, 12, 13, 12, 12));
        hero.setMaxHp(20);
        hero.setCurrentHp(20);
        SaveGame save = new SaveGame();
        save.setCharacter(hero);

        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room here = new Room(0);
        here.setContent(ContentType.MONSTER);
        here.setMonsterName(type.name());
        here.setMonsterCount(count);
        Room next = new Room(1);
        here.getExits().put(Direction.EAST, new Exit(Direction.EAST, 1, DoorState.NONE, false));
        next.getExits().put(Direction.WEST, new Exit(Direction.WEST, 0, DoorState.NONE, false));
        level.addRoom(here);
        level.addRoom(next);
        level.setEntranceRoomId(0);
        dungeon.addLevel(level);

        save.getSession().setDungeon(dungeon);
        save.getSession().setCurrentLevel(0);
        save.getSession().setCurrentRoomId(0);
        save.getSession().setState(SessionState.EXPLORING);
        return save;
    }
}
