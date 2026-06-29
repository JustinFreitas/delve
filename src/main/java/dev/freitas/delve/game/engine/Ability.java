package dev.freitas.delve.game.engine;

/** The six B/X ability scores, in roll order. */
public enum Ability {
    STR("Strength"),
    INT("Intelligence"),
    WIS("Wisdom"),
    DEX("Dexterity"),
    CON("Constitution"),
    CHA("Charisma");

    private final String fullName;

    Ability(String fullName) {
        this.fullName = fullName;
    }

    public String fullName() {
        return fullName;
    }

    /** Three-letter abbreviation (the enum name). */
    public String abbreviation() {
        return name();
    }
}
