package dev.freitas.delve.config;

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
}
