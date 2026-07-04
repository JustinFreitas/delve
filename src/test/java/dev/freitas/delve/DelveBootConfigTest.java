package dev.freitas.delve;

import net.dv8tion.jda.api.sharding.ShardManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Boots with the real {@code delve.properties} in the default (web-disabled) mode — the headless
 * deployment PM2 runs. Guards against the regression where the always-present Discord OAuth2 client
 * registration with an empty client id made Spring Boot fail to start; the engine tests never loaded
 * the real property file and so missed it. With web off, {@code web-application-type=none} keeps the
 * servlet/OAuth autoconfiguration out entirely.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@TestPropertySource(
        locations = "classpath:delve.properties",
        properties = {
            "spring.datasource.url=jdbc:h2:mem:delve-boot-test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
            "WEB_ENABLED=false"
        })
class DelveBootConfigTest {

    // Replaces the real JDA gateway so the empty test token never triggers a connection.
    @MockitoBean
    private ShardManager shardManager;

    @Test
    void headlessContextLoadsWithRealProperties() {
        // Success is the context starting at all — the crash was at refresh time.
    }
}
