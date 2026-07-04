package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.Toughness;
import dev.freitas.delve.game.model.Retainer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToughnessTest {

    @Test
    void betterArmorSortsFirst() {
        Retainer wellArmored = retainer(Armor.CHAIN_MAIL, false, 10);
        Retainer poorlyArmored = retainer(Armor.NONE, false, 10);
        List<Retainer> list = new ArrayList<>(List.of(poorlyArmored, wellArmored));

        list.sort(Toughness.BY_TOUGHNESS);

        assertThat(list).containsExactly(wellArmored, poorlyArmored);
    }

    @Test
    void tiedArmorHigherHpSortsFirst() {
        Retainer tanky = retainer(Armor.LEATHER, false, 30);
        Retainer frail = retainer(Armor.LEATHER, false, 10);
        List<Retainer> list = new ArrayList<>(List.of(frail, tanky));

        list.sort(Toughness.BY_TOUGHNESS);

        assertThat(list).containsExactly(tanky, frail);
    }

    @Test
    void fullyTiedPreservesInputOrderUnderStableSort() {
        Retainer a = retainer(Armor.LEATHER, false, 10);
        Retainer b = retainer(Armor.LEATHER, false, 10);
        List<Retainer> list = new ArrayList<>(List.of(a, b));

        list.sort(Toughness.BY_TOUGHNESS);

        assertThat(list).containsExactly(a, b);
    }

    @Test
    void genericByToughnessRanksAnyTypeWithAcAndHpAccessors() {
        record Proxy(int armorClass, int hp) {}
        Proxy tanky = new Proxy(4, 8);
        Proxy frail = new Proxy(9, 4);
        List<Proxy> list = new ArrayList<>(List.of(frail, tanky));

        list.sort(Toughness.byToughness(Proxy::armorClass, Proxy::hp));

        assertThat(list).containsExactly(tanky, frail);
    }

    private Retainer retainer(Armor armor, boolean shield, int hp) {
        Retainer r = new Retainer();
        r.setName("Test");
        r.setArmor(armor);
        r.setShield(shield);
        r.setMaxHp(hp);
        r.setCurrentHp(hp);
        return r;
    }
}
