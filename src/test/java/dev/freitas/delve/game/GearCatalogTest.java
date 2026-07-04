package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.GearCatalog;
import org.junit.jupiter.api.Test;

class GearCatalogTest {

    @Test
    void pricesEveryItemGrantedByCharacterAndRetainerFactories() {
        // Weapons/gear CharacterFactory/RetainerFactory currently grant for free (or would after
        // switching to guided shopping).
        assertThat(GearCatalog.priceGp("Sword")).isEqualTo(10);
        assertThat(GearCatalog.priceGp("Battle axe")).isEqualTo(7);
        assertThat(GearCatalog.priceGp("Mace")).isEqualTo(5);
        assertThat(GearCatalog.priceGp("Dagger")).isEqualTo(3);
        assertThat(GearCatalog.priceGp("Short sword")).isEqualTo(8);
        assertThat(GearCatalog.priceGp("Thieves' tools")).isEqualTo(25);
        assertThat(GearCatalog.priceGp("Holy symbol (wooden)")).isEqualTo(25);
        assertThat(GearCatalog.priceGp("Spellbook")).isEqualTo(25);
        assertThat(GearCatalog.priceGp("Backpack")).isEqualTo(5);
        assertThat(GearCatalog.priceGp("Tinderbox")).isEqualTo(3);
        assertThat(GearCatalog.priceGp("Rations (1 week)")).isEqualTo(15);
        assertThat(GearCatalog.priceGp("Waterskin")).isEqualTo(1);
        assertThat(GearCatalog.priceGp("50' rope")).isEqualTo(1);
    }

    @Test
    void pricesCompoundWeaponAndAmmoBundles() {
        assertThat(GearCatalog.priceGp("Sling & 30 stones")).isEqualTo(2);
        assertThat(GearCatalog.priceGp("Short bow & 20 arrows")).isEqualTo(30);
    }

    @Test
    void pricesArmorTiersAndShield() {
        assertThat(GearCatalog.priceGp("Leather")).isEqualTo(20);
        assertThat(GearCatalog.priceGp("Chain mail")).isEqualTo(60);
        assertThat(GearCatalog.priceGp("Plate mail")).isEqualTo(400);
        assertThat(GearCatalog.priceGp("Shield")).isEqualTo(10);
    }

    @Test
    void moreSpecificNeedlesWinOverShorterSubstrings() {
        // "Short sword" must not resolve to the plain "sword" price.
        assertThat(GearCatalog.priceGp("Short sword")).isNotEqualTo(GearCatalog.priceGp("Sword"));
    }

    @Test
    void unrecognizedItemsReturnNegativeOne() {
        assertThat(GearCatalog.priceGp("Wand of Wonder")).isEqualTo(-1);
        assertThat(GearCatalog.priceGp(null)).isEqualTo(-1);
    }
}
