package dev.freitas.delve.game.model;

/**
 * Root of a player's persisted game state — the object serialized into {@code player_save.state_json}.
 * It grows over the milestones (party of retainers, in-progress dungeon run, session state); for now
 * it holds the active character.
 */
public class SaveGame {

    private Character character;

    public SaveGame() {}

    public boolean hasCharacter() {
        return character != null;
    }

    public Character getCharacter() {
        return character;
    }

    public void setCharacter(Character character) {
        this.character = character;
    }
}
