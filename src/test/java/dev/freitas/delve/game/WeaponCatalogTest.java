package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.RangeBand;
import dev.freitas.delve.game.engine.WeaponCatalog;
import dev.freitas.delve.game.engine.WeaponClass;
import org.junit.jupiter.api.Test;

class WeaponCatalogTest {

    @Test
    void classifiesKnownWeaponTypes() {
        assertThat(WeaponCatalog.classify("Short bow").weaponClass()).isEqualTo(WeaponClass.MISSILE);
        assertThat(WeaponCatalog.classify("Sling & 30 stones").weaponClass()).isEqualTo(WeaponClass.MISSILE);
        assertThat(WeaponCatalog.classify("Spear").weaponClass()).isEqualTo(WeaponClass.REACH);
        assertThat(WeaponCatalog.classify("Sword").weaponClass()).isEqualTo(WeaponClass.MELEE);
        assertThat(WeaponCatalog.classify(null).weaponClass()).isEqualTo(WeaponClass.MELEE);
        assertThat(WeaponCatalog.classify("Unrecognized Thing").weaponClass()).isEqualTo(WeaponClass.MELEE);
    }

    @Test
    void lightCrossbowIsNotShadowedByTheGenericCrossbowEntry() {
        assertThat(WeaponCatalog.classify("Light crossbow").rangeTable().shortFeet()).isEqualTo(60);
        assertThat(WeaponCatalog.classify("Heavy crossbow").rangeTable().shortFeet()).isEqualTo(80);
    }

    @Test
    void rangeBandsFollowTheShortMediumLongCeilings() {
        var table = WeaponCatalog.classify("Short bow").rangeTable();
        assertThat(table.band(10)).isEqualTo(RangeBand.SHORT);
        assertThat(table.band(50)).isEqualTo(RangeBand.SHORT);
        assertThat(table.band(51)).isEqualTo(RangeBand.MEDIUM);
        assertThat(table.band(100)).isEqualTo(RangeBand.MEDIUM);
        assertThat(table.band(150)).isEqualTo(RangeBand.LONG);
        assertThat(table.band(151)).isNull();
    }

    @Test
    void toHitModifiersMatchBxRangeBands() {
        assertThat(RangeBand.SHORT.toHitModifier()).isEqualTo(1);
        assertThat(RangeBand.MEDIUM.toHitModifier()).isEqualTo(0);
        assertThat(RangeBand.LONG.toHitModifier()).isEqualTo(-1);
    }

    @Test
    void damageForFallsBackToD6ForUnknownWeapons() {
        assertThat(WeaponCatalog.damageFor("Dagger").toString()).isEqualTo("1d4");
        assertThat(WeaponCatalog.damageFor("Unknown Thing").toString()).isEqualTo("1d6");
    }

    // --- Weight (gygax75-rules: 1 coin = 1 cn) ---------------------------------

    @Test
    void weighsRecognizedWeapons() {
        assertThat(WeaponCatalog.weightCns("Dagger")).isEqualTo(10);
        assertThat(WeaponCatalog.weightCns("Sword")).isEqualTo(60);
        assertThat(WeaponCatalog.weightCns("Battle axe")).isEqualTo(30);
        assertThat(WeaponCatalog.weightCns("Lance")).isEqualTo(120);
    }

    @Test
    void halberdAndPikeMapToPolearmsWeight() {
        assertThat(WeaponCatalog.weightCns("Halberd")).isEqualTo(150);
        assertThat(WeaponCatalog.weightCns("Pike")).isEqualTo(150);
        assertThat(WeaponCatalog.weightCns("Polearm")).isEqualTo(150);
    }

    @Test
    void unmatchedWeaponsFallBackToARepresentativeDefaultWeight() {
        assertThat(WeaponCatalog.weightCns("Unknown Thing")).isEqualTo(30);
        assertThat(WeaponCatalog.weightCns(null)).isEqualTo(30);
    }
}
