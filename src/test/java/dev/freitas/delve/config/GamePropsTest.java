package dev.freitas.delve.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.CharacterClass;
import org.junit.jupiter.api.Test;

class GamePropsTest {

    @Test
    void standardClassesAreAlwaysEnabledRegardlessOfConfig() {
        GameProps props = new GameProps(); // default: enabledCustomClasses = ""
        for (CharacterClass cls : CharacterClass.values()) {
            if (!cls.isCustom()) {
                assertThat(props.isClassEnabled(cls)).as(cls.toString()).isTrue();
            }
        }
    }

    @Test
    void customClassesAreDisabledByDefault() {
        GameProps props = new GameProps();
        for (CharacterClass cls : CharacterClass.values()) {
            if (cls.isCustom()) {
                assertThat(props.isClassEnabled(cls)).as(cls.toString()).isFalse();
            }
        }
    }

    @Test
    void customClassesEnabledViaCommaSeparatedList() {
        GameProps props = new GameProps();
        props.setEnabledCustomClasses("barbarian, Gnome ,wood-elf");

        assertThat(props.isClassEnabled(CharacterClass.BARBARIAN)).isTrue();
        assertThat(props.isClassEnabled(CharacterClass.GNOME)).isTrue();
        assertThat(props.isClassEnabled(CharacterClass.WOOD_ELF)).isTrue();
        assertThat(props.isClassEnabled(CharacterClass.DRUID)).isFalse();
        assertThat(props.isClassEnabled(CharacterClass.KNIGHT)).isFalse();
        assertThat(props.isClassEnabled(CharacterClass.WARDEN)).isFalse();
        assertThat(props.isClassEnabled(CharacterClass.HALF_ORC)).isFalse();
        // Standard classes are unaffected either way.
        assertThat(props.isClassEnabled(CharacterClass.FIGHTER)).isTrue();
    }

    @Test
    void unrecognizedTokensInTheListAreHarmlesslyIgnored() {
        GameProps props = new GameProps();
        props.setEnabledCustomClasses("bard,,barbarian");

        assertThat(props.isClassEnabled(CharacterClass.BARBARIAN)).isTrue();
        assertThat(props.isClassEnabled(CharacterClass.DRUID)).isFalse();
    }
}
