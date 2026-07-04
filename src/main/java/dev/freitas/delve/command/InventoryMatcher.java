package dev.freitas.delve.command;

import dev.freitas.delve.game.model.Character;

/** Resolves a player-typed item name against a character's inventory — shared by {@code /wield} and
    {@code /sell}, both of which need to find "the item the player means" from free text. */
final class InventoryMatcher {

    private InventoryMatcher() {}

    /** Case-insensitive substring match against the character's inventory, or {@code null} if none. */
    static String find(Character character, String query) {
        for (String item : character.getInventory()) {
            if (item.toLowerCase().contains(query.toLowerCase())) {
                return item;
            }
        }
        return null;
    }
}
