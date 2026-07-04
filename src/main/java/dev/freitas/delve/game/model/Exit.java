package dev.freitas.delve.game.model;

import dev.freitas.delve.game.engine.DamageRoll;

/**
 * A one-way link from a room toward another in a given direction (each connection stores a matching
 * {@code Exit} on both rooms). Mutable so doors can be opened and secret passages revealed during
 * play. Serialized by id reference, so the dungeon graph stays acyclic for Jackson.
 */
public class Exit {

    private Direction direction;
    private int destinationRoomId;
    private DoorState door = DoorState.NONE;
    private boolean secret;
    private boolean revealed;

    /** Whether a character has already spent their one listening attempt at this door. */
    private boolean listened;

    // Corridor trap in the passage itself — same shape as Room's own trap, sprung on traversal instead
    // of on room entry.
    private boolean trapped;
    private boolean trapDetected;
    private boolean trapSprung;
    private String trapDescription;
    private DamageRoll trapDamage;

    public Exit() {}

    public Exit(Direction direction, int destinationRoomId, DoorState door, boolean secret) {
        this.direction = direction;
        this.destinationRoomId = destinationRoomId;
        this.door = door;
        this.secret = secret;
        this.revealed = !secret;
    }

    /** Whether the player currently knows this exit exists (non-secret, or a discovered secret). */
    public boolean isKnown() {
        return revealed;
    }

    /** Whether the player can move through right now (known and not blocked by a shut door). */
    public boolean isPassable() {
        return revealed && door.isPassable();
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public int getDestinationRoomId() {
        return destinationRoomId;
    }

    public void setDestinationRoomId(int destinationRoomId) {
        this.destinationRoomId = destinationRoomId;
    }

    public DoorState getDoor() {
        return door;
    }

    public void setDoor(DoorState door) {
        this.door = door;
    }

    public boolean isSecret() {
        return secret;
    }

    public void setSecret(boolean secret) {
        this.secret = secret;
    }

    public boolean isRevealed() {
        return revealed;
    }

    public void setRevealed(boolean revealed) {
        this.revealed = revealed;
    }

    public boolean isListened() {
        return listened;
    }

    public void setListened(boolean listened) {
        this.listened = listened;
    }

    public boolean isTrapped() {
        return trapped;
    }

    public void setTrapped(boolean trapped) {
        this.trapped = trapped;
    }

    public boolean isTrapDetected() {
        return trapDetected;
    }

    public void setTrapDetected(boolean trapDetected) {
        this.trapDetected = trapDetected;
    }

    public boolean isTrapSprung() {
        return trapSprung;
    }

    public void setTrapSprung(boolean trapSprung) {
        this.trapSprung = trapSprung;
    }

    public String getTrapDescription() {
        return trapDescription;
    }

    public void setTrapDescription(String trapDescription) {
        this.trapDescription = trapDescription;
    }

    public DamageRoll getTrapDamage() {
        return trapDamage;
    }

    public void setTrapDamage(DamageRoll trapDamage) {
        this.trapDamage = trapDamage;
    }
}
