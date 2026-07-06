package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.ContainerType;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.CombatEncounter;
import dev.freitas.delve.game.model.Container;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.ContainerService;
import dev.freitas.delve.game.session.ExplorationResult;
import org.junit.jupiter.api.Test;

/** {@link ContainerService}: a held sack is the lowest-priority claim on a combatant's two hands, so it
    drops (rather than blocking a weapon/shield) the moment the fight starts and their existing loadout
    already fills both -- returned to them on victory, lost for good on a successful flee. */
class ContainerServiceTest {

    private final ContainerService containers = new ContainerService();

    @Test
    void heldSackDropsWhenAlreadyCommittedGearFillsBothHands() {
        SaveGame save = new SaveGame();
        Character pc = bareFighter("Conan");
        pc.setMainWeapon("Long bow"); // two-handed, already fills both hands
        Container heldSack = new Container(ContainerType.SMALL_SACK, true, "Conan");
        heldSack.getItems().add("Gems");
        pc.setContainers(new java.util.ArrayList<>(java.util.List.of(heldSack)));
        save.setCharacter(pc);
        save.getSession().setCombat(new CombatEncounter());

        ExplorationResult result = new ExplorationResult();
        containers.reconcileHeldContainers(save, result);

        assertThat(pc.getContainers()).isEmpty();
        assertThat(save.getSession().getCombat().getDroppedContainers()).containsExactly(heldSack);
        assertThat(result.text()).contains("drops the small sack");
    }

    @Test
    void heldSackStaysWhenAHandIsStillFree() {
        SaveGame save = new SaveGame();
        Character pc = bareFighter("Conan");
        pc.setMainWeapon("Dagger"); // one-handed, one hand still free
        Container heldSack = new Container(ContainerType.SMALL_SACK, true, "Conan");
        pc.setContainers(new java.util.ArrayList<>(java.util.List.of(heldSack)));
        save.setCharacter(pc);
        save.getSession().setCombat(new CombatEncounter());

        ExplorationResult result = new ExplorationResult();
        containers.reconcileHeldContainers(save, result);

        assertThat(pc.getContainers()).containsExactly(heldSack);
        assertThat(save.getSession().getCombat().getDroppedContainers()).isEmpty();
        assertThat(result.text()).isBlank();
    }

    @Test
    void wornContainersAreNeverDroppedRegardlessOfHandBudget() {
        SaveGame save = new SaveGame();
        Character pc = bareFighter("Conan");
        pc.setMainWeapon("Long bow");
        pc.setShield(true); // already over budget without any sack at all
        Container backpack = new Container(ContainerType.BACKPACK, false, "Conan");
        pc.setContainers(new java.util.ArrayList<>(java.util.List.of(backpack)));
        save.setCharacter(pc);
        save.getSession().setCombat(new CombatEncounter());

        ExplorationResult result = new ExplorationResult();
        containers.reconcileHeldContainers(save, result);

        assertThat(pc.getContainers()).containsExactly(backpack);
        assertThat(save.getSession().getCombat().getDroppedContainers()).isEmpty();
    }

    @Test
    void aRetainersHeldSackAlsoDropsWhenTheirHandsAreFull() {
        SaveGame save = new SaveGame();
        Character pc = bareFighter("Conan");
        save.setCharacter(pc);
        Retainer r = new Retainer();
        r.setName("Bryn");
        r.setMainWeapon("Long bow");
        r.setCurrentHp(10);
        r.setMaxHp(10);
        Container heldSack = new Container(ContainerType.SMALL_SACK, true, "Bryn");
        r.setContainers(new java.util.ArrayList<>(java.util.List.of(heldSack)));
        save.getRetainers().add(r);
        save.getSession().setCombat(new CombatEncounter());

        ExplorationResult result = new ExplorationResult();
        containers.reconcileHeldContainers(save, result);

        assertThat(r.getContainers()).isEmpty();
        assertThat(save.getSession().getCombat().getDroppedContainers()).containsExactly(heldSack);
    }

    @Test
    void victoryReturnsDroppedContainersToTheirOwner() {
        SaveGame save = new SaveGame();
        Character pc = bareFighter("Conan");
        pc.setContainers(new java.util.ArrayList<>());
        save.setCharacter(pc);
        CombatEncounter encounter = new CombatEncounter();
        Container heldSack = new Container(ContainerType.SMALL_SACK, true, "Conan");
        heldSack.getItems().add("Gems");
        encounter.getDroppedContainers().add(heldSack);
        save.getSession().setCombat(encounter);

        containers.returnDroppedContainers(save, encounter);

        assertThat(pc.getContainers()).containsExactly(heldSack);
        assertThat(encounter.getDroppedContainers()).isEmpty();
    }

    @Test
    void fleeingDiscardsDroppedContainersAndReportsWhatWasLost() {
        SaveGame save = new SaveGame();
        Character pc = bareFighter("Conan");
        pc.setContainers(new java.util.ArrayList<>());
        save.setCharacter(pc);
        CombatEncounter encounter = new CombatEncounter();
        Container heldSack = new Container(ContainerType.SMALL_SACK, true, "Conan");
        heldSack.getItems().add("Gems");
        encounter.getDroppedContainers().add(heldSack);
        save.getSession().setCombat(encounter);

        ExplorationResult result = new ExplorationResult();
        containers.discardDroppedContainers(save, encounter, result);

        assertThat(pc.getContainers()).isEmpty(); // not returned -- lost for good
        assertThat(encounter.getDroppedContainers()).isEmpty();
        assertThat(result.text()).contains("Gems").contains("small sack");
    }

    @Test
    void fleeingSaysNothingAboutAnEmptyDroppedContainer() {
        SaveGame save = new SaveGame();
        Character pc = bareFighter("Conan");
        save.setCharacter(pc);
        CombatEncounter encounter = new CombatEncounter();
        encounter.getDroppedContainers().add(new Container(ContainerType.SMALL_SACK, true, "Conan"));
        save.getSession().setCombat(encounter);

        ExplorationResult result = new ExplorationResult();
        containers.discardDroppedContainers(save, encounter, result);

        assertThat(result.text()).isBlank();
    }

    private Character bareFighter(String name) {
        Character c = new Character();
        c.setName(name);
        c.setCharacterClass(CharacterClass.FIGHTER);
        c.setAbilities(new AbilityScores(12, 12, 12, 12, 12, 12));
        c.setMaxHp(10);
        c.setCurrentHp(10);
        return c;
    }
}
