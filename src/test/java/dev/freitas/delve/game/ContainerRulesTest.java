package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.ContainerRules;
import dev.freitas.delve.game.engine.ContainerType;
import dev.freitas.delve.game.model.Container;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** gygax75-rules' container/stowage rules: exempt on-person items, per-container capacity, auto-assign
    fill order, held-count, and the worn-slot cap ({@link ContainerRules}). */
class ContainerRulesTest {

    // --- isExempt --------------------------------------------------------------

    @Test
    void namedExceptionsNeverNeedAContainer() {
        assertThat(ContainerRules.isExempt("Waterskin")).isTrue();
        assertThat(ContainerRules.isExempt("Holy symbol (wooden)")).isTrue();
        assertThat(ContainerRules.isExempt("Quiver")).isTrue();
        assertThat(ContainerRules.isExempt("Bolt case")).isTrue();
    }

    @Test
    void everythingElseNeedsAContainer() {
        assertThat(ContainerRules.isExempt("Dagger")).isFalse();
        assertThat(ContainerRules.isExempt("Rope")).isFalse();
        assertThat(ContainerRules.isExempt(null)).isFalse();
    }

    // --- capacityRemaining -------------------------------------------------------

    @Test
    void capacityRemainingSubtractsItemWeightFromTypeCapacity() {
        Container backpack = new Container(ContainerType.BACKPACK, false, "Conan");
        assertThat(ContainerRules.capacityRemaining(backpack)).isEqualTo(400);

        backpack.getItems().add("Rope"); // 50cn
        assertThat(ContainerRules.capacityRemaining(backpack)).isEqualTo(350);
    }

    @Test
    void capacityRemainingNeverGoesNegative() {
        Container smallSack = new Container(ContainerType.SMALL_SACK, false, "Conan"); // 200cn
        smallSack.getItems().add("Chain mail"); // 400cn, overflows on its own
        assertThat(ContainerRules.capacityRemaining(smallSack)).isZero();
    }

    // --- findRoomFor --------------------------------------------------------------

    @Test
    void findRoomForPrefersWornContainersThenBackpackOverAWornSack() {
        Container heldLargeSack = new Container(ContainerType.LARGE_SACK, true, "Conan");
        Container wornSack = new Container(ContainerType.SMALL_SACK, false, "Conan");
        Container backpack = new Container(ContainerType.BACKPACK, false, "Conan");
        List<Container> containers = new ArrayList<>(List.of(heldLargeSack, wornSack, backpack));

        assertThat(ContainerRules.findRoomFor(containers, 10)).isSameAs(backpack);
    }

    @Test
    void findRoomForFallsBackToAHeldSackWhenWornContainersAreFull() {
        Container backpack = new Container(ContainerType.BACKPACK, false, "Conan");
        backpack.getItems().add("Chain mail"); // 400cn, fills the 400cn backpack completely
        Container heldSack = new Container(ContainerType.SMALL_SACK, true, "Conan");
        List<Container> containers = new ArrayList<>(List.of(backpack, heldSack));

        assertThat(ContainerRules.findRoomFor(containers, 10)).isSameAs(heldSack);
    }

    @Test
    void findRoomForReturnsNullWhenNothingFits() {
        Container smallSack = new Container(ContainerType.SMALL_SACK, false, "Conan"); // 200cn
        smallSack.getItems().add("Chain mail"); // fills it past capacity already
        assertThat(ContainerRules.findRoomFor(List.of(smallSack), 10)).isNull();
        assertThat(ContainerRules.findRoomFor(List.of(), 1)).isNull();
    }

    // --- heldCount --------------------------------------------------------------

    @Test
    void heldCountOnlyCountsHeldNotWornContainers() {
        Container worn = new Container(ContainerType.BACKPACK, false, "Conan");
        Container held1 = new Container(ContainerType.SMALL_SACK, true, "Conan");
        Container held2 = new Container(ContainerType.LARGE_SACK, true, "Conan");

        assertThat(ContainerRules.heldCount(List.of(worn, held1, held2))).isEqualTo(2);
        assertThat(ContainerRules.heldCount(List.of(worn))).isZero();
        assertThat(ContainerRules.heldCount(List.of())).isZero();
    }

    // --- canWearAnother -----------------------------------------------------------

    @Test
    void canWearAnotherBackpackOnlyWithNoneAlreadyWorn() {
        assertThat(ContainerRules.canWearAnother(List.of(), ContainerType.BACKPACK)).isTrue();

        Container wornBackpack = new Container(ContainerType.BACKPACK, false, "Conan");
        assertThat(ContainerRules.canWearAnother(List.of(wornBackpack), ContainerType.BACKPACK)).isFalse();
        // A held (not worn) backpack doesn't block wearing a fresh one.
        Container heldBackpack = new Container(ContainerType.BACKPACK, true, "Conan");
        assertThat(ContainerRules.canWearAnother(List.of(heldBackpack), ContainerType.BACKPACK)).isTrue();
    }

    @Test
    void canWearAnotherSmallSackIsIndependentOfTheBackpackSlot() {
        Container wornBackpack = new Container(ContainerType.BACKPACK, false, "Conan");
        assertThat(ContainerRules.canWearAnother(List.of(wornBackpack), ContainerType.SMALL_SACK)).isTrue();

        Container wornSack = new Container(ContainerType.SMALL_SACK, false, "Conan");
        assertThat(ContainerRules.canWearAnother(List.of(wornBackpack, wornSack), ContainerType.SMALL_SACK)).isFalse();
    }

    @Test
    void aLargeSackCanNeverBeWorn() {
        assertThat(ContainerRules.canWearAnother(List.of(), ContainerType.LARGE_SACK)).isFalse();
    }
}
