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

/** {@link DismissCommand#canDismiss} -- the owner-restriction check behind {@code /dismiss}. No test
    here constructs a {@link dev.freitas.delve.discord.CommandContext}, matching this codebase's
    established pattern of testing small extracted pure logic directly. */
class DismissCommandTest {

    private final Dice dice = new Dice(new Random(51));
    private final RetainerFactory retainerFactory = new RetainerFactory(dice);

    @Test
    void soloSaveAlwaysPermitsDismissal() {
        SaveGame save = new SaveGame();
        Character solo = pc("Hero");
        save.setCharacter(solo);
        Retainer hench = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 9); // owner unset

        assertThat(DismissCommand.canDismiss(save, solo, hench)).isTrue();
    }

    @Test
    void multiPcOwnerMayDismissTheirOwnRetainer() {
        SaveGame save = new SaveGame();
        Character anna = pc("Anna");
        save.setCharacter(anna);
        Character bram = pc("Bram");
        save.addCharacter(bram);
        Retainer hench = retainerFactory.create("AnnaHench", CharacterClass.FIGHTER, 1, 9);
        hench.setOwner("Anna");

        assertThat(DismissCommand.canDismiss(save, anna, hench)).isTrue();
    }

    @Test
    void multiPcNonOwnerMayNotDismissAnotherPcsRetainer() {
        SaveGame save = new SaveGame();
        Character anna = pc("Anna");
        save.setCharacter(anna);
        Character bram = pc("Bram");
        save.addCharacter(bram);
        Retainer hench = retainerFactory.create("AnnaHench", CharacterClass.FIGHTER, 1, 9);
        hench.setOwner("Anna");

        assertThat(DismissCommand.canDismiss(save, bram, hench)).isFalse();
    }

    @Test
    void wholeTextMatchingAMultiWordRetainerNameIsNotSplitByALeadingPcNameMatch() {
        // Regression: a retainer named "Bram the Bold" (multi-word names are possible via /hire's
        // free-form name argument) must not be misparsed as "PC Bram, retainer 'the Bold'" just because
        // a PC also happens to be named Bram.
        SaveGame save = new SaveGame();
        Character anna = pc("Anna");
        save.setCharacter(anna);
        Character bram = pc("Bram");
        save.addCharacter(bram);
        Retainer hench = retainerFactory.create("Bram the Bold", CharacterClass.FIGHTER, 1, 9);
        hench.setOwner("Anna");
        save.getRetainers().add(hench);

        SaveGame.PcNameToken resolved = DismissCommand.resolveActorAndName(save, "Bram the Bold");

        assertThat(resolved.remainder()).isEqualTo("Bram the Bold");
        assertThat(resolved.actor()).isSameAs(anna); // defaults to the primary PC, not the peeled "Bram"
    }

    @Test
    void leadingPcNameIsPeeledWhenTheWholeTextIsNotARetainerName() {
        SaveGame save = new SaveGame();
        Character anna = pc("Anna");
        save.setCharacter(anna);
        Character bram = pc("Bram");
        save.addCharacter(bram);
        Retainer hench = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 9);
        hench.setOwner("Bram");
        save.getRetainers().add(hench);

        SaveGame.PcNameToken resolved = DismissCommand.resolveActorAndName(save, "Bram Bryn");

        assertThat(resolved.actor()).isSameAs(bram);
        assertThat(resolved.remainder()).isEqualTo("Bryn");
    }

    @Test
    void noLeadingPcNameDefaultsToThePrimaryPcAndKeepsTheWholeText() {
        SaveGame save = new SaveGame();
        Character solo = pc("Hero");
        save.setCharacter(solo);

        SaveGame.PcNameToken resolved = DismissCommand.resolveActorAndName(save, "Bryn");

        assertThat(resolved.actor()).isSameAs(solo);
        assertThat(resolved.remainder()).isEqualTo("Bryn");
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
