package dev.freitas.delve.game.model;

import dev.freitas.delve.game.engine.CombatTables;
import dev.freitas.delve.game.engine.DamageRoll;

/**
 * An immutable B/X monster stat block (the template). Hit Dice are d8 in B/X; a "+" bonus adds to HP
 * and bumps the attack band. {@code numberAppearing} is kept as a dice expression string (e.g.
 * {@code "2d4"}) for the dungeon-stocking step in Milestone 4.
 *
 * @param name             monster name
 * @param hitDiceCount     number of d8 Hit Dice
 * @param hitDiceBonus     flat HP/"plus" modifier on the Hit Dice
 * @param armorClass       descending Armor Class
 * @param attack           damage dealt on a hit
 * @param morale           2d6 morale target (B/X: lower flees sooner; 2-12)
 * @param xpValue          XP awarded for defeating one
 * @param numberAppearing  dice expression for how many appear in a dungeon
 */
public record MonsterType(
        String name,
        int hitDiceCount,
        int hitDiceBonus,
        int armorClass,
        DamageRoll attack,
        int morale,
        int xpValue,
        String numberAppearing) {

    /** This monster's THAC0, derived from its Hit Dice. */
    public int thac0() {
        return CombatTables.monsterThac0(hitDiceCount, hitDiceBonus);
    }

    /** Human-readable Hit Dice, e.g. {@code "1"}, {@code "1+1"}, {@code "3"}. */
    public String hitDiceLabel() {
        return hitDiceBonus == 0 ? String.valueOf(hitDiceCount) : hitDiceCount + "+" + hitDiceBonus;
    }
}
