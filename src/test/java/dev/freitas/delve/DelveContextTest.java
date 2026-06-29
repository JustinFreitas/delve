package dev.freitas.delve;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.data.PlayerSave;
import dev.freitas.delve.data.PlayerSaveService;
import dev.freitas.delve.discord.CommandManager;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Boots the full Spring context with the Discord gateway mocked out, proving the command framework,
 * persistence layer, and Flyway migration all wire together — Milestone 1's "it boots" check.
 */
@SpringBootTest
@TestPropertySource(
        properties = {
            "config.token=test-token",
            "spring.datasource.url=jdbc:h2:mem:delve-test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
        })
class DelveContextTest {

    // Replaces the real JDA ShardManager bean so the test never contacts Discord.
    @MockitoBean
    private ShardManager shardManager;

    @Autowired
    private CommandManager commandManager;

    @Autowired
    private PlayerSaveService playerSaves;

    @Test
    void commandsAreDiscovered() {
        assertThat(commandManager.get("ping")).isNotNull();
        assertThat(commandManager.get("help")).isNotNull();
        // "h" and "?" are aliases of help.
        assertThat(commandManager.get("h")).isSameAs(commandManager.get("help"));
    }

    @Test
    void playerSaveRoundTrips() {
        long userId = 424242L;
        playerSaves.transform(userId, save -> save.setStateJson("{\"hp\":7}"));

        PlayerSave reloaded = playerSaves.get(userId);
        assertThat(reloaded.getStateJson()).isEqualTo("{\"hp\":7}");
        assertThat(reloaded.isNew()).isFalse();
    }
}
