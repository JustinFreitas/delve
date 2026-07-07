package dev.freitas.delve.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.freitas.delve.game.model.SaveGame;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * Typed access to a player's {@link SaveGame}, layered over {@link PlayerSaveService}: it
 * (de)serializes the JSON blob with Jackson so commands work with game objects rather than strings.
 */
@Service
public class GameStateService {

    private final PlayerSaveService playerSaves;
    private final ObjectMapper objectMapper;

    public GameStateService(PlayerSaveService playerSaves, ObjectMapper objectMapper) {
        this.playerSaves = playerSaves;
        this.objectMapper = objectMapper;
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
