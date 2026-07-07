package dev.freitas.delve.game.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.freitas.delve.game.engine.LightSource;
import java.util.ArrayList;
import java.util.List;

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

    /** Which kind of light is currently lit, if any; null (with {@link #inDarkness} true) means none. */
    private LightSource activeLight;

    /** Turns of light left on the current torch/lantern; 0 with none lit means darkness. */
    private int lightTurnsRemaining;
    private boolean inDarkness;

    /** Who's physically holding the lit torch/lantern: {@link SaveGame#PLAYER_SLOT} or a retainer's
        name (same token style as {@code marchingOrder}); null if nobody is (or nothing is lit). */
    private String lightBearer;

    /** The active fight, when {@link #state} is {@link SessionState#IN_COMBAT}. */
    private CombatEncounter combat;

    /** Front rank is probing ahead with a 10-foot pole: passive trap detection, but caught with hands
        off their weapon (AC penalty) if surprised. Resets when a delve begins. */
    private boolean polingFrontRank;

    /** Dungeon turns elapsed since the party last rested; resets to 0 on {@code rest} and when a
        delve begins. Six turns (one hour) without resting incurs a fatigue penalty. */
    private int turnsSinceRest;

    /** Active timed status/spell effects in the turn loop. */
    private List<ActiveEffect> activeEffects = new ArrayList<>();

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

    public LightSource getActiveLight() {
        return activeLight;
    }

    public void setActiveLight(LightSource activeLight) {
        this.activeLight = activeLight;
    }

    public int getLightTurnsRemaining() {
        return lightTurnsRemaining;
    }

    public void setLightTurnsRemaining(int lightTurnsRemaining) {
        this.lightTurnsRemaining = lightTurnsRemaining;
    }

    public String getLightBearer() {
        return lightBearer;
    }

    public void setLightBearer(String lightBearer) {
        this.lightBearer = lightBearer;
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

    public boolean isPolingFrontRank() {
        return polingFrontRank;
    }

    public void setPolingFrontRank(boolean polingFrontRank) {
        this.polingFrontRank = polingFrontRank;
    }

    public int getTurnsSinceRest() {
        return turnsSinceRest;
    }

    public void setTurnsSinceRest(int turnsSinceRest) {
        this.turnsSinceRest = turnsSinceRest;
    }

    /** Six dungeon turns (one hour) without resting incurs a -1 attack/damage penalty until the
        party rests. */
    @JsonIgnore
    public boolean isFatigued() {
        return turnsSinceRest >= 6;
    }

    public List<ActiveEffect> getActiveEffects() {
        return activeEffects;
    }

    public void setActiveEffects(List<ActiveEffect> activeEffects) {
        this.activeEffects = activeEffects;
    }

    public void addEffect(String label, int turns, String ownerPcName) {
        if (activeEffects == null) {
            activeEffects = new ArrayList<>();
        }
        for (ActiveEffect effect : activeEffects) {
            if (effect.getLabel().equalsIgnoreCase(label) &&
                ((ownerPcName == null && effect.getOwnerPcName() == null) ||
                 (ownerPcName != null && ownerPcName.equalsIgnoreCase(effect.getOwnerPcName())))) {
                effect.setTurnsRemaining(turns);
                return;
            }
        }
        activeEffects.add(new ActiveEffect(label, turns, ownerPcName));
    }

    @JsonIgnore
    public boolean isSpellLightActive() {
        if (activeEffects == null) {
            return false;
        }
        for (ActiveEffect effect : activeEffects) {
            String label = effect.getLabel();
            if (label.equalsIgnoreCase("Light") || label.equalsIgnoreCase("Light (Cleric)")) {
                return true;
            }
        }
        return false;
    }
}
