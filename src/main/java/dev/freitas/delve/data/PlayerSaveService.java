package dev.freitas.delve.data;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Read-through/write-through access to {@link PlayerSave} rows, cached with Caffeine (the blocking,
 * virtual-thread-friendly analogue of ukulele's {@code GuildPropertiesService}).
 */
@Service
public class PlayerSaveService {

    private final Logger log = LoggerFactory.getLogger(PlayerSaveService.class);
    private final PlayerSaveRepository repo;
    private final Cache<Long, PlayerSave> cache =
            Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(30)).build();

    public PlayerSaveService(PlayerSaveRepository repo) {
        this.repo = repo;
    }

    /** Returns the save for this user, loading from the database (or creating a fresh one) as needed. */
    public PlayerSave get(long discordUserId) {
        return cache.get(
                discordUserId,
                id -> {
                    Optional<PlayerSave> found = repo.findById(id);
                    return found.map(PlayerSave::markExisting)
                            .orElseGet(() -> new PlayerSave(id, null).markNew());
                });
    }

    /** Applies a mutation to the user's save and persists it. */
    public PlayerSave transform(long discordUserId, Consumer<PlayerSave> mutator) {
        PlayerSave save = get(discordUserId);
        mutator.accept(save);
        PlayerSave persisted = repo.save(save).markExisting();
        cache.put(discordUserId, persisted);
        log.info("Saved player {} ", discordUserId);
        return persisted;
    }
}
