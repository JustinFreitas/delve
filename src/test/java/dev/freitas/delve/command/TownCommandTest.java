package dev.freitas.delve.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.LodgingTier;
import dev.freitas.delve.game.session.TownService;
import org.junit.jupiter.api.Test;

/** No test here constructs a {@link dev.freitas.delve.discord.CommandContext}, matching this codebase's
    established pattern of testing small extracted pure logic directly (see {@code BuyCommandTest}). */
class TownCommandTest {

    private final TownCommand townCommand = new TownCommand(new TownService(null, null, null));

    @Test
    void resolvesLodgingTiersCaseInsensitively() {
        assertThat(townCommand.lodgingTierFor("dormitory")).isEqualTo(LodgingTier.DORMITORY);
        assertThat(townCommand.lodgingTierFor("Dorm")).isEqualTo(LodgingTier.DORMITORY);
        assertThat(townCommand.lodgingTierFor("shared")).isEqualTo(LodgingTier.SHARED_ROOM);
        assertThat(townCommand.lodgingTierFor("SharedRoom")).isEqualTo(LodgingTier.SHARED_ROOM);
        assertThat(townCommand.lodgingTierFor("room")).isEqualTo(LodgingTier.ROOM);
    }

    @Test
    void nonTierTokensResolveToNull() {
        assertThat(townCommand.lodgingTierFor("7")).isNull();
        assertThat(townCommand.lodgingTierFor("gold")).isNull();
    }
}
