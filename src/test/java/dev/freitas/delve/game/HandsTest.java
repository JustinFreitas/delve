package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.Hands;
import org.junit.jupiter.api.Test;

class HandsTest {

    @Test
    void oneHandedWeaponAloneLeavesOneHandFree() {
        assertThat(Hands.used("Sword", false, false)).isEqualTo(1);
        assertThat(Hands.free("Sword", false, false)).isEqualTo(1);
        assertThat(Hands.fits("Sword", false, false)).isTrue();
    }

    @Test
    void oneHandedWeaponPlusShieldFillsBothHands() {
        assertThat(Hands.used("Sword", true, false)).isEqualTo(2);
        assertThat(Hands.free("Sword", true, false)).isEqualTo(0);
        assertThat(Hands.fits("Sword", true, false)).isTrue();
    }

    @Test
    void shieldPlusHoldingLightExceedsTwoHands() {
        assertThat(Hands.used("Sword", true, true)).isEqualTo(3);
        assertThat(Hands.free("Sword", true, true)).isEqualTo(0); // clamped, never negative
        assertThat(Hands.fits("Sword", true, true)).isFalse();
    }

    @Test
    void twoHandedWeaponAloneLeavesNoFreeHand() {
        assertThat(Hands.used("Short bow", false, false)).isEqualTo(2);
        assertThat(Hands.free("Short bow", false, false)).isEqualTo(0);
        assertThat(Hands.fits("Short bow", false, false)).isTrue();
    }

    @Test
    void twoHandedWeaponPlusShieldNeverFits() {
        assertThat(Hands.used("Long bow", true, false)).isEqualTo(3);
        assertThat(Hands.fits("Long bow", true, false)).isFalse();
    }

    @Test
    void slingIsOneHandedUnlikeBows() {
        assertThat(Hands.used("Sling & 30 stones", false, true)).isEqualTo(2);
        assertThat(Hands.fits("Sling & 30 stones", false, true)).isTrue();
    }

    @Test
    void offHandWeaponAloneFitsWithOneHandFree() {
        assertThat(Hands.used("Sword", false, false, true)).isEqualTo(2);
        assertThat(Hands.free("Sword", false, false, true)).isEqualTo(0);
        assertThat(Hands.fits("Sword", false, false, true)).isTrue();
    }

    @Test
    void offHandWeaponPlusShieldExceedsTwoHands() {
        assertThat(Hands.used("Sword", true, false, true)).isEqualTo(3);
        assertThat(Hands.free("Sword", true, false, true)).isEqualTo(0);
        assertThat(Hands.fits("Sword", true, false, true)).isFalse();
    }

    @Test
    void offHandWeaponPlusTwoHandedMainWeaponExceedsTwoHands() {
        assertThat(Hands.used("Long bow", false, false, true)).isEqualTo(3);
        assertThat(Hands.fits("Long bow", false, false, true)).isFalse();
    }

    @Test
    void heldSacksAloneCostAHandEach() {
        // 3-arg + int overload: no off-hand concept (retainer shape).
        assertThat(Hands.used("Sword", false, false, 1)).isEqualTo(2);
        assertThat(Hands.free("Sword", false, false, 1)).isEqualTo(0);
        assertThat(Hands.fits("Sword", false, false, 1)).isTrue();
        assertThat(Hands.fits("Sword", false, false, 2)).isFalse();
    }

    @Test
    void heldSacksStackWithOffHandWeapon() {
        // 5-arg overload: PC shape, off-hand weapon plus held sacks both count.
        assertThat(Hands.used("Sword", false, false, true, 1)).isEqualTo(3);
        assertThat(Hands.free("Sword", false, false, true, 1)).isEqualTo(0);
        assertThat(Hands.fits("Sword", false, false, true, 0)).isTrue();
        assertThat(Hands.fits("Sword", false, false, true, 1)).isFalse();
    }
}
