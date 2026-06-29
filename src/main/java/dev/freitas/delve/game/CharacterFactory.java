package dev.freitas.delve.game;

import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds a complete level-1 B/X character from rolled abilities: hit points (hit die + CON modifier,
 * minimum 1), starting gold (3d6 × 10 gp), a class-appropriate equipment package, and — for arcane
 * casters — a starting spellbook. A guided shopping phase can replace the fixed packages later
 * (Milestone 7 / {@code /town}).
 */
@Component
public class CharacterFactory {

    // B/X 1st-level magic-user spells a new caster can start with (besides the always-known Read Magic).
    private static final List<String> STARTING_ARCANE_SPELLS = List.of(
            "Charm Person",
            "Detect Magic",
            "Floating Disc",
            "Hold Portal",
            "Light",
            "Magic Missile",
            "Protection from Evil",
            "Read Languages",
            "Shield",
            "Sleep",
            "Ventriloquism");

    private final Dice dice;

    public CharacterFactory(Dice dice) {
        this.dice = dice;
    }

    public Character create(String name, CharacterClass characterClass, AbilityScores abilities) {
        Character c = new Character();
        c.setName(name);
        c.setCharacterClass(characterClass);
        c.setLevel(1);
        c.setXp(0);
        c.setAbilities(abilities);

        int hp = Math.max(1, dice.d(characterClass.hitDie()) + abilities.modifier(Ability.CON));
        c.setMaxHp(hp);
        c.setCurrentHp(hp);

        c.setGold(dice.roll(3, 6) * 10);

        applyEquipmentPackage(c, characterClass);

        if (characterClass.isArcaneCaster()) {
            List<String> spellbook = new ArrayList<>();
            spellbook.add("Read Magic");
            spellbook.add(STARTING_ARCANE_SPELLS.get(dice.d(STARTING_ARCANE_SPELLS.size()) - 1));
            c.setSpellbook(spellbook);
        }

        return c;
    }

    /** Equips a class with its starting armor/shield and fills the pack with common adventuring gear. */
    private void applyEquipmentPackage(Character c, CharacterClass characterClass) {
        List<String> gear = new ArrayList<>();
        switch (characterClass) {
            case FIGHTER, DWARF -> {
                c.setArmor(Armor.CHAIN_MAIL);
                c.setShield(true);
                String weapon = characterClass == CharacterClass.DWARF ? "Battle axe" : "Sword";
                setWeapon(c, weapon, new DamageRoll(1, 8));
                gear.add(weapon);
                gear.add("Dagger");
            }
            case CLERIC -> {
                c.setArmor(Armor.CHAIN_MAIL);
                c.setShield(true);
                setWeapon(c, "Mace", new DamageRoll(1, 6)); // clerics use blunt weapons only
                gear.add("Mace");
                gear.add("Holy symbol (wooden)");
            }
            case MAGIC_USER -> {
                c.setArmor(Armor.NONE);
                setWeapon(c, "Dagger", new DamageRoll(1, 4));
                gear.add("Dagger");
                gear.add("Spellbook");
            }
            case THIEF -> {
                c.setArmor(Armor.LEATHER);
                setWeapon(c, "Sword", new DamageRoll(1, 8));
                gear.add("Sword");
                gear.add("Dagger");
                gear.add("Thieves' tools");
            }
            case ELF -> {
                c.setArmor(Armor.CHAIN_MAIL);
                setWeapon(c, "Sword", new DamageRoll(1, 8));
                gear.add("Sword");
                gear.add("Short bow & 20 arrows");
                gear.add("Spellbook");
            }
            case HALFLING -> {
                c.setArmor(Armor.LEATHER);
                setWeapon(c, "Short sword", new DamageRoll(1, 6));
                gear.add("Short sword");
                gear.add("Sling & 30 stones");
            }
        }
        // Common kit every delver carries (see Character.torches for the light supply).
        gear.add("Backpack");
        gear.add("6 Torches");
        gear.add("Tinderbox");
        gear.add("Rations (1 week)");
        gear.add("Waterskin");
        gear.add("50' rope");
        c.setInventory(gear);
    }

    private void setWeapon(Character c, String name, DamageRoll damage) {
        c.setMainWeapon(name);
        c.setMainWeaponDamage(damage);
    }
}
