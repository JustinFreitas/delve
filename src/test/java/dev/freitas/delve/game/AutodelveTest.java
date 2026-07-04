package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.AutodelveService;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.LightingService;
import dev.freitas.delve.game.session.ExplorationService;
import dev.freitas.delve.game.session.SpellService;
import dev.freitas.delve.game.session.TownService;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AutodelveTest {

    private AutodelveService newService(Dice dice) {
        SpellService spells = new SpellService(dice);
        CombatService combat = new CombatService(dice, spells);
        ExplorationService exploration = new ExplorationService(dice, new DungeonGenerator(dice), combat, new LightingService());
        TownService town = new TownService(spells, dice);
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
            assertThat(result.episodes()).isBetween(6, 20);
        }
    }
}
