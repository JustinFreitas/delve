package dev.freitas.delve.command;

import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Container;

/** Resolves a player-typed item name against a character's exempt on-person inventory and every
    container they're carrying — shared by {@code /wield}, {@code /sell}, and {@code /spike}, all of
    which need to find "the item the player means" from free text regardless of where it's actually
    stowed. */
final class InventoryMatcher {

    private InventoryMatcher() {}

    /** Case-insensitive substring match against the character's exempt inventory, then every
        container's items, or {@code null} if none. */
    static String find(Character character, String query) {
        String lowerQuery = query.toLowerCase();
        for (String item : character.getInventory()) {
            if (item.toLowerCase().contains(lowerQuery)) {
                return item;
            }
        }
        for (Container container : character.getContainers()) {
            for (String item : container.getItems()) {
                if (item.toLowerCase().contains(lowerQuery)) {
                    return item;
                }
            }
        }
        return null;
    }

    /** Removes one occurrence of {@code item} from wherever {@link #find} actually located it — the
        exempt inventory or a container — so callers that only matched by name don't need to know which
        list to mutate. */
    static void remove(Character character, String item) {
        if (character.getInventory().remove(item)) {
            return;
        }
        for (Container container : character.getContainers()) {
            if (container.getItems().remove(item)) {
                return;
            }
        }
    }
}
