package dev.freitas.delve.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.freitas.delve.game.model.SaveGame;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Typed access to a player's {@link SaveGame}, layered over {@link PlayerSaveService}: it
 * (de)serializes the JSON blob with Jackson so commands work with game objects rather than strings.
 *
 * <p>Also owns the per-player action lock ({@link #withUserLock}): a whole action is a
 * load → mutate → save cycle spanning several calls here, and two front ends (Discord commands on
 * virtual threads, the web API) can act on the same save concurrently — without the lock the later
 * save silently overwrites the earlier one's changes.
 */
@Service
public class GameStateService {

    private final PlayerSaveService playerSaves;
    private final ObjectMapper objectMapper;

    // Per-player action lock cached with Caffeine weak values: as long as a caller holds a reference to
    // the lock (during withUserLock or an active HTTP request in UserActionLockFilter), the lock stays in
    // memory. Once released and unreferenced, garbage collection reclaims it cleanly.
    private final Cache<Long, ReentrantLock> userLocks = Caffeine.newBuilder().weakValues().build();

    public GameStateService(PlayerSaveService playerSaves, ObjectMapper objectMapper) {
        this.playerSaves = playerSaves;
        this.objectMapper = objectMapper;
    }

    /** This player's action lock — for callers (the web interceptor) whose acquire and release sites
        are two separate lifecycle callbacks and so can't pass a lambda to {@link #withUserLock}. */
    public ReentrantLock userLock(long discordUserId) {
        return userLocks.get(discordUserId, id -> new ReentrantLock());
    }

    /** Runs one player action holding that player's lock, so a concurrent command/web request for the
        same player waits instead of interleaving its load → mutate → save with this one. */
    public <T> T withUserLock(long discordUserId, Supplier<T> action) {
        ReentrantLock lock = userLock(discordUserId);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /** Loads the player's save, or a fresh empty one if they have never played. */
    public SaveGame load(long discordUserId) {
        String json = playerSaves.get(discordUserId).getStateJson();
        if (json == null || json.isBlank()) {
            return new SaveGame();
        }
        try {
            return objectMapper.readValue(json, SaveGame.class);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt save for user " + discordUserId, e);
        }
    }

    /** Mutates and persists the player's save in one call. */
    public SaveGame mutate(long discordUserId, Consumer<SaveGame> mutator) {
        SaveGame state = load(discordUserId);
        mutator.accept(state);
        save(discordUserId, state);
        return state;
    }

    public void save(long discordUserId, SaveGame state) {
        try {
            String prevJson = playerSaves.get(discordUserId).getStateJson();
            if (prevJson != null && !prevJson.isBlank()) {
                try {
                    SaveGame prevState = objectMapper.readValue(prevJson, SaveGame.class);
                    if (prevState.hasCharacter()) {
                        prevState.getHistory().clear();
                        String cleanPrevJson = objectMapper.writeValueAsString(prevState);
                        java.util.List<String> history = state.getHistory();
                        if (history.isEmpty() || !history.get(history.size() - 1).equals(cleanPrevJson)) {
                            history.add(cleanPrevJson);
                            if (history.size() > 5) {
                                history.remove(0);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            saveDirect(discordUserId, state);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize save for user " + discordUserId, e);
        }
    }

    public void saveDirect(long discordUserId, SaveGame state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            playerSaves.transform(discordUserId, ps -> ps.setStateJson(json));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize save for user " + discordUserId, e);
        }
    }

    public SaveGame undo(long discordUserId) {
        SaveGame current = load(discordUserId);
        if (current.getHistory().isEmpty()) {
            return null;
        }
        String prevJson = current.getHistory().remove(current.getHistory().size() - 1);
        try {
            SaveGame undone = objectMapper.readValue(prevJson, SaveGame.class);
            undone.setHistory(current.getHistory());
            saveDirect(discordUserId, undone);
            return undone;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to undo: " + e.getMessage(), e);
        }
    }
}
