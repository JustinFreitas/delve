package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.AutodelveService;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.ExplorationService;
import dev.freitas.delve.game.session.SpellService;
import dev.freitas.delve.game.session.TownService;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AutodelveTest {

    private AutodelveService newService(Dice dice) {
        SpellService spells = new SpellService(dice);
        CombatService combat = new CombatService(dice, spells);
        ExplorationService exploration = new ExplorationService(dice, new DungeonGenerator(dice), combat);
        TownService town = new TownService(spells);
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
