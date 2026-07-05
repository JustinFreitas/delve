package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * {@link SaveGame#ownerOf}/{@link SaveGame#retainersOwnedBy} — the lookup behind persistent retainer
 * ownership, including the legacy-save fallback (a retainer persisted before the {@code owner} field
 * existed, or whose stored owner name no longer matches any current PC, resolves to the primary PC).
 */
class SaveGameTest {

    private final Dice dice = new Dice(new Random(41));
    private final CharacterFactory factory = new CharacterFactory(dice);
    private final RetainerFactory retainerFactory = new RetainerFactory(dice);

    @Test
    void ownerOfFallsBackToPrimaryPcWhenRetainerHasNoStoredOwner() {
        SaveGame save = new SaveGame();
        Character anna = factory.create("Anna", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
        save.setCharacter(anna);
        Retainer legacy = retainerFactory.create("Legacy", CharacterClass.FIGHTER, 1, 9);
        save.getRetainers().add(legacy); // never had setOwner called, as if persisted before the field existed

        assertThat(save.ownerOf(legacy)).isSameAs(anna);
    }

    @Test
    void ownerOfResolvesTheStoredOwnerName() {
        SaveGame save = new SaveGame();
        save.setCharacter(factory.create("Anna", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9)));
        Character bram = factory.create("Bram", CharacterClass.CLERIC, new AbilityScores(9, 9, 9, 9, 9, 12));
        save.addCharacter(bram);
        Retainer hench = retainerFactory.create("Hench", CharacterClass.CLERIC, 1, 9);
        hench.setOwner("Bram");
        save.getRetainers().add(hench);

        assertThat(save.ownerOf(hench)).isSameAs(bram);
    }

    @Test
    void ownerOfFallsBackToPrimaryPcWhenStoredOwnerNoLongerMatchesAnyPc() {
        SaveGame save = new SaveGame();
        Character anna = factory.create("Anna", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
        save.setCharacter(anna);
        Retainer hench = retainerFactory.create("Hench", CharacterClass.FIGHTER, 1, 9);
        hench.setOwner("Nobody");
        save.getRetainers().add(hench);

        assertThat(save.ownerOf(hench)).isSameAs(anna);
    }

    @Test
    void retainersOwnedByPartitionsEveryRetainerExactlyOnce() {
        SaveGame save = new SaveGame();
        Character anna = factory.create("Anna", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
        save.setCharacter(anna);
        Character bram = factory.create("Bram", CharacterClass.CLERIC, new AbilityScores(9, 9, 9, 9, 9, 12));
        save.addCharacter(bram);

        Retainer annaHench = retainerFactory.create("AnnaHench", CharacterClass.FIGHTER, 1, 9);
        annaHench.setOwner("Anna");
        Retainer bramHench = retainerFactory.create("BramHench", CharacterClass.CLERIC, 1, 9);
        bramHench.setOwner("Bram");
        Retainer legacy = retainerFactory.create("Legacy", CharacterClass.FIGHTER, 1, 9); // no owner set
        save.getRetainers().add(annaHench);
        save.getRetainers().add(bramHench);
        save.getRetainers().add(legacy);

        assertThat(save.retainersOwnedBy(anna)).containsExactlyInAnyOrder(annaHench, legacy);
        assertThat(save.retainersOwnedBy(bram)).containsExactly(bramHench);
        int total = save.retainersOwnedBy(anna).size() + save.retainersOwnedBy(bram).size();
        assertThat(total).isEqualTo(save.getRetainers().size());
    }

    @Test
    void atRetainerCapIsFalseUntilThatPcsOwnCapIsReachedThenTrue() {
        SaveGame save = new SaveGame();
        Character lowCha = factory.create("Anna", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 3)); // cap 1
        save.setCharacter(lowCha);

        assertThat(save.retainerCapFor(lowCha)).isEqualTo(1);
        assertThat(save.atRetainerCap(lowCha)).isFalse();

        Retainer hench = retainerFactory.create("Hench", CharacterClass.FIGHTER, 1, 9);
        hench.setOwner("Anna");
        save.getRetainers().add(hench);

        assertThat(save.atRetainerCap(lowCha)).isTrue();
    }

    @Test
    void atRetainerCapIsPerOwnerNotPerParty() {
        // One PC sitting at their own cap must not block a different PC, who owns none, from hiring.
        SaveGame save = new SaveGame();
        Character anna = factory.create("Anna", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 3)); // cap 1
        save.setCharacter(anna);
        Character bram = factory.create("Bram", CharacterClass.CLERIC, new AbilityScores(9, 9, 9, 9, 9, 18)); // cap 7
        save.addCharacter(bram);
        Retainer annaHench = retainerFactory.create("AnnaHench", CharacterClass.FIGHTER, 1, 9);
        annaHench.setOwner("Anna");
        save.getRetainers().add(annaHench);

        assertThat(save.atRetainerCap(anna)).isTrue();
        assertThat(save.atRetainerCap(bram)).isFalse();
    }

    @Test
    void peelLeadingPcNameExtractsALivePcAndTheRemainder() {
        SaveGame save = new SaveGame();
        save.setCharacter(factory.create("Anna", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9)));
        Character bram = factory.create("Bram", CharacterClass.CLERIC, new AbilityScores(9, 9, 9, 9, 9, 12));
        save.addCharacter(bram);

        SaveGame.PcNameToken token = save.peelLeadingPcName("Bram fighter Conan", save.getCharacter());

        assertThat(token.actor()).isSameAs(bram);
        assertThat(token.remainder()).isEqualTo("fighter Conan");
    }

    @Test
    void peelLeadingPcNameFallsBackToDefaultActorWhenNoLeadingTokenResolves() {
        SaveGame save = new SaveGame();
        Character anna = factory.create("Anna", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
        save.setCharacter(anna);

        SaveGame.PcNameToken token = save.peelLeadingPcName("fighter Conan", anna);

        assertThat(token.actor()).isSameAs(anna);
        assertThat(token.remainder()).isEqualTo("fighter Conan");
    }
}
