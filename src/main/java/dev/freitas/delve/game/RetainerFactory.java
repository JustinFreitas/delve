package dev.freitas.delve.game;

import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Leveling;
import dev.freitas.delve.game.model.Retainer;
import org.springframework.stereotype.Component;

/** Rolls up a hireable retainer: abilities, hit points, a class-appropriate weapon and armor. */
@Component
public class RetainerFactory {

    private final Dice dice;

    public RetainerFactory(Dice dice) {
        this.dice = dice;
    }

    public Retainer create(String name, CharacterClass characterClass, int level, int loyalty) {
        Retainer r = new Retainer();
        r.setName(name);
        r.setCharacterClass(characterClass);
        r.setLevel(Math.max(1, level));
        r.setAbilities(AbilityScores.roll(dice));
        r.setLoyalty(loyalty);

        int hp = 0;
        for (int i = 0; i < r.getLevel(); i++) {
            hp += Math.max(1, Leveling.rollHitDie(dice, characterClass.hitDie()) + r.getAbilities().modifier(Ability.CON));
        }
        r.setMaxHp(Math.max(1, hp));
        r.setCurrentHp(r.getMaxHp());

        equip(r, characterClass);
        return r;
    }

    /** A class's starting retainer equipment package. Exposed so other call sites (the {@code /hire}
        "smart" bulk-hire class ordering) can derive a class-level stat proxy from the exact same rules
        a real hire gets, instead of a second, driftable table. {@code secondaryWeapon}/{@code
        secondaryWeaponDamage} are a carried missile weapon the retainer isn't currently wielding as
        {@code weapon} (null when none fits the class, e.g. the Magic-User's dagger-only tradition) —
        lets a melee-equipped retainer still fire from rank 2+ before melee closes. */
    public record Equipment(
            Armor armor, boolean shield, String weapon, DamageRoll weaponDamage,
            String secondaryWeapon, DamageRoll secondaryWeaponDamage) {}

    private static final DamageRoll SLING_DAMAGE = new DamageRoll(1, 4);

    public static Equipment equipmentFor(CharacterClass characterClass) {
        return switch (characterClass) {
            case FIGHTER, DWARF -> new Equipment(Armor.CHAIN_MAIL, true,
                    characterClass == CharacterClass.DWARF ? "Battle axe" : "Sword", new DamageRoll(1, 8),
                    "Sling", SLING_DAMAGE);
            case CLERIC -> new Equipment(Armor.CHAIN_MAIL, true, "Mace", new DamageRoll(1, 6),
                    "Sling", SLING_DAMAGE); // blunt, fits the cleric's no-edged-weapons tradition
            case MAGIC_USER -> new Equipment(Armor.NONE, false, "Dagger", new DamageRoll(1, 4), null, null);
            case THIEF -> new Equipment(Armor.LEATHER, false, "Sword", new DamageRoll(1, 8), "Sling", SLING_DAMAGE);
            case ELF -> new Equipment(Armor.LEATHER, false, "Sword", new DamageRoll(1, 8),
                    "Short bow", new DamageRoll(1, 6));
            case HALFLING -> new Equipment(Armor.LEATHER, false, "Short sword", new DamageRoll(1, 6),
                    "Sling", SLING_DAMAGE);
            // gygax75-rules custom classes.
            case BARBARIAN, HALF_ORC -> new Equipment(Armor.CHAIN_MAIL, true, "Sword", new DamageRoll(1, 8),
                    "Sling", SLING_DAMAGE);
            case DRUID -> new Equipment(Armor.NONE, false, "Dagger", new DamageRoll(1, 4), "Sling", SLING_DAMAGE);
            case KNIGHT -> new Equipment(Armor.CHAIN_MAIL, true, "Sword", new DamageRoll(1, 8), null, null);
            case WARDEN, WOOD_ELF -> new Equipment(Armor.LEATHER, false, "Sword", new DamageRoll(1, 8),
                    "Short bow", new DamageRoll(1, 6));
            case GNOME -> new Equipment(Armor.LEATHER, false, "Short sword", new DamageRoll(1, 6),
                    "Sling", SLING_DAMAGE);
        };
    }

    private void equip(Retainer r, CharacterClass characterClass) {
        Equipment eq = equipmentFor(characterClass);
        r.setArmor(eq.armor());
        r.setShield(eq.shield());
        r.setMainWeapon(eq.weapon());
        r.setMainWeaponDamage(eq.weaponDamage());
        r.setSecondaryWeapon(eq.secondaryWeapon());
        r.setSecondaryWeaponDamage(eq.secondaryWeaponDamage());
    }
}
