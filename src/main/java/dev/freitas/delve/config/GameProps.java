package dev.freitas.delve.config;

import dev.freitas.delve.game.engine.CharacterClass;
import java.util.Arrays;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Game mechanics configuration bound under the {@code config.game} prefix.
 */
@ConfigurationProperties("config.game")
public class GameProps {

    /** Upfront advance fee to hire a retainer (in gold pieces). */
    private int retainerHiringFee = 25;

    /** Upkeep cost per retainer for a week in town (in gold pieces). */
    private int retainerUpkeep = 10;

    /** Comma-separated gygax75-rules custom class tokens the DM has opted into (e.g.
        {@code "barbarian,druid,gnome"}, parsed the same as {@link CharacterClass#parse}) — empty by
        default, since these are optional variants beyond the 7 standard B/X classes. */
    private String enabledCustomClasses = "";

    public int getRetainerHiringFee() {
        return retainerHiringFee;
    }

    public void setRetainerHiringFee(int retainerHiringFee) {
        this.retainerHiringFee = retainerHiringFee;
    }

    public int getRetainerUpkeep() {
        return retainerUpkeep;
    }

    public void setRetainerUpkeep(int retainerUpkeep) {
        this.retainerUpkeep = retainerUpkeep;
    }

    public String getEnabledCustomClasses() {
        return enabledCustomClasses;
    }

    public void setEnabledCustomClasses(String enabledCustomClasses) {
        this.enabledCustomClasses = enabledCustomClasses;
    }

    /** Whether {@code cls} may be rolled/hired by a player right now: every standard class always is;
        a custom (gygax75-rules) class only if this DM has named it in {@link #enabledCustomClasses}. */
    public boolean isClassEnabled(CharacterClass cls) {
        if (!cls.isCustom()) {
            return true;
        }
        return Arrays.stream(enabledCustomClasses.split(","))
                .map(String::trim)
                .anyMatch(token -> CharacterClass.parse(token) == cls);
    }
}
