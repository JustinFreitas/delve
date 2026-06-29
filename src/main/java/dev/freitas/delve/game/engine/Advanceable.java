package dev.freitas.delve.game.engine;

/** Anything that earns XP and gains levels — the player character and retainers. Used by {@link Leveling}. */
public interface Advanceable {

    String getName();

    CharacterClass getCharacterClass();

    AbilityScores getAbilities();

    int getXp();

    void setXp(int xp);

    int getLevel();

    void setLevel(int level);

    int getMaxHp();

    void setMaxHp(int maxHp);

    int getCurrentHp();

    void setCurrentHp(int currentHp);
}
