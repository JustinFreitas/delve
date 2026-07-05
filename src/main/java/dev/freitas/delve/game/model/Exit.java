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

    // A trap on the door's lock/mechanism itself -- distinct from the corridor trap above, sprung by
    // forcing/picking the door rather than by walking through it. Only ever set on STUCK/LOCKED doors.
    private boolean doorTrapped;
    private boolean doorTrapDetected;
    private boolean doorTrapSprung;
    private String doorTrapDescription;
    private DamageRoll doorTrapDamage;

    // Whether this door's state has ever been LOCKED -- so swing-shut (see ExplorationService#move)
    // knows to rest a formerly-locked door at UNLOCKED, not CLOSED, once it's shut again.
    private boolean everLocked;

    // Whether a spike/wedge has been jammed into this door -- true once used, from either direction;
    // excludes it from the automatic swing-shut check and blocks using a second spike on it.
    private boolean spiked;

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

    public boolean isDoorTrapped() {
        return doorTrapped;
    }

    public void setDoorTrapped(boolean doorTrapped) {
        this.doorTrapped = doorTrapped;
    }

    public boolean isDoorTrapDetected() {
        return doorTrapDetected;
    }

    public void setDoorTrapDetected(boolean doorTrapDetected) {
        this.doorTrapDetected = doorTrapDetected;
    }

    public boolean isDoorTrapSprung() {
        return doorTrapSprung;
    }

    public void setDoorTrapSprung(boolean doorTrapSprung) {
        this.doorTrapSprung = doorTrapSprung;
    }

    public String getDoorTrapDescription() {
        return doorTrapDescription;
    }

    public void setDoorTrapDescription(String doorTrapDescription) {
        this.doorTrapDescription = doorTrapDescription;
    }

    public DamageRoll getDoorTrapDamage() {
        return doorTrapDamage;
    }

    public void setDoorTrapDamage(DamageRoll doorTrapDamage) {
        this.doorTrapDamage = doorTrapDamage;
    }

    public boolean isEverLocked() {
        return everLocked;
    }

    public void setEverLocked(boolean everLocked) {
        this.everLocked = everLocked;
    }

    public boolean isSpiked() {
        return spiked;
    }

    public void setSpiked(boolean spiked) {
        this.spiked = spiked;
    }
}
