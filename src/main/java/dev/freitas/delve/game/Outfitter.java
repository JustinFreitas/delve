package dev.freitas.delve.game;

import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.GearCatalog;
import dev.freitas.delve.game.engine.Hands;
import dev.freitas.delve.game.engine.LightSource;
import dev.freitas.delve.game.model.Character;
import java.util.ArrayList;
import java.util.List;

/**
 * Best-effort auto-gear for a freshly-rolled PC: spends as much of {@link Character#getGold()} as it
 * can afford on a class-appropriate weapon, armor, and shield — degrading tier-by-tier (chain mail ->
 * leather -> none) rather than buying nothing — plus a few torches for light. Prices come from
 * {@link GearCatalog}, the same price list {@code /buy} uses, so this is a real gold purchase against a
 * bare character (contrast {@link CharacterFactory#create}'s free kit). Common flavor gear (rope,
 * rations, a backpack, etc.) is left out since it has no mechanical effect — a player who wants it can
 * still {@code /buy} it by hand. This is {@code /roll-character}'s default gearing step; passing the
 * {@code bare} flag skips it in favor of manual shopping.
 */
public final class Outfitter {

    private Outfitter() {}

    private record Kit(String weapon, DamageRoll weaponDamage, List<Armor> armorTiers, boolean shield) {}

    private static final DamageRoll DAGGER_DAMAGE = new DamageRoll(1, 4);

    /** Torches aimed for — matches the free starting kit's count. */
    private static final int TORCH_TARGET = 6;

    private static Kit kitFor(CharacterClass characterClass) {
        return switch (characterClass) {
            case FIGHTER -> new Kit("Sword", new DamageRoll(1, 8), List.of(Armor.CHAIN_MAIL, Armor.LEATHER), true);
            case DWARF -> new Kit("Battle axe", new DamageRoll(1, 8), List.of(Armor.CHAIN_MAIL, Armor.LEATHER), true);
            case CLERIC -> new Kit("Mace", new DamageRoll(1, 6), List.of(Armor.CHAIN_MAIL, Armor.LEATHER), true);
            case MAGIC_USER -> new Kit("Dagger", DAGGER_DAMAGE, List.of(), false);
            case THIEF -> new Kit("Sword", new DamageRoll(1, 8), List.of(Armor.LEATHER), false);
            case ELF -> new Kit("Sword", new DamageRoll(1, 8), List.of(Armor.CHAIN_MAIL, Armor.LEATHER), false);
            case HALFLING -> new Kit("Short sword", new DamageRoll(1, 6), List.of(Armor.LEATHER), false);
        };
    }

    /** Spends {@code c}'s current gold on its class kit, mutating weapon/armor/shield/torches directly.
        Returns a human-readable summary of what was bought (or that nothing could be afforded). */
    public static String outfit(Character c) {
        Kit kit = kitFor(c.getCharacterClass());
        List<String> bought = new ArrayList<>();
        int startingGold = c.getGold();

        if (buy(c, kit.weapon(), bought)) {
            c.setMainWeapon(kit.weapon());
            c.setMainWeaponDamage(kit.weaponDamage());
        } else if (buy(c, "Dagger", bought)) {
            // Even a class whose ideal weapon was out of reach still ends up armed with something.
            c.setMainWeapon("Dagger");
            c.setMainWeaponDamage(DAGGER_DAMAGE);
        }

        for (Armor tier : kit.armorTiers()) {
            if (buy(c, tier.displayName(), bought)) {
                c.setArmor(tier);
                break;
            }
        }

        if (kit.shield() && Hands.fits(c.getMainWeapon(), true, false) && buy(c, "Shield", bought)) {
            c.setShield(true);
        }

        int torchCost = LightSource.TORCH.itemCostGp();
        int torches = 0;
        while (torches < TORCH_TARGET && c.getGold() >= torchCost) {
            c.setGold(c.getGold() - torchCost);
            torches++;
        }
        if (torches > 0) {
            c.setTorches(c.getTorches() + torches);
            bought.add(torches + " torch" + (torches == 1 ? "" : "es") + " (" + (torches * torchCost) + " gp)");
        }

        if (bought.isEmpty()) {
            return "couldn't afford any starting gear.";
        }
        int spent = startingGold - c.getGold();
        return "outfitted with " + String.join(", ", bought) + " — " + spent + " gp spent, " + c.getGold() + " gp left.";
    }

    private static boolean buy(Character c, String item, List<String> bought) {
        int cost = GearCatalog.priceGp(item);
        if (cost < 0 || c.getGold() < cost) {
            return false;
        }
        c.setGold(c.getGold() - cost);
        bought.add(item + " (" + cost + " gp)");
        return true;
    }
}
