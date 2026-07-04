package dev.freitas.delve.game.engine;

/** A missile weapon's Short/Medium/Long range ceilings, in feet. */
public record MissileRangeTable(int shortFeet, int mediumFeet, int longFeet) {

    /** The range band for a given distance, or {@code null} if beyond Long range (an automatic miss). */
    public RangeBand band(int distanceFeet) {
        if (distanceFeet <= shortFeet) {
            return RangeBand.SHORT;
        }
        if (distanceFeet <= mediumFeet) {
            return RangeBand.MEDIUM;
        }
        if (distanceFeet <= longFeet) {
            return RangeBand.LONG;
        }
        return null;
    }
}
