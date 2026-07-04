package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Guards the live-data-safety property behind the multi-PC migration: {@link SaveGame} used to persist
 * a single {@code "character"} JSON key; it now persists a {@code "characters"} list. Because the app's
 * {@link ObjectMapper} ignores unknown properties (see {@code JacksonConfig}), a naive rename would have
 * silently dropped every existing save's PC on load. The {@code @JsonIgnore} getter / {@code
 * @JsonProperty("character")} setter bridge on {@link SaveGame} is what prevents that.
 */
class SaveGameMigrationTest {

    private final ObjectMapper mapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Dice dice = new Dice(new Random(7));
    private final CharacterFactory factory = new CharacterFactory(dice);

    @Test
    void legacySingleCharacterKeyStillLoadsIntoTheCharactersList() throws Exception {
        Character legacyPc = factory.create("Legacy", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
        String characterJson = mapper.writeValueAsString(legacyPc);
        // A hand-built legacy blob: only the old singular key, never round-tripped through new code.
        String legacyBlob = "{\"character\":" + characterJson + "}";

        SaveGame loaded = mapper.readValue(legacyBlob, SaveGame.class);

        assertThat(loaded.getCharacters()).hasSize(1);
        assertThat(loaded.getCharacter()).isNotNull();
        assertThat(loaded.getCharacter().getName()).isEqualTo("Legacy");
    }

    @Test
    void reserializingAMigratedSaveWritesTheNewKeyNotTheOld() throws Exception {
        Character legacyPc = factory.create("Legacy", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
        String legacyBlob = "{\"character\":" + mapper.writeValueAsString(legacyPc) + "}";
        SaveGame loaded = mapper.readValue(legacyBlob, SaveGame.class);

        String reserialized = mapper.writeValueAsString(loaded);

        assertThat(reserialized).contains("\"characters\"");
        assertThat(reserialized).doesNotContain("\"character\":");
    }

    @Test
    void freshMultiPcSaveRoundTripsCleanly() throws Exception {
        SaveGame save = new SaveGame();
        save.setCharacter(factory.create("Anna", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9)));
        save.addCharacter(factory.create("Bram", CharacterClass.CLERIC, new AbilityScores(9, 9, 9, 9, 9, 12)));

        String json = mapper.writeValueAsString(save);
        SaveGame reloaded = mapper.readValue(json, SaveGame.class);

        assertThat(reloaded.getCharacters()).extracting(Character::getName).containsExactly("Anna", "Bram");
    }
}
