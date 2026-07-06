package dev.freitas.delve.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.ContainerType;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Container;
import org.junit.jupiter.api.Test;

/** {@link InventoryMatcher}: finding and removing a player-typed item name regardless of whether it
    lives in the exempt on-person inventory list or one of the character's containers -- shared by
    {@code /wield}, {@code /sell}, and {@code /spike}. */
class InventoryMatcherTest {

    @Test
    void findsAnItemInTheExemptInventoryList() {
        Character c = bareCharacter();
        c.getInventory().add("Waterskin");

        assertThat(InventoryMatcher.find(c, "water")).isEqualTo("Waterskin");
    }

    @Test
    void findsAnItemInsideAContainer() {
        Character c = bareCharacter();
        Container backpack = new Container(ContainerType.BACKPACK, false, c.getName());
        backpack.getItems().add("Dagger");
        c.getContainers().add(backpack);

        assertThat(InventoryMatcher.find(c, "dag")).isEqualTo("Dagger");
    }

    @Test
    void searchesEveryContainerNotJustTheFirst() {
        Character c = bareCharacter();
        Container backpack = new Container(ContainerType.BACKPACK, false, c.getName());
        backpack.getItems().add("Rope");
        Container heldSack = new Container(ContainerType.SMALL_SACK, true, c.getName());
        heldSack.getItems().add("Dagger");
        c.getContainers().add(backpack);
        c.getContainers().add(heldSack);

        assertThat(InventoryMatcher.find(c, "dag")).isEqualTo("Dagger");
    }

    @Test
    void returnsNullWhenNothingMatchesAnywhere() {
        Character c = bareCharacter();
        c.getInventory().add("Waterskin");
        Container backpack = new Container(ContainerType.BACKPACK, false, c.getName());
        backpack.getItems().add("Rope");
        c.getContainers().add(backpack);

        assertThat(InventoryMatcher.find(c, "sword")).isNull();
    }

    @Test
    void removeDeletesFromTheExemptInventoryListWhenFoundThere() {
        Character c = bareCharacter();
        c.getInventory().add("Waterskin");

        InventoryMatcher.remove(c, "Waterskin");

        assertThat(c.getInventory()).isEmpty();
    }

    @Test
    void removeDeletesFromWhicheverContainerActuallyHoldsIt() {
        Character c = bareCharacter();
        Container backpack = new Container(ContainerType.BACKPACK, false, c.getName());
        backpack.getItems().add("Rope");
        Container heldSack = new Container(ContainerType.SMALL_SACK, true, c.getName());
        heldSack.getItems().add("Dagger");
        c.getContainers().add(backpack);
        c.getContainers().add(heldSack);

        InventoryMatcher.remove(c, "Dagger");

        assertThat(heldSack.getItems()).isEmpty();
        assertThat(backpack.getItems()).containsExactly("Rope"); // untouched
    }

    @Test
    void removeIsANoOpWhenTheItemIsntFoundAnywhere() {
        Character c = bareCharacter();
        c.getInventory().add("Waterskin");

        InventoryMatcher.remove(c, "Nonexistent Item");

        assertThat(c.getInventory()).containsExactly("Waterskin");
    }

    private Character bareCharacter() {
        Character c = new Character();
        c.setName("Hero");
        c.setCharacterClass(CharacterClass.FIGHTER);
        c.setAbilities(new AbilityScores(9, 9, 9, 9, 9, 9));
        c.setMaxHp(10);
        c.setCurrentHp(10);
        return c;
    }
}
