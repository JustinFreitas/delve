package dev.freitas.delve.game.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.CharacterFactory;
import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ExplorationServiceTest {

    private final Dice dice = new Dice(new Random(1));
    private final ExplorationService service = new ExplorationService(
            dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService());
    private final CharacterFactory factory = new CharacterFactory(dice);

    @Test
    void partySizeCountsEveryLivingPcAndRetainerNotJustOne() {
        SaveGame save = new SaveGame();
        save.setCharacter(factory.create("First", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9)));
        save.addCharacter(factory.create("Second", CharacterClass.CLERIC, new AbilityScores(9, 9, 9, 9, 9, 9)));
        Character deadPc = factory.create("Dead", CharacterClass.THIEF, new AbilityScores(9, 9, 9, 9, 9, 9));
        deadPc.setCurrentHp(0);
        save.addCharacter(deadPc); // dead -- must not count

        Retainer aliveRetainer = new Retainer();
        aliveRetainer.setName("Alive");
        aliveRetainer.setCharacterClass(CharacterClass.FIGHTER);
        aliveRetainer.setMaxHp(10);
        aliveRetainer.setCurrentHp(10);
        save.getRetainers().add(aliveRetainer);
        Retainer deadRetainer = new Retainer();
        deadRetainer.setName("Fallen");
        deadRetainer.setCharacterClass(CharacterClass.FIGHTER);
        deadRetainer.setMaxHp(10);
        deadRetainer.setCurrentHp(0);
        save.getRetainers().add(deadRetainer); // dead -- must not count

        // The old (buggy) formula `1 + livingRetainers().size()` would have said 2 here (assuming only
        // one PC); the correct count is 2 living PCs + 1 living retainer.
        assertThat(service.partySize(save)).isEqualTo(3);
    }
}
