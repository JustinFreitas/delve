package dev.freitas.delve.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.Armor;
import org.junit.jupiter.api.Test;

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
}
