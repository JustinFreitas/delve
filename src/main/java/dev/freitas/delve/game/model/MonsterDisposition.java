package dev.freitas.delve.game.model;

/**
 * An authored override for a room's monster reaction, superseding the rolled reaction check (and even
 * the undead-always-hostile default) when a module scripts one explicitly.
 */
public enum MonsterDisposition {
    HOSTILE,
    NEUTRAL,
    FRIENDLY
}
