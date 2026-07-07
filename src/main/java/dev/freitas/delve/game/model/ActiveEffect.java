package dev.freitas.delve.game.model;

/** An active timed status or spell effect in the dungeon turn loop. */
public class ActiveEffect {

    private String label;
    private int turnsRemaining;
    private String ownerPcName;

    public ActiveEffect() {}

    public ActiveEffect(String label, int turnsRemaining, String ownerPcName) {
        this.label = label;
        this.turnsRemaining = turnsRemaining;
        this.ownerPcName = ownerPcName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getTurnsRemaining() {
        return turnsRemaining;
    }

    public void setTurnsRemaining(int turnsRemaining) {
        this.turnsRemaining = turnsRemaining;
    }

    public String getOwnerPcName() {
        return ownerPcName;
    }

    public void setOwnerPcName(String ownerPcName) {
        this.ownerPcName = ownerPcName;
    }
}
