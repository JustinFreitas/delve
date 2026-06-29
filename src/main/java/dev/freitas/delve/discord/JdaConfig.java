package dev.freitas.delve.discord;

import dev.freitas.delve.config.BotProps;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/** Builds and manages the JDA {@link ShardManager}. No audio module — this is a text bot. */
@Configuration
public class JdaConfig {

    public JdaConfig() {
        MessageRequest.setDefaultMentions(java.util.Collections.emptyList());
    }

    @Bean
    public ShardManager shardManager(BotProps botProps, EventHandler eventHandler) {
        if (botProps.getToken().isBlank()) {
            throw new RuntimeException("Discord token not configured!");
        }
        String activityText = botProps.getGame().isBlank() ? "a dungeon delve" : botProps.getGame();

        // MESSAGE_CONTENT is privileged and only needed for the legacy prefix path; it can be removed
        // once slash commands fully replace prefix commands.
        return DefaultShardManagerBuilder.create(
                        botProps.getToken(),
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT)
                .disableCache(
                        CacheFlag.ACTIVITY,
                        CacheFlag.VOICE_STATE,
                        CacheFlag.CLIENT_STATUS,
                        CacheFlag.EMOJI,
                        CacheFlag.STICKER,
                        CacheFlag.SCHEDULED_EVENTS,
                        CacheFlag.ONLINE_STATUS)
                .setBulkDeleteSplittingEnabled(false)
                .setEnableShutdownHook(true)
                .addEventListeners(eventHandler)
                .setActivity(Activity.playing(activityText))
                .setStatus(OnlineStatus.ONLINE)
                .build();
    }

    @Component
    public static class JdaShutdownHook implements DisposableBean {
        private final ShardManager shardManager;

        public JdaShutdownHook(ShardManager shardManager) {
            this.shardManager = shardManager;
        }

        @Override
        public void destroy() {
            shardManager.shutdown();
        }
    }
}
