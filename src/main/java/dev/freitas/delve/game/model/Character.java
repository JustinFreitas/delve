package dev.freitas.delve.game.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.CombatTables;
import java.util.ArrayList;
import java.util.List;

/**
 * A player character. Mutable POJO serialized to/from the save blob via Jackson, so it carries a
 * no-arg constructor and plain getters/setters. Derived combat values (effective AC, THAC0) are
 * computed from the stored state rather than persisted, so they stay correct as gear/level change.
 */
public class Character {

    private String name;
    private CharacterClass characterClass;
    private int level = 1;
    private int xp = 0;

    private AbilityScores abilities = new AbilityScores();

    private int maxHp;
    private int currentHp;

    private Armor armor = Armor.NONE;
    private boolean shield;

    private int gold;

    /** Torches carried for lighting the way; consumed as they burn out underground. */
    private int torches = 6;

    private List<String> inventory = new ArrayList<>();
    private List<String> spellbook = new ArrayList<>();

    public Character() {}

    /** Effective descending Armor Class: armor base, improved by a shield and by the DEX modifier. */
    public int armorClass() {
        return armor.baseArmorClass() - (shield ? 1 : 0) - abilities.modifier(Ability.DEX);
    }

    /** Ascending AC equivalent (AC asc = 19 - AC desc), shown on the sheet for readability. */
    public int ascendingArmorClass() {
        return 19 - armorClass();
    }

    /** To-hit-AC-0 number, from the B/X "attacks as" progression for this class and level. */
    public int thac0() {
        return CombatTables.classThac0(characterClass, level);
    }

    /** Melee to-hit modifier: STR modifier (added to the d20 attack roll in B/X). */
    @JsonIgnore
    public int meleeToHitModifier() {
        return abilities.modifier(Ability.STR);
    }

    /** Missile to-hit modifier: DEX modifier. */
    @JsonIgnore
    public int missileToHitModifier() {
        return abilities.modifier(Ability.DEX);
    }

    /** Melee damage modifier: STR modifier (no ability bonus to missile damage in B/X). */
    @JsonIgnore
    public int meleeDamageModifier() {
        return abilities.modifier(Ability.STR);
    }

    /** XP needed to reach the next level (only level 2 is known until Milestone 7). */
    public int xpForNextLevel() {
        return characterClass.xpForLevel2();
    }

    @JsonIgnore
    public boolean isAlive() {
        return currentHp > 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CharacterClass getCharacterClass() {
        return characterClass;
    }

    public void setCharacterClass(CharacterClass characterClass) {
        this.characterClass = characterClass;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    public AbilityScores getAbilities() {
        return abilities;
    }

    public void setAbilities(AbilityScores abilities) {
        this.abilities = abilities;
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

    public Armor getArmor() {
        return armor;
    }

    public void setArmor(Armor armor) {
        this.armor = armor;
    }

    public boolean isShield() {
        return shield;
    }

    public void setShield(boolean shield) {
        this.shield = shield;
    }

    public int getGold() {
        return gold;
    }

    public void setGold(int gold) {
        this.gold = gold;
    }

    public int getTorches() {
        return torches;
    }

    public void setTorches(int torches) {
        this.torches = torches;
    }

    public List<String> getInventory() {
        return inventory;
    }

    public void setInventory(List<String> inventory) {
        this.inventory = inventory;
    }

    public List<String> getSpellbook() {
        return spellbook;
    }

    public void setSpellbook(List<String> spellbook) {
        this.spellbook = spellbook;
    }
}
