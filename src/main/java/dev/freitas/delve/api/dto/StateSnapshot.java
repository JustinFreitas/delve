package dev.freitas.delve.api.dto;

import dev.freitas.delve.game.model.SaveGame;

/** A compact view of the player's current state, returned alongside every action result. */
public record StateSnapshot(
        boolean hasCharacter,
        String characterName,
        String characterClass,
        Integer level,
        Integer currentHp,
        Integer maxHp,
        String sessionState,
        boolean inDungeon,
        boolean inCombat,
        Integer dungeonLevel,
        Integer dungeonTurn,
        Integer retainers) {

    public static StateSnapshot of(SaveGame save) {
        if (save == null || !save.hasCharacter()) {
            return new StateSnapshot(false, null, null, null, null, null, "IN_TOWN", false, false, null, null, 0);
        }
        var c = save.getCharacter();
        var session = save.getSession();
        boolean inDungeon = session.isInDungeon();
        return new StateSnapshot(
                true,
                c.getName(),
                c.getCharacterClass().displayName(),
                c.getLevel(),
                c.getCurrentHp(),
                c.getMaxHp(),
                session.getState().name(),
                inDungeon,
                session.getState().name().equals("IN_COMBAT"),
                inDungeon ? session.getCurrentLevel() + 1 : null,
                inDungeon ? session.getDungeonTurn() : null,
                save.getRetainers().size());
    }
}
