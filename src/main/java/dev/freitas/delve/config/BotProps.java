package dev.freitas.delve.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bot configuration bound from {@code delve.properties} / environment under the {@code config}
 * prefix (cf. ukulele's {@code BotProps}). Audio-specific options are dropped.
 */
@ConfigurationProperties("config")
public class BotProps {

    /** Discord bot token. Required; the bot refuses to start if blank. */
    private String token = "";

    /** Number of gateway shards. */
    private int shards = 1;

    /** Default command prefix for the legacy message path; overridable per guild. */
    private String prefix = "!";

    /** Discord "Playing ..." activity text. Defaults to a thematic line when blank. */
    private String game = "";

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getShards() {
        return shards;
    }

    public void setShards(int shards) {
        this.shards = shards;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getGame() {
        return game;
    }

    public void setGame(String game) {
        this.game = game;
    }
}
