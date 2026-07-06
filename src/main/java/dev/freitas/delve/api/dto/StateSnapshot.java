package dev.freitas.delve.api.dto;

import dev.freitas.delve.game.engine.MuleRules;
import dev.freitas.delve.game.model.Mule;
import dev.freitas.delve.game.model.SaveGame;
import java.util.List;

/** A compact view of the player's current state, returned alongside every action result. The singular
    {@code characterName}/{@code characterClass}/etc. fields always describe the primary (first-rolled)
    PC — the default view before a specific PC is selected in a multi-PC party; {@code characters} lists
    every PC for a PC switcher to choose from. */
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
        List<CharacterSummary> characters,
        List<RetainerSummary> retainers,
        MuleSummary mule) {

    public record CharacterSummary(
            String name, String characterClass, int level, int currentHp, int maxHp, boolean alive, boolean primary) {}

    public record RetainerSummary(
            String name, String characterClass, int level, int currentHp, int maxHp, String owner) {}

    public record MuleSummary(
            String name, String handler, int carriedGold, int capacityMax, int armorClass, int currentHp, int maxHp) {}

    public static StateSnapshot of(SaveGame save) {
        if (save == null || !save.hasCharacter()) {
            return new StateSnapshot(false, null, null, null, null, null, "IN_TOWN", false, false, null, null,
                    List.of(), List.of(), null);
        }
        var c = save.getCharacter();
        var session = save.getSession();
        boolean inDungeon = session.isInDungeon();

        List<CharacterSummary> characters = save.getCharacters().stream()
                .map(pc -> new CharacterSummary(pc.getName(), pc.getCharacterClass().displayName(), pc.getLevel(),
                        pc.getCurrentHp(), pc.getMaxHp(), pc.isAlive(), pc == c))
                .toList();
        List<RetainerSummary> retainers = save.getRetainers().stream()
                .map(r -> new RetainerSummary(r.getName(), r.getCharacterClass().displayName(), r.getLevel(),
                        r.getCurrentHp(), r.getMaxHp(), save.ownerOf(r).getName()))
                .toList();
        Mule m = save.getMule();
        MuleSummary mule = m == null ? null : new MuleSummary(m.getName(), m.getHandler(), m.getCarriedGold(),
                MuleRules.CAPACITY_MAX_CNS, m.armorClass(), m.getCurrentHp(), m.getMaxHp());

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
                characters,
                retainers,
                mule);
    }
}
