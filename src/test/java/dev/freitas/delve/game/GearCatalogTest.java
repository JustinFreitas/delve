package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.GearCatalog;
import org.junit.jupiter.api.Test;

class GearCatalogTest {

    @Test
    void pricesEveryItemGrantedByCharacterAndRetainerFactories() {
        // Weapons/gear CharacterFactory/RetainerFactory currently grant for free (or would after
        // switching to guided shopping). Reconciled to gygax75-rules' own price table where a clean
        // name match exists (dagger/lance/short bow/chain mail/plate mail/rations all changed).
        assertThat(GearCatalog.priceGp("Sword")).isEqualTo(10);
        assertThat(GearCatalog.priceGp("Battle axe")).isEqualTo(7);
        assertThat(GearCatalog.priceGp("Mace")).isEqualTo(5);
        assertThat(GearCatalog.priceGp("Dagger")).isEqualTo(4);
        assertThat(GearCatalog.priceGp("Short sword")).isEqualTo(8); // no gygax75 entry, unchanged
        assertThat(GearCatalog.priceGp("Thieves' tools")).isEqualTo(25);
        assertThat(GearCatalog.priceGp("Holy symbol (wooden)")).isEqualTo(25);
        assertThat(GearCatalog.priceGp("Spellbook")).isEqualTo(25); // gygax75's 0gp not applied, see class javadoc
        assertThat(GearCatalog.priceGp("Backpack")).isEqualTo(5);
        assertThat(GearCatalog.priceGp("Tinderbox")).isEqualTo(3);
        assertThat(GearCatalog.priceGp("Rations (1 week)")).isEqualTo(7);
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
        assertThat(GearCatalog.priceGp("Chain mail")).isEqualTo(40);
        assertThat(GearCatalog.priceGp("Plate mail")).isEqualTo(60);
        assertThat(GearCatalog.priceGp("Shield")).isEqualTo(10);
    }

    @Test
    void moreSpecificNeedlesWinOverShorterSubstrings() {
        // "Short sword" must not resolve to the plain "sword" price.
        assertThat(GearCatalog.priceGp("Short sword")).isNotEqualTo(GearCatalog.priceGp("Sword"));
        // "Rations (1 week)" must not resolve to the generic per-unit "rations" price.
        assertThat(GearCatalog.priceGp("Rations (1 week)")).isNotEqualTo(GearCatalog.priceGp("Rations"));
    }

    @Test
    void unrecognizedItemsReturnNegativeOneForPriceAndZeroForWeight() {
        assertThat(GearCatalog.priceGp("Wand of Wonder")).isEqualTo(-1);
        assertThat(GearCatalog.priceGp(null)).isEqualTo(-1);
        assertThat(GearCatalog.weightCns("Wand of Wonder")).isZero();
        assertThat(GearCatalog.weightCns(null)).isZero();
    }

    // --- Weight (gygax75-rules: 1 coin = 1 cn) ---------------------------------

    @Test
    void weighsArmorTiersAndShield() {
        assertThat(GearCatalog.weightCns("Leather")).isEqualTo(200);
        assertThat(GearCatalog.weightCns("Chain mail")).isEqualTo(400);
        assertThat(GearCatalog.weightCns("Plate mail")).isEqualTo(500);
        assertThat(GearCatalog.weightCns("Shield")).isEqualTo(100);
    }

    @Test
    void weighsCommonGear() {
        assertThat(GearCatalog.weightCns("Backpack")).isEqualTo(20);
        assertThat(GearCatalog.weightCns("Tinderbox")).isEqualTo(5);
        assertThat(GearCatalog.weightCns("Waterskin")).isEqualTo(35);
        assertThat(GearCatalog.weightCns("50' rope")).isEqualTo(50);
        assertThat(GearCatalog.weightCns("Spellbook")).isEqualTo(300); // heavy, per gygax75
    }

    @Test
    void weighsRationsBundleDifferentlyFromAPerUnitRation() {
        assertThat(GearCatalog.weightCns("Rations (1 week)")).isEqualTo(210);
        assertThat(GearCatalog.weightCns("Rations")).isEqualTo(30);
    }
}
