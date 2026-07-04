package dev.freitas.delve.game.engine;

/**
 * How a weapon behaves relative to marching-order rank: {@code MELEE} only from the front of a column,
 * {@code REACH} (spear/polearm-style) also from rank 2 past a living rank-1 column-mate, {@code MISSILE}
 * fired from rank 2+ instead of melee'd.
 */
public enum WeaponClass {
    MELEE,
    REACH,
    MISSILE
}
