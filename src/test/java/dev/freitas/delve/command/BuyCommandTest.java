package dev.freitas.delve.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.ContainerType;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Container;
import dev.freitas.delve.game.model.SaveGame;
import org.junit.jupiter.api.Test;

/** No test here constructs a {@link dev.freitas.delve.discord.CommandContext}, matching this codebase's
    established pattern of testing small extracted pure logic directly (see {@code MuleCommandTest}). */
class BuyCommandTest {

    private final BuyCommand buyCommand = new BuyCommand();

    @Test
    void resolvesArmorTiersCaseInsensitively() {
        assertThat(buyCommand.armorTierFor("plate mail")).isEqualTo(Armor.PLATE_MAIL);
        assertThat(buyCommand.armorTierFor("platemail")).isEqualTo(Armor.PLATE_MAIL);
        assertThat(buyCommand.armorTierFor("chain mail")).isEqualTo(Armor.CHAIN_MAIL);
        assertThat(buyCommand.armorTierFor("chainmail")).isEqualTo(Armor.CHAIN_MAIL);
        assertThat(buyCommand.armorTierFor("leather")).isEqualTo(Armor.LEATHER);
        assertThat(buyCommand.armorTierFor("leather armor")).isEqualTo(Armor.LEATHER);
    }

    @Test
    void nonArmorItemsResolveToNull() {
        assertThat(buyCommand.armorTierFor("sword")).isNull();
        assertThat(buyCommand.armorTierFor("torch")).isNull();
        assertThat(buyCommand.armorTierFor("shield")).isNull(); // shield is its own keyword, not an armor tier
    }

    @Test
    void resolvesContainerTypesCaseInsensitively() {
        assertThat(buyCommand.containerTypeFor("backpack")).isEqualTo(ContainerType.BACKPACK);
        assertThat(buyCommand.containerTypeFor("small sack")).isEqualTo(ContainerType.SMALL_SACK);
        assertThat(buyCommand.containerTypeFor("large sack")).isEqualTo(ContainerType.LARGE_SACK);
        assertThat(buyCommand.containerTypeFor("sword")).isNull();
    }

    @Test
    void buyContainerWearsTheFirstBackpackAndSmallSackForFree() {
        SaveGame save = new SaveGame();
        Character pc = pc(100);
        save.setCharacter(pc);

        int bought = buyCommand.buyContainer(save, pc, ContainerType.BACKPACK, 1);

        assertThat(bought).isEqualTo(1);
        assertThat(pc.getContainers()).hasSize(1);
        assertThat(pc.getContainers().get(0).isHeld()).isFalse();
        assertThat(pc.getGold()).isEqualTo(100 - 5); // backpack is 5gp
    }

    @Test
    void buyContainerHoldsASecondSackIfAHandIsFree() {
        SaveGame save = new SaveGame();
        Character pc = pc(100);
        pc.setMainWeapon("Dagger"); // one-handed, one hand free
        save.setCharacter(pc);
        pc.getContainers().add(new Container(ContainerType.SMALL_SACK, false, pc.getName())); // worn slot taken

        int bought = buyCommand.buyContainer(save, pc, ContainerType.SMALL_SACK, 1);

        assertThat(bought).isEqualTo(1);
        assertThat(pc.getContainers()).hasSize(2);
        assertThat(pc.getContainers().get(1).isHeld()).isTrue();
    }

    @Test
    void buyContainerRefusesWhenNoHandOrGoldIsLeft() {
        SaveGame save = new SaveGame();
        Character pc = pc(100);
        pc.setMainWeapon("Long bow"); // two-handed, 0 free hands
        save.setCharacter(pc);
        pc.getContainers().add(new Container(ContainerType.SMALL_SACK, false, pc.getName())); // worn slot taken

        int bought = buyCommand.buyContainer(save, pc, ContainerType.SMALL_SACK, 1);

        assertThat(bought).isZero();
        assertThat(pc.getContainers()).hasSize(1); // unchanged
        assertThat(pc.getGold()).isEqualTo(100); // untouched
    }

    @Test
    void buyContainerStopsPartwayThroughAQtyWhenHandsRunOut() {
        SaveGame save = new SaveGame();
        Character pc = pc(100);
        pc.setMainWeapon("Dagger"); // one hand free -- room for exactly one held sack
        save.setCharacter(pc);

        int bought = buyCommand.buyContainer(save, pc, ContainerType.SMALL_SACK, 3);

        // First is worn (free), second is held (uses the one free hand), third has nowhere to go.
        assertThat(bought).isEqualTo(2);
        assertThat(pc.getContainers()).hasSize(2);
    }

    @Test
    void buyGearStowsANonExemptItemInTheFirstContainerWithRoom() {
        SaveGame save = new SaveGame();
        Character pc = pc(100);
        save.setCharacter(pc);
        Container backpack = new Container(ContainerType.BACKPACK, false, pc.getName());
        pc.getContainers().add(backpack);

        int bought = buyCommand.buyGear(save, pc, "Rope", 1);

        assertThat(bought).isEqualTo(1);
        assertThat(backpack.getItems()).containsExactly("Rope");
        assertThat(pc.getInventory()).isEmpty();
        assertThat(pc.getGold()).isEqualTo(100 - 1); // rope is 1gp
    }

    @Test
    void buyGearRoutesAnExemptItemToOnPersonInventoryInstead() {
        SaveGame save = new SaveGame();
        Character pc = pc(100);
        save.setCharacter(pc); // no containers at all

        int bought = buyCommand.buyGear(save, pc, "Waterskin", 1);

        assertThat(bought).isEqualTo(1);
        assertThat(pc.getInventory()).containsExactly("Waterskin");
        assertThat(pc.getContainers()).isEmpty();
    }

    @Test
    void buyGearRefusesANonExemptItemWithNowhereToPutIt() {
        SaveGame save = new SaveGame();
        Character pc = pc(100);
        save.setCharacter(pc); // no containers at all -- a rope needs one

        int bought = buyCommand.buyGear(save, pc, "Rope", 1);

        assertThat(bought).isZero();
        assertThat(pc.getGold()).isEqualTo(100); // untouched
    }

    @Test
    void buyGearReturnsZeroForAnUnrecognizedItem() {
        SaveGame save = new SaveGame();
        Character pc = pc(100);
        save.setCharacter(pc);

        assertThat(buyCommand.buyGear(save, pc, "Wand of Wonder", 1)).isZero();
    }

    private Character pc(int gold) {
        Character c = new Character();
        c.setName("Hero");
        c.setCharacterClass(CharacterClass.FIGHTER);
        c.setAbilities(new AbilityScores(9, 9, 9, 9, 9, 9));
        c.setMaxHp(10);
        c.setCurrentHp(10);
        c.setArmor(Armor.NONE);
        c.setShield(false);
        c.setMainWeapon("Sword");
        c.setGold(gold);
        return c;
    }
}
