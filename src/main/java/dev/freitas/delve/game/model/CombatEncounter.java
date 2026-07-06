package dev.freitas.delve.game.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

/** A live fight in the current room: the monster group, the round counter, and morale state. */
public class CombatEncounter {

    private List<Monster> monsters = new ArrayList<>();
    private String monsterName;
    private int initialCount;
    private int round;
    private boolean moraleBroken;

    // Morale is edge-triggered (B/X: first casualty, then half-or-fewer remaining), each rolled once.
    private boolean firstCasualtyChecked;
    private boolean halfLossChecked;

    // Surprise (rolled once, at the start of the fight) and the abstract engagement range that closes
    // toward melee over subsequent rounds.
    private boolean partySurprised;
    private boolean monstersSurprised;
    private int distanceFeet;

    // Held (not worn) containers dropped at combat start because their owner's hands are already
    // full without them (see ContainerService.reconcileHeldContainers) — each still carries its own
    // owner token, so this is a flat list rather than a per-owner map. Returned to the owner on
    // victory, lost for good on a successful flee.
    private List<Container> droppedContainers = new ArrayList<>();

    public CombatEncounter() {}

    @JsonIgnore
    public boolean isMelee() {
        return distanceFeet <= 0;
    }

    @JsonIgnore
    public List<Monster> aliveMonsters() {
        List<Monster> alive = new ArrayList<>();
        for (Monster m : monsters) {
            if (m.isAlive()) {
                alive.add(m);
            }
        }
        return alive;
    }

    @JsonIgnore
    public boolean isOver() {
        return moraleBroken || aliveMonsters().isEmpty();
    }

    public List<Monster> getMonsters() {
        return monsters;
    }

    public void setMonsters(List<Monster> monsters) {
        this.monsters = monsters;
    }

    public String getMonsterName() {
        return monsterName;
    }

    public void setMonsterName(String monsterName) {
        this.monsterName = monsterName;
    }

    public int getInitialCount() {
        return initialCount;
    }

    public void setInitialCount(int initialCount) {
        this.initialCount = initialCount;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public boolean isMoraleBroken() {
        return moraleBroken;
    }

    public void setMoraleBroken(boolean moraleBroken) {
        this.moraleBroken = moraleBroken;
    }

    public boolean isFirstCasualtyChecked() {
        return firstCasualtyChecked;
    }

    public void setFirstCasualtyChecked(boolean firstCasualtyChecked) {
        this.firstCasualtyChecked = firstCasualtyChecked;
    }

    public boolean isHalfLossChecked() {
        return halfLossChecked;
    }

    public void setHalfLossChecked(boolean halfLossChecked) {
        this.halfLossChecked = halfLossChecked;
    }

    public boolean isPartySurprised() {
        return partySurprised;
    }

    public void setPartySurprised(boolean partySurprised) {
        this.partySurprised = partySurprised;
    }

    public boolean isMonstersSurprised() {
        return monstersSurprised;
    }

    public void setMonstersSurprised(boolean monstersSurprised) {
        this.monstersSurprised = monstersSurprised;
    }

    public int getDistanceFeet() {
        return distanceFeet;
    }

    public void setDistanceFeet(int distanceFeet) {
        this.distanceFeet = Math.max(0, distanceFeet);
    }

    public List<Container> getDroppedContainers() {
        return droppedContainers;
    }

    public void setDroppedContainers(List<Container> droppedContainers) {
        this.droppedContainers = droppedContainers;
    }
}
