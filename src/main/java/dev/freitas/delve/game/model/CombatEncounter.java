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

    public CombatEncounter() {}

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
}
