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
            String json = objectMapper.writeValueAsString(state);
            playerSaves.transform(discordUserId, ps -> ps.setStateJson(json));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize save for user " + discordUserId, e);
        }
    }
}
