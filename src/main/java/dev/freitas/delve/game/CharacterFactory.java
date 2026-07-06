package dev.freitas.delve.game;

import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.ContainerRules;
import dev.freitas.delve.game.engine.ContainerType;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.GearCatalog;
import dev.freitas.delve.game.engine.Leveling;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Container;
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
        Character c = newCharacter(name, characterClass, abilities);
        applyEquipmentPackage(c, characterClass);
        return c;
    }

    /** As {@link #create}, but skips the free starting-equipment package — inventory stays empty, no
        armor/shield, {@code mainWeapon} left at {@link Character}'s own bare-fists default — so a real
        player character spends their rolled gold via {@code /buy} instead ("guided shopping"). Used only
        by {@code RollCharacterCommand}: pregen/roster/NPC generation still calls {@link #create}, since
        those tools (and the wider test suite) rely on an instantly fully-equipped character. */
    public Character createBare(String name, CharacterClass characterClass, AbilityScores abilities) {
        Character c = newCharacter(name, characterClass, abilities);
        c.setTorches(0); // no free starting torches either — buy everything, including light
        return c;
    }

    private Character newCharacter(String name, CharacterClass characterClass, AbilityScores abilities) {
        Character c = new Character();
        c.setName(name);
        c.setCharacterClass(characterClass);
        c.setLevel(1);
        c.setXp(0);
        c.setAbilities(abilities);

        int hp = Math.max(1, Leveling.rollHitDie(dice, characterClass.hitDie()) + abilities.modifier(Ability.CON));
        c.setMaxHp(hp);
        c.setCurrentHp(hp);

        c.setGold(dice.roll(3, 6) * 10);

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
                gear.add("Dagger");
                gear.add("Sling & 30 stones"); // cheap, one-handed missile backup
            }
            case CLERIC -> {
                c.setArmor(Armor.CHAIN_MAIL);
                c.setShield(true);
                setWeapon(c, "Mace", new DamageRoll(1, 6)); // clerics use blunt weapons only
                gear.add("Holy symbol (wooden)");
                gear.add("Sling & 30 stones"); // blunt, fits the no-edged-weapons tradition
            }
            case MAGIC_USER -> {
                c.setArmor(Armor.NONE);
                setWeapon(c, "Dagger", new DamageRoll(1, 4));
                gear.add("Spellbook");
                // No missile weapon: B/X restricts magic-users to the dagger alone.
            }
            case THIEF -> {
                c.setArmor(Armor.LEATHER);
                setWeapon(c, "Sword", new DamageRoll(1, 8));
                gear.add("Dagger");
                gear.add("Thieves' tools");
                gear.add("Sling & 30 stones");
            }
            case ELF -> {
                c.setArmor(Armor.CHAIN_MAIL);
                setWeapon(c, "Sword", new DamageRoll(1, 8));
                gear.add("Short bow & 20 arrows");
                gear.add("Spellbook");
            }
            case HALFLING -> {
                c.setArmor(Armor.LEATHER);
                setWeapon(c, "Short sword", new DamageRoll(1, 6));
                gear.add("Sling & 30 stones");
            }
        }
        // Common kit every delver carries (see Character.torches for the light supply, tracked as a
        // dedicated int rather than a flavor string here).
        gear.add("Tinderbox");
        gear.add("Rations (1 week)");
        gear.add("Waterskin");
        gear.add("50' rope");

        // Every starting kit comes with a real worn backpack; non-exempt items are auto-assigned into it
        // (or wherever else has room, following the same rule /buy uses). This curated grant is
        // best-effort, not capacity-refused the way a purchase is — a couple of kits (Magic-User/Elf's
        // 300cn spellbook plus the ~265cn common kit) run past a single 400cn backpack's limit, and that
        // overflow is allowed here rather than denying character creation or inventing a second backpack
        // nobody asked for.
        Container backpack = new Container(ContainerType.BACKPACK, false, c.getName());
        c.getContainers().add(backpack);
        for (String item : gear) {
            if (ContainerRules.isExempt(item)) {
                c.getInventory().add(item);
                continue;
            }
            Container home = ContainerRules.findRoomFor(c.getContainers(), GearCatalog.weightCns(item));
            (home != null ? home : backpack).getItems().add(item);
        }
    }

    private void setWeapon(Character c, String name, DamageRoll damage) {
        c.setMainWeapon(name);
        c.setMainWeaponDamage(damage);
    }
}
