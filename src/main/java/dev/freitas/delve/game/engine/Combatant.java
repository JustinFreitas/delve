package dev.freitas.delve.game.engine;

/** Anything that can take (and usually deal) hits in a round of combat — implemented by player
    characters, retainers, and the party's mule (which is targetable but never attacks). */
public interface Combatant {

    String getName();

    int getCurrentHp();

    void setCurrentHp(int hp);

    int getMaxHp();

    /** Effective descending Armor Class. */
    int armorClass();

    /** To-hit-AC-0 number for this combatant's class and level. */
    int thac0();

    int meleeToHitModifier();

    /** Missile to-hit modifier (DEX modifier in B/X). */
    int missileToHitModifier();

    int meleeDamageModifier();

    DamageRoll getMainWeaponDamage();

    String getMainWeapon();

    boolean isAlive();

    CharacterClass getCharacterClass();
}
