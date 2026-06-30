package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.session.SpellService;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ExportTest {

    private final Dice dice = new Dice(new Random(77));
    private final SpellService spellService = new SpellService(dice);
    private final PregenService pregen =
            new PregenService(dice, new CharacterFactory(dice), spellService);

    @Test
    void pregenExportContainsTheKeyBxFields() {
        Character f = pregen.create("Aragorn", CharacterClass.FIGHTER, 5);
        Map<String, Object> export = PregenExport.of(f);

        assertThat(export.get("name")).isEqualTo("Aragorn");
        assertThat(export.get("class")).isEqualTo("Fighter");
        assertThat(export.get("level")).isEqualTo(5);
        assertThat(export.get("thac0")).isEqualTo(f.thac0());
        assertThat(export.get("armorClass")).isEqualTo(f.armorClass());
        assertThat(export).containsKeys("abilities", "savingThrows", "equipment", "gold", "hitPoints");

        @SuppressWarnings("unchecked")
        Map<String, Integer> abilities = (Map<String, Integer>) export.get("abilities");
        assertThat(abilities).containsKeys("STR", "INT", "WIS", "DEX", "CON", "CHA");
    }

    @Test
    void exportSerializesToValidJson() throws Exception {
        Character mu = pregen.create("Gandalf", CharacterClass.MAGIC_USER, 5);
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(PregenExport.of(mu));
        assertThat(json).contains("\"class\" : \"Magic-User\"").contains("\"level\" : 5");

        // Round-trips back to a map with the same core fields.
        @SuppressWarnings("unchecked")
        Map<String, Object> reparsed = mapper.readValue(json, Map.class);
        assertThat(reparsed.get("name")).isEqualTo("Gandalf");
        assertThat(reparsed).containsKey("preparedSpells"); // a level-5 MU has prepared spells
    }
}
