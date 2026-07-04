package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.LightSource;
import org.junit.jupiter.api.Test;

class LightSourceTest {

    @Test
    void torchIsCheapAndShortLived() {
        assertThat(LightSource.TORCH.itemCostGp()).isEqualTo(1);
        assertThat(LightSource.TORCH.turnsPerUse()).isEqualTo(6);
        assertThat(LightSource.TORCH.radiusFeet()).isEqualTo(30);
    }

    @Test
    void lanternCostsMoreButItsFuelBurnsFourTimesAsLong() {
        assertThat(LightSource.LANTERN.itemCostGp()).isEqualTo(10);
        assertThat(LightSource.LANTERN.turnsPerUse()).isEqualTo(4 * LightSource.TORCH.turnsPerUse());
        assertThat(LightSource.LANTERN.radiusFeet()).isEqualTo(30);
    }

    @Test
    void oilFlaskCostIsSeparateFromTheLanternItself() {
        assertThat(LightSource.OIL_FLASK_COST_GP).isEqualTo(2);
        assertThat(LightSource.OIL_FLASK_COST_GP).isLessThan(LightSource.LANTERN.itemCostGp());
    }
}
