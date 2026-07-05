package dev.freitas.delve.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.RetainerFactory;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@link RollCharacterCommand#nameCollides} -- the check behind rejecting a second+ PC whose name
    would collide with an existing party member's, which would otherwise silently break name-based
    ownership lookups ({@link SaveGame#ownerOf}). No test here constructs a
    {@link dev.freitas.delve.discord.CommandContext}, matching this codebase's established pattern of
    testing small extracted pure logic directly. */
class RollCharacterCommandTest {

    private final Dice dice = new Dice(new Random(61));
    private final RetainerFactory retainerFactory = new RetainerFactory(dice);

    @Test
    void noCollisionForAFreshSave() {
        SaveGame save = new SaveGame();
        assertThat(RollCharacterCommand.nameCollides(save, "Anna")).isFalse();
    }

    @Test
    void collidesWithAnExistingPcNameCaseInsensitively() {
        SaveGame save = new SaveGame();
        save.setCharacter(pc("Anna"));

        assertThat(RollCharacterCommand.nameCollides(save, "anna")).isTrue();
        assertThat(RollCharacterCommand.nameCollides(save, "Bram")).isFalse();
    }

    @Test
    void collidesWithAnExistingRetainerNameCaseInsensitively() {
        SaveGame save = new SaveGame();
        save.setCharacter(pc("Anna"));
        Retainer hench = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 9);
        save.getRetainers().add(hench);

        assertThat(RollCharacterCommand.nameCollides(save, "bryn")).isTrue();
    }

    private Character pc(String name) {
        Character c = new Character();
        c.setName(name);
        c.setCharacterClass(CharacterClass.FIGHTER);
        c.setAbilities(new AbilityScores(13, 9, 9, 12, 12, 12));
        c.setMaxHp(10);
        c.setCurrentHp(10);
        return c;
    }
}
