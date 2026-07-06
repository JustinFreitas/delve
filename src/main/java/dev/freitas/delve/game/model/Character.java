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
import dev.freitas.delve.game.engine.Encumbrance;
import dev.freitas.delve.game.engine.GearCatalog;
import dev.freitas.delve.game.engine.WeaponCatalog;
import java.util.ArrayList;
import java.util.List;

/**
 * A player character. Mutable POJO serialized to/from the save blob via Jackson, so it carries a
 * no-arg constructor and plain getters/setters. Derived combat values (effective AC, THAC0) are
 * computed from the stored state rather than persisted, so they stay correct as gear/level change.
 */
public class Character implements Combatant, Advanceable {

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

    /** A second one-handed weapon in the off hand — grants +1 to attack (never itself, no separate
        damage roll: the house rule is a flat to-hit bonus, not an extra attack). Mutually exclusive
        with a shield by the ordinary two-hands budget ({@link dev.freitas.delve.game.engine.Hands}). */
    private String offHandWeapon;

    private int gold;

    /** Torches carried for lighting the way; consumed as they burn out underground. */
    private int torches = 6;

    /** Lanterns owned (reusable — burns oil flasks rather than being consumed itself). */
    private int lanterns = 0;

    /** Flasks of oil owned, a lantern's fuel (one flask per {@link dev.freitas.delve.game.engine.LightSource#turnsPerUse()}). */
    private int oilFlasks = 0;

    private List<String> inventory = new ArrayList<>();
    private List<String> spellbook = new ArrayList<>();
    private List<String> memorizedSpells = new ArrayList<>();
    private int healingPotions = 0;

    /** Number of delves begun (manual or simulated via autodelve), for tracking career progress. */
    private int delveCount = 0;

    public Character() {}

    /** Effective descending Armor Class: armor base, improved by a shield and by the DEX modifier. */
    public int armorClass() {
        return armor.baseArmorClass() - (shield ? 1 : 0) - abilities.modifier(Ability.DEX);
    }

    /** Ascending AC equivalent (AC asc = 19 - AC desc), shown on the sheet for readability. */
    public int ascendingArmorClass() {
        return 19 - armorClass();
    }

    /** Total carried weight in cns (gygax75-rules, 1 coin = 1 cn): armor, shield, both weapons, light
        supplies, healing potions, everything in {@code inventory} (by name via {@link GearCatalog}),
        and gold — the input to {@link Encumbrance}'s movement-rate bands. */
    @JsonIgnore
    public int carriedWeightCns() {
        int weight = armor.weightCns()
                + (shield ? Encumbrance.SHIELD_WEIGHT_CNS : 0)
                + WeaponCatalog.weightCns(mainWeapon)
                + (offHandWeapon != null ? WeaponCatalog.weightCns(offHandWeapon) : 0)
                + torches * Encumbrance.TORCH_WEIGHT_CNS
                + lanterns * Encumbrance.LANTERN_WEIGHT_CNS
                + oilFlasks * Encumbrance.OIL_FLASK_WEIGHT_CNS
                + healingPotions * Encumbrance.POTION_WEIGHT_CNS
                + gold * Encumbrance.COIN_WEIGHT_CNS;
        for (String item : inventory) {
            weight += GearCatalog.weightCns(item);
        }
        return weight;
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
    @Override
    @JsonIgnore
    public int missileToHitModifier() {
        return abilities.modifier(Ability.DEX);
    }

    /** Melee damage modifier: STR modifier (no ability bonus to missile damage in B/X). */
    @JsonIgnore
    public int meleeDamageModifier() {
        return abilities.modifier(Ability.STR);
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

    public String getOffHandWeapon() {
        return offHandWeapon;
    }

    /** No hands-budget check here — this is a plain POJO setter (Jackson deserializes saves by calling
        setters directly, in whatever order fields appear in the JSON, so cross-field validation here
        would be order-dependent and fragile). Callers that offer this as a player action (currently only
        {@code WieldCommand}) are responsible for validating against {@link dev.freitas.delve.game.engine.Hands}
        first. */
    public void setOffHandWeapon(String offHandWeapon) {
        this.offHandWeapon = offHandWeapon;
    }

    /** Sheet/party-listing note for the off-hand weapon, or {@code null} if none is wielded — the single
        source of truth for this text so {@code CharacterSheets} and {@code PartySummary} can't drift. */
    @JsonIgnore
    public String offHandDescription() {
        return offHandWeapon == null ? null : " + " + offHandWeapon + " off hand (+1 to attack)";
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

    public int getLanterns() {
        return lanterns;
    }

    public void setLanterns(int lanterns) {
        this.lanterns = lanterns;
    }

    public int getOilFlasks() {
        return oilFlasks;
    }

    public void setOilFlasks(int oilFlasks) {
        this.oilFlasks = oilFlasks;
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

    public List<String> getMemorizedSpells() {
        return memorizedSpells;
    }

    public void setMemorizedSpells(List<String> memorizedSpells) {
        this.memorizedSpells = memorizedSpells;
    }

    public int getHealingPotions() {
        return healingPotions;
    }

    public void setHealingPotions(int healingPotions) {
        this.healingPotions = healingPotions;
    }

    public int getDelveCount() {
        return delveCount;
    }

    public void setDelveCount(int delveCount) {
        this.delveCount = delveCount;
    }
}
