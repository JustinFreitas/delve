package dev.freitas.delve.game.model;

/** What a monster's hit does beyond rolling {@link MonsterType#attack()} for damage. */
public enum AttackEffect {
    /** Ordinary damage, per the monster's {@code attack} roll. */
    NORMAL,
    /** Permanently drains a level instead of dealing normal damage. */
    DRAIN
}
