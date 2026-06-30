package dev.freitas.delve.game.dungeon;

/**
 * An authored special room that can be stamped into a generated level. Loaded from
 * {@code content/setpieces.json}. A guardian (Bestiary name) and treasure are optional.
 */
public record SetPiece(
        String name,
        String description,
        String specialText,
        String guardianMonster,
        int guardianCount,
        int treasureGold) {}
