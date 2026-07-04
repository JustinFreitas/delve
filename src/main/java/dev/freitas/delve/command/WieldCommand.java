package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.Hands;
import dev.freitas.delve.game.engine.WeaponCatalog;
import dev.freitas.delve.game.engine.WeaponClass;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Component;

/** Switches your main weapon to a recognized inventory item, toggles your shield, or wields a second
    one-handed weapon in your off hand for +1 to attack: {@code /wield <item name>},
    {@code /wield shield}, {@code /wield unshield}, {@code /wield offhand <item name>},
    {@code /wield unoffhand}. */
@Component
public class WieldCommand extends Command {

    public WieldCommand() {
        super("wield", "equip");
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = ctx.getBeans().gameState.load(ctx.getInvokerUserId());
        if (!save.hasCharacter()) {
            ctx.reply("Roll a character first with `" + ctx.getPrefix() + "roll-character <class>`.");
            return;
        }
        String name = ctx.getArgumentText().trim();
        if (name.isBlank()) {
            ctx.reply("Wield what? `wield <item name>` — check `sheet` for your inventory. "
                    + "Or `wield shield`/`unshield`, `wield offhand <item>`/`unoffhand`.");
            return;
        }
        Character character = save.getCharacter();
        boolean isBearer = SaveGame.PLAYER_SLOT.equalsIgnoreCase(save.getSession().getLightBearer());
        boolean offHandEquipped = character.getOffHandWeapon() != null;

        if (name.equalsIgnoreCase("unshield")) {
            character.setShield(false);
            ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
            ctx.reply("You stow your shield, freeing a hand.");
            return;
        }
        if (name.equalsIgnoreCase("shield")) {
            if (!Hands.fits(character.getMainWeapon(), true, isBearer, offHandEquipped)) {
                ctx.reply(handsFullMessage("a shield", WeaponCatalog.handsRequired(character.getMainWeapon()),
                        true, isBearer, offHandEquipped));
                return;
            }
            character.setShield(true);
            ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
            ctx.reply("You raise your shield.");
            return;
        }
        if (name.equalsIgnoreCase("unoffhand")) {
            character.setOffHandWeapon(null);
            ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
            ctx.reply("You sheath your off-hand weapon.");
            return;
        }
        // "offhand <item>" is only treated as the subcommand when there's no inventory item whose name
        // literally matches the whole argument — an item genuinely named e.g. "Offhand Axe" still wields
        // as a main weapon via the fallback path below, instead of always being misrouted here.
        String[] tokens = name.split("\\s+", 2);
        boolean offHandSubcommand = tokens[0].equalsIgnoreCase("offhand") && findInventoryItem(character, name) == null;
        if (offHandSubcommand) {
            String query = tokens.length > 1 ? tokens[1].trim() : "";
            if (query.isBlank()) {
                ctx.reply("Wield what in your off hand? `wield offhand <item name>`.");
                return;
            }
            String match = findInventoryItem(character, query);
            if (match == null) {
                ctx.reply("You don't have **" + query + "** in your inventory.");
                return;
            }
            if (WeaponCatalog.handsRequired(match) != 1
                    || WeaponCatalog.classify(match).weaponClass() == WeaponClass.MISSILE) {
                ctx.reply("**" + match + "** won't work in an off hand — only a one-handed melee or "
                        + "reach weapon will.");
                return;
            }
            if (!Hands.fits(character.getMainWeapon(), character.isShield(), isBearer, true)) {
                ctx.reply(handsFullMessage("**" + match + "** in your off hand",
                        WeaponCatalog.handsRequired(character.getMainWeapon()), character.isShield(), isBearer, true));
                return;
            }
            character.setOffHandWeapon(match);
            ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
            ctx.reply("You take up **" + match + "** in your off hand (+1 to attack while dual-wielding).");
            return;
        }

        String match = findInventoryItem(character, name);
        if (match == null) {
            ctx.reply("You don't have **" + name + "** in your inventory.");
            return;
        }
        if (!Hands.fits(match, character.isShield(), isBearer, offHandEquipped)) {
            ctx.reply(handsFullMessage("**" + match + "**", WeaponCatalog.handsRequired(match),
                    character.isShield(), isBearer, offHandEquipped));
            return;
        }
        character.setMainWeapon(match);
        character.setMainWeaponDamage(WeaponCatalog.damageFor(match));
        ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
        ctx.reply("You wield **" + match + "**.");
    }

    /** Case-insensitive substring match against the character's inventory, or {@code null} if none. */
    private String findInventoryItem(Character character, String name) {
        for (String item : character.getInventory()) {
            if (item.toLowerCase().contains(name.toLowerCase())) {
                return item;
            }
        }
        return null;
    }

    private String handsFullMessage(
            String itemDescription, int weaponHands, boolean shield, boolean isBearer, boolean offHand) {
        int needed = weaponHands + (shield ? 1 : 0) + (isBearer ? 1 : 0) + (offHand ? 1 : 0);
        StringBuilder sb = new StringBuilder(
                "You'd need " + needed + " hands for " + itemDescription + " (you only have 2");
        if (shield) {
            sb.append(", one's holding your shield");
        }
        if (offHand) {
            sb.append(shield ? " and" : ",").append(" one's holding your off-hand weapon");
        }
        if (isBearer) {
            sb.append(shield || offHand ? " and" : ",").append(" one's holding the party's light");
        }
        return sb.append(").").toString();
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<item name>");
        help.addUsage("shield");
        help.addUsage("unshield");
        help.addUsage("offhand <item name>");
        help.addUsage("unoffhand");
        help.addDescription("Wields a recognized item from your inventory as your main weapon "
                + "(melee, reach, or missile — see `party`/`order` for how rank affects what you can use). "
                + "Every character has two hands: a two-handed weapon, a shield, an off-hand weapon, and "
                + "carrying the party's torch/lantern each cost one, so a change that would exceed two is "
                + "refused. `wield shield`/`unshield` raises or stows your shield. `wield offhand <item>` "
                + "takes up a one-handed melee/reach weapon in your off hand for +1 to attack (no shield "
                + "while dual-wielding — the budget won't allow both); `unoffhand` stows it.");
    }
}
