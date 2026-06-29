package dev.freitas.delve.game;

import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
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
            hp += Math.max(1, dice.d(characterClass.hitDie()) + r.getAbilities().modifier(Ability.CON));
        }
        r.setMaxHp(Math.max(1, hp));
        r.setCurrentHp(r.getMaxHp());

        equip(r, characterClass);
        return r;
    }

    private void equip(Retainer r, CharacterClass characterClass) {
        switch (characterClass) {
            case FIGHTER, DWARF -> {
                r.setArmor(Armor.CHAIN_MAIL);
                r.setShield(true);
                r.setMainWeapon(characterClass == CharacterClass.DWARF ? "Battle axe" : "Sword");
                r.setMainWeaponDamage(new DamageRoll(1, 8));
            }
            case CLERIC -> {
                r.setArmor(Armor.CHAIN_MAIL);
                r.setShield(true);
                r.setMainWeapon("Mace");
                r.setMainWeaponDamage(new DamageRoll(1, 6));
            }
            case MAGIC_USER -> {
                r.setArmor(Armor.NONE);
                r.setMainWeapon("Dagger");
                r.setMainWeaponDamage(new DamageRoll(1, 4));
            }
            case THIEF, ELF -> {
                r.setArmor(Armor.LEATHER);
                r.setMainWeapon("Sword");
                r.setMainWeaponDamage(new DamageRoll(1, 8));
            }
            case HALFLING -> {
                r.setArmor(Armor.LEATHER);
                r.setMainWeapon("Short sword");
                r.setMainWeaponDamage(new DamageRoll(1, 6));
            }
        }
    }
}
