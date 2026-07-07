package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.GameClock;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.AutodelveService;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.LightingService;
import dev.freitas.delve.game.session.MuleService;
import dev.freitas.delve.game.session.ExplorationService;
import dev.freitas.delve.game.session.SpellService;
import dev.freitas.delve.game.session.TownService;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AutodelveTest {

    private AutodelveService newService(Dice dice) {
        SpellService spells = new SpellService(dice);
        CombatService combat = new CombatService(dice, spells);
        ExplorationService exploration = new ExplorationService(dice, new DungeonGenerator(dice), combat, new LightingService(), new MuleService());
        TownService town = new TownService(spells, dice, new GameClock(), new dev.freitas.delve.config.GameProps());
        return new AutodelveService(dice, exploration, combat, town);
    }

    private SaveGame fighterSave(Dice dice) {
        SaveGame save = new SaveGame();
        Character hero = new CharacterFactory(dice)
                .create("Sim", CharacterClass.FIGHTER, new AbilityScores(13, 9, 9, 12, 13, 9));
        save.setCharacter(hero);
        return save;
    }

    @Test
    void bxOsePaceTerminatesAndLeavesAConsistentState() {
        // The default by-the-book pace must always halt and leave the save coherent — never hang.
        for (long seed = 1; seed <= 20; seed++) {
            Dice dice = new Dice(new Random(seed));
            AutodelveService service = newService(dice);
            SaveGame save = fighterSave(dice);
            Character hero = save.getCharacter();

            AutodelveService.Result result = service.run(save, 3); // default BX_OSE

            assertThat(result.outcome()).isNotNull();
            assertThat(result.episodes()).isBetween(0, 150);
            assertThat(result.finalLevel()).isGreaterThanOrEqualTo(result.startLevel());
            if (hero.isAlive()) {
                assertThat(save.getSession().getState()).isEqualTo(SessionState.IN_TOWN);
            } else {
                assertThat(result.outcome()).isEqualTo(AutodelveService.Outcome.DIED);
            }
        }
    }

    @Test
    void fastPaceLevelsCharactersUpEveryFewDelves() {
        // FAST pace targets ~one level per 3-4 delves, so most survivors reach the target.
        int reached = 0;
        for (long seed = 1; seed <= 20; seed++) {
            Dice dice = new Dice(new Random(seed));
            AutodelveService service = newService(dice);
            SaveGame save = fighterSave(dice);
            Character hero = save.getCharacter();

            AutodelveService.Result result = service.run(save, 4, AutodelveService.Pace.FAST);

            if (result.outcome() == AutodelveService.Outcome.REACHED_TARGET) {
                assertThat(hero.getLevel()).isGreaterThanOrEqualTo(4);
                reached++;
            }
        }
        assertThat(reached).isGreaterThan(0); // the fast cadence actually delivers levels
    }

    @Test
    void targetAtOrBelowCurrentLevelRunsExactlyOneDelve() {
        for (long seed = 1; seed <= 20; seed++) {
            Dice dice = new Dice(new Random(seed));
            AutodelveService service = newService(dice);
            SaveGame save = fighterSave(dice);

            AutodelveService.Result result = service.run(save, 1); // level 1 hero, target == current level

            assertThat(result.episodes()).isEqualTo(1);
            assertThat(result.outcome()).isIn(AutodelveService.Outcome.SINGLE_DELVE, AutodelveService.Outcome.DIED);
        }
    }

    @Test
    void singleDelveLogIsGroupedByDelveWithMilestoneLines() {
        Dice dice = new Dice(new Random(5));
        AutodelveService service = newService(dice);
        SaveGame save = fighterSave(dice);

        AutodelveService.Result result = service.run(save, 1);

        assertThat(result.log()).isNotEmpty();
        assertThat(result.log().get(0)).isEqualTo("Delve 1:");
        // Every subsequent line is either the start of a later delve or an indented milestone bullet.
        for (int i = 1; i < result.log().size(); i++) {
            String line = result.log().get(i);
            assertThat(line.startsWith("  - ") || line.matches("Delve \\d+:")).isTrue();
        }
    }

    @Test
    void noArgumentDefaultToTheCharactersCurrentLevelViaTheCommandsOwnDefaulting() {
        // AutodelveCommand defaults target to the character's current level (mirrored here directly on
        // the service, since a level-1 default target is exactly "target <= current level").
        Dice dice = new Dice(new Random(9));
        AutodelveService service = newService(dice);
        SaveGame save = fighterSave(dice);
        int currentLevel = save.getCharacter().getLevel();

        AutodelveService.Result result = service.run(save, currentLevel);

        assertThat(result.episodes()).isEqualTo(1);
    }

    @Test
    void trappedTreasureIsRetriedButEventuallyGivenUpOnGracefully() {
        // A Fighter has only the flat low fallback disarm chance. Encountering a trapped-treasure room
        // at all is itself a compound low-probability event in a single short delve, so: give the hero
        // enough HP that a few 1d4 trap hits won't trip the "retreat at half HP" safety net before the
        // retry cap is reached, and search a generous seed range for the (otherwise fully deterministic
        // and bounded) give-up path actually firing at least once.
        boolean sawGiveUp = false;
        for (long seed = 1; seed <= 300 && !sawGiveUp; seed++) {
            Dice dice = new Dice(new Random(seed));
            AutodelveService service = newService(dice);
            SaveGame save = fighterSave(dice);
            save.getCharacter().setMaxHp(500);
            save.getCharacter().setCurrentHp(500);

            AutodelveService.Result result = service.run(save, 1); // single delve

            for (String line : result.log()) {
                if (line.contains("Gave up on a trapped treasure")) {
                    sawGiveUp = true;
                    break;
                }
            }
        }
        assertThat(sawGiveUp).isTrue();
    }

    @Test
    void allShieldedPartyStillMakesProgressInsteadOfPermanentDarkness() {
        // Regression: PC + retainers all Fighter (sword+shield) means nobody in the party has a free
        // hand for the torch. Without dropping someone's shield, every delve retreats in darkness on
        // step one — before a single turn passes — forever, silently reporting "Nothing noteworthy"
        // and zero XP/gold every time. (A delve legitimately *can* still run its torches dry near the
        // end of a long run — that's fine; the bug this guards is *zero turns elapsing at all*.)
        for (long seed = 1; seed <= 10; seed++) {
            Dice dice = new Dice(new Random(seed));
            AutodelveService service = newService(dice);
            SaveGame save = fighterSave(dice);
            RetainerFactory retainerFactory = new RetainerFactory(dice);
            save.getRetainers().add(retainerFactory.create("Petra", CharacterClass.FIGHTER, 1, 9));
            save.getRetainers().add(retainerFactory.create("Holt", CharacterClass.FIGHTER, 1, 9));

            service.run(save, 1); // single delve

            assertThat(save.getSession().getDungeonTurn()).as("seed " + seed).isGreaterThan(0);
        }
    }

    @Test
    void everyEncounterIsLoggedWithItsOutcome() {
        // Across enough seeds, a level-1 fighter's single delves should show an encounter line (either
        // a stocked "Encountered" or a random "A wandering" one — every engaged fight logs one of the
        // two) and a "Defeated" line (a fight actually won, not fled or died).
        boolean sawEncounter = false;
        boolean sawDefeated = false;
        for (long seed = 1; seed <= 40 && !(sawEncounter && sawDefeated); seed++) {
            Dice dice = new Dice(new Random(seed));
            AutodelveService service = newService(dice);
            SaveGame save = fighterSave(dice);

            AutodelveService.Result result = service.run(save, 1); // single delve

            for (String line : result.log()) {
                if (line.contains("Encountered ") || line.contains("A wandering ")) {
                    sawEncounter = true;
                }
                if (line.contains("Defeated ")) {
                    sawDefeated = true;
                }
            }
        }
        assertThat(sawEncounter).isTrue();
        assertThat(sawDefeated).isTrue();
    }

    @Test
    void verboseLogDetailCapturesFullNarrationInsteadOfCuratedMilestones() {
        Dice dice = new Dice(new Random(45));
        AutodelveService service = newService(dice);
        SaveGame save = fighterSave(dice);

        AutodelveService.Result verbose =
                service.run(save, 1, AutodelveService.Pace.BX_OSE, AutodelveService.LogDetail.VERBOSE);

        // Verbose mode should never emit the curated milestone phrasing...
        assertThat(verbose.log()).noneMatch(line -> line.contains("Encountered ") || line.contains("Defeated "));
        // ...but should be substantially longer than a single "Delve N:"/"Nothing noteworthy" pair, since
        // it captures every action's full narration instead.
        assertThat(verbose.log().size()).isGreaterThan(2);
    }

    @Test
    void verboseLogDetailShowsTheRawXpBreakdown() {
        boolean sawXpBreakdown = false;
        for (long seed = 1; seed <= 40; seed++) {
            Dice dice = new Dice(new Random(seed));
            AutodelveService service = newService(dice);
            SaveGame save = fighterSave(dice);
            AutodelveService.Result verbose =
                    service.run(save, 1, AutodelveService.Pace.BX_OSE, AutodelveService.LogDetail.VERBOSE);
            if (verbose.log().stream().anyMatch(l -> l.contains("Gained **"))) {
                sawXpBreakdown = true;
                break;
            }
        }
        assertThat(sawXpBreakdown).isTrue();
    }

    @Test
    void milestonesLogDetailNeverShowsTheRawXpBreakdown() {
        for (long seed = 1; seed <= 20; seed++) {
            Dice dice = new Dice(new Random(seed));
            AutodelveService service = newService(dice);
            SaveGame save = fighterSave(dice);

            AutodelveService.Result result = service.run(save, 1); // default MILESTONES

            assertThat(result.log()).noneMatch(line -> line.contains("Gained **"));
        }
    }

    @Test
    void defaultLogDetailIsMilestonesForBackwardCompatibleOverloads() {
        Dice dice = new Dice(new Random(46));
        AutodelveService service = newService(dice);
        SaveGame save = fighterSave(dice);

        AutodelveService.Result result = service.run(save, 1); // two-arg overload, no LogDetail
        boolean hasCuratedShape = result.log().stream()
                .anyMatch(line -> line.equals("Delve 1:") || line.contains("Nothing noteworthy")
                        || line.contains("Encountered ") || line.contains("A wandering ")
                        || line.contains("Defeated ") || line.contains("Recovered "));
        assertThat(hasCuratedShape).isTrue();
    }

    @Test
    void fastPaceCadenceIsRoughlyThreeToFourDelvesPerLevel() {
        // A fighter from level 1: reaching level 4 should take on the order of ~9-12 delves, not 1 or 100.
        Dice dice = new Dice(new Random(42));
        AutodelveService service = newService(dice);
        SaveGame save = fighterSave(dice);

        AutodelveService.Result result = service.run(save, 4, AutodelveService.Pace.FAST);

        if (result.outcome() == AutodelveService.Outcome.REACHED_TARGET) {
            // 3 levels gained (1->4) at 3-4 delves each ≈ 9-12; allow slack for organic XP and variance.
            assertThat(result.episodes()).isBetween(4, 20);
        }
    }

    // --- Multi-PC single-delve awareness -------------------------------------

    @Test
    void singleDelveDoesNotReportDeathWhenOnlyTheNonPrimaryPcSurvives() {
        // Regression: the episode loop used to stop (and run() reported DIED) the instant the primary
        // PC died, even if another PC was still alive and fighting. Give the primary essentially no HP
        // (dies to almost anything) and the second PC a huge HP pool (should easily survive); across
        // enough seeds, find one where the primary falls but the second PC doesn't, and confirm the run
        // does NOT report DIED in that case.
        boolean found = false;
        for (long seed = 1; seed <= 60 && !found; seed++) {
            Dice dice = new Dice(new Random(seed));
            AutodelveService service = newService(dice);
            SaveGame save = new SaveGame();
            Character fragile = new CharacterFactory(dice)
                    .create("Fragile", CharacterClass.MAGIC_USER, new AbilityScores(9, 9, 9, 9, 9, 9));
            fragile.setMaxHp(1);
            fragile.setCurrentHp(1);
            fragile.setHealingPotions(0);
            save.setCharacter(fragile);
            Character tank = new CharacterFactory(dice)
                    .create("Tank", CharacterClass.FIGHTER, new AbilityScores(16, 9, 9, 13, 13, 9));
            tank.setMaxHp(200);
            tank.setCurrentHp(200);
            save.addCharacter(tank);

            AutodelveService.Result result = service.run(save, 1); // single delve

            if (!fragile.isAlive() && tank.isAlive()) {
                found = true;
                assertThat(result.outcome()).as("seed " + seed).isNotEqualTo(AutodelveService.Outcome.DIED);
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void weakestLivingPcHpTriggersRetreatEvenWhenThePrimaryPcIsHealthy() {
        // Regression: the retreat-to-town check used to look only at the primary PC's HP. Anna starts
        // at full health; Bram starts already at exactly half -- the retreat check fires before any
        // exploring happens, so this is deterministic regardless of seed.
        Dice dice = new Dice(new Random(7));
        AutodelveService service = newService(dice);
        SaveGame save = new SaveGame();
        Character anna = new CharacterFactory(dice)
                .create("Anna", CharacterClass.FIGHTER, new AbilityScores(13, 9, 9, 12, 13, 9));
        anna.setMaxHp(60);
        anna.setCurrentHp(60);
        save.setCharacter(anna);
        Character bram = new CharacterFactory(dice)
                .create("Bram", CharacterClass.CLERIC, new AbilityScores(9, 9, 9, 9, 9, 9));
        bram.setMaxHp(20);
        bram.setCurrentHp(10); // exactly half -- triggers retreat on its own
        save.addCharacter(bram);

        service.run(save, 1); // single delve

        assertThat(save.getSession().getState()).isEqualTo(SessionState.IN_TOWN);
    }

    @Test
    void eachLivingPcQuaffsTheirOwnPotionWhenBadlyHurtNotJustThePrimary() {
        // Regression: fightStep used to quaff only from the primary PC's stash. Two modestly-tough
        // fighters with plenty of potions are likely to have at least one of them (not necessarily the
        // primary) drop below the 1/4-hp quaff threshold across enough seeds of real combat.
        boolean sawNonPrimaryQuaff = false;
        for (long seed = 1; seed <= 60 && !sawNonPrimaryQuaff; seed++) {
            Dice dice = new Dice(new Random(seed));
            AutodelveService service = newService(dice);
            SaveGame save = new SaveGame();
            Character anna = new CharacterFactory(dice)
                    .create("Anna", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 13, 13, 9));
            anna.setMaxHp(12);
            anna.setCurrentHp(12);
            anna.setHealingPotions(3);
            save.setCharacter(anna);
            Character bram = new CharacterFactory(dice)
                    .create("Bram", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 13, 13, 9));
            bram.setMaxHp(12);
            bram.setCurrentHp(12);
            bram.setHealingPotions(3);
            save.addCharacter(bram);

            service.run(save, 1); // single delve

            if (bram.getHealingPotions() < 3) {
                sawNonPrimaryQuaff = true;
            }
        }
        assertThat(sawNonPrimaryQuaff).isTrue();
    }

    @Test
    void handFreedomCheckSeesEveryLivingPcNotJustThePrimary() {
        // Regression: the "does anyone already have a free hand for the torch" detection used to check
        // only the primary PC plus retainers -- it would wrongly strip the primary's shield even when a
        // second PC already had a free hand.
        Dice dice = new Dice(new Random(3));
        AutodelveService service = newService(dice);
        SaveGame save = new SaveGame();
        Character anna = new CharacterFactory(dice)
                .create("Anna", CharacterClass.FIGHTER, new AbilityScores(13, 9, 9, 12, 13, 9));
        anna.setMainWeapon("Sword");
        anna.setShield(true); // sword + shield: no free hand on her own
        save.setCharacter(anna);
        Character bram = new CharacterFactory(dice)
                .create("Bram", CharacterClass.THIEF, new AbilityScores(9, 13, 9, 9, 9, 9));
        bram.setMainWeapon("Sword");
        bram.setShield(false); // one-handed weapon, no shield: already has a free hand
        save.addCharacter(bram);

        service.run(save, 1); // single delve

        assertThat(anna.isShield()).isTrue(); // never stripped -- Bram already had a free hand
    }
}
