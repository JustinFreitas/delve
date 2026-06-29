package dev.freitas.delve.game.model;

/**
 * Root of a player's persisted game state — the object serialized into {@code player_save.state_json}.
 * It grows over the milestones (party of retainers, in-progress dungeon run, session state); for now
 * it holds the active character.
 */
public class SaveGame {

    private Character character;
    private GameSession session = new GameSession();
    private java.util.List<Retainer> retainers = new java.util.ArrayList<>();

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

    public GameSession getSession() {
        return session;
    }

    public void setSession(GameSession session) {
        this.session = session;
    }

    public java.util.List<Retainer> getRetainers() {
        return retainers;
    }

    public void setRetainers(java.util.List<Retainer> retainers) {
        this.retainers = retainers;
    }

    /** Retainers still able to fight (alive and not fled). */
    public java.util.List<Retainer> livingRetainers() {
        java.util.List<Retainer> alive = new java.util.ArrayList<>();
        for (Retainer r : retainers) {
            if (r.isAlive()) {
                alive.add(r);
            }
        }
        return alive;
    }
}

