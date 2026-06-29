package dev.freitas.delve.game.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * The player's live dungeon run: where they are, what turn it is, and how much light remains. Stored
 * inside {@link SaveGame} so a delve survives bot restarts.
 */
public class GameSession {

    private SessionState state = SessionState.IN_TOWN;
    private Dungeon dungeon;
    private int currentLevel;
    private int currentRoomId;

    /** Elapsed B/X dungeon turns (10 minutes each). */
    private int dungeonTurn;

    /** Turns of light left on the current torch; 0 with no torch lit means darkness. */
    private int torchTurnsRemaining;
    private boolean inDarkness;

    /** The active fight, when {@link #state} is {@link SessionState#IN_COMBAT}. */
    private CombatEncounter combat;

    public GameSession() {}

    @JsonIgnore
    public boolean isInDungeon() {
        return dungeon != null && state != SessionState.IN_TOWN;
    }

    @JsonIgnore
    public DungeonLevel currentLevel() {
        return dungeon.level(currentLevel);
    }

    @JsonIgnore
    public Room currentRoom() {
        return currentLevel().room(currentRoomId);
    }

    public SessionState getState() {
        return state;
    }

    public void setState(SessionState state) {
        this.state = state;
    }

    public Dungeon getDungeon() {
        return dungeon;
    }

    public void setDungeon(Dungeon dungeon) {
        this.dungeon = dungeon;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(int currentLevel) {
        this.currentLevel = currentLevel;
    }

    public int getCurrentRoomId() {
        return currentRoomId;
    }

    public void setCurrentRoomId(int currentRoomId) {
        this.currentRoomId = currentRoomId;
    }

    public int getDungeonTurn() {
        return dungeonTurn;
    }

    public void setDungeonTurn(int dungeonTurn) {
        this.dungeonTurn = dungeonTurn;
    }

    public int getTorchTurnsRemaining() {
        return torchTurnsRemaining;
    }

    public void setTorchTurnsRemaining(int torchTurnsRemaining) {
        this.torchTurnsRemaining = torchTurnsRemaining;
    }

    public boolean isInDarkness() {
        return inDarkness;
    }

    public void setInDarkness(boolean inDarkness) {
        this.inDarkness = inDarkness;
    }

    public CombatEncounter getCombat() {
        return combat;
    }

    public void setCombat(CombatEncounter combat) {
        this.combat = combat;
    }
}
