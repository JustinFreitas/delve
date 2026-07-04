package dev.freitas.delve.game.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Advanceable;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.CombatTables;
import dev.freitas.delve.game.engine.DamageRoll;

/**
 * A hired retainer (henchman) who adventures and fights alongside the player. Shares the combat and
 * advancement contracts with {@link Character} but adds loyalty and a "fled" flag for desertion.
 */
public class Retainer implements Combatant, Advanceable {

    private String name;
    private CharacterClass characterClass;
    private int level = 1;
    private int xp = 0;
    private AbilityScores abilities = new AbilityScores();

    private int maxHp;
    private int currentHp;

    private Armor armor = Armor.NONE;
    private boolean shield;
    private String mainWeapon = "Weapon";
    private DamageRoll mainWeaponDamage = new DamageRoll(1, 6);
    // A carried missile weapon the retainer isn't currently wielding as mainWeapon (null if none fits
    // the class) — lets a melee-equipped retainer still fire from rank 2+ before melee closes.
    private String secondaryWeapon;
    private DamageRoll secondaryWeaponDamage;

    private int loyalty = 7;
    private boolean fled;

    public Retainer() {}

    @Override
    public int armorClass() {
        return armor.baseArmorClass() - (shield ? 1 : 0) - abilities.modifier(Ability.DEX);
    }

    @Override
    public int thac0() {
        return CombatTables.classThac0(characterClass, level);
    }

    @Override
    @JsonIgnore
    public int meleeToHitModifier() {
        return abilities.modifier(Ability.STR);
    }

    /** Missile to-hit modifier: DEX modifier. */
    @Override
    @JsonIgnore
    public int missileToHitModifier() {
        return abilities.modifier(Ability.DEX);
    }

    @Override
    @JsonIgnore
    public int meleeDamageModifier() {
        return abilities.modifier(Ability.STR);
    }

    @Override
    @JsonIgnore
    public boolean isAlive() {
        return currentHp > 0 && !fled;
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

    public String getMainWeapon() {
        return mainWeapon;
    }

    public void setMainWeapon(String mainWeapon) {
        this.mainWeapon = mainWeapon;
    }

    public DamageRoll getMainWeaponDamage() {
        return mainWeaponDamage == null ? new DamageRoll(1, 6) : mainWeaponDamage;
    }

    public void setMainWeaponDamage(DamageRoll mainWeaponDamage) {
        this.mainWeaponDamage = mainWeaponDamage;
    }

    public String getSecondaryWeapon() {
        return secondaryWeapon;
    }

    public void setSecondaryWeapon(String secondaryWeapon) {
        this.secondaryWeapon = secondaryWeapon;
    }

    public DamageRoll getSecondaryWeaponDamage() {
        return secondaryWeaponDamage;
    }

    public void setSecondaryWeaponDamage(DamageRoll secondaryWeaponDamage) {
        this.secondaryWeaponDamage = secondaryWeaponDamage;
    }

    public int getLoyalty() {
        return loyalty;
    }

    public void setLoyalty(int loyalty) {
        this.loyalty = loyalty;
    }

    public boolean isFled() {
        return fled;
    }

    public void setFled(boolean fled) {
        this.fled = fled;
    }
}
