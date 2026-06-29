package dev.freitas.delve.game.model;

import dev.freitas.delve.game.engine.Dice;

/**
 * A live monster in an encounter: a {@link MonsterType} with rolled hit points and current damage.
 * Hit points are {@code (count)d8 + bonus}, floored at 1.
 */
public class Monster {

    private MonsterType type;
    private int maxHp;
    private int currentHp;

    public Monster() {}

    private Monster(MonsterType type, int maxHp) {
        this.type = type;
        this.maxHp = maxHp;
        this.currentHp = maxHp;
    }

    /** Rolls a fresh instance of the given type (B/X monster Hit Dice are d8). */
    public static Monster roll(MonsterType type, Dice dice) {
        int hp = Math.max(1, dice.roll(type.hitDiceCount(), 8, type.hitDiceBonus()));
        return new Monster(type, hp);
    }

    public boolean isAlive() {
        return currentHp > 0;
    }

    /** Applies damage and returns the remaining hit points. */
    public int takeDamage(int amount) {
        currentHp = Math.max(0, currentHp - amount);
        return currentHp;
    }

    public MonsterType getType() {
        return type;
    }

    public void setType(MonsterType type) {
        this.type = type;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public void setMaxHp(int maxHp) {
        this.maxHp = maxHp;
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public void setCurrentHp(int currentHp) {
        this.currentHp = currentHp;
    }
}
