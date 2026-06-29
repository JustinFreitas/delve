package dev.freitas.delve;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for delve, a B/X (Basic/Expert) D&D dungeon-crawler Discord bot.
 *
 * <p>The bot is patterned on the ukulele music bot: a Spring-DI command framework with dual
 * prefix + slash support, JDA event dispatch, and a cached persistence layer. The audio domain is
 * replaced by a deterministic B/X rules engine and a per-player game-session state machine.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DelveApplication {

    public static void main(String[] args) {
        System.setProperty("spring.config.name", "delve");
        SpringApplication.run(DelveApplication.class, args);
    }
}
