package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.Hands;
import dev.freitas.delve.game.engine.WeaponCatalog;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Component;

/** Switches your main weapon to a recognized inventory item, or toggles your shield:
    {@code /wield <item name>}, {@code /wield shield}, {@code /wield unshield}. */
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
                    + "Or `wield shield` / `wield unshield` to adjust your shield.");
            return;
        }
        Character character = save.getCharacter();
        boolean isBearer = SaveGame.PLAYER_SLOT.equalsIgnoreCase(save.getSession().getLightBearer());

        if (name.equalsIgnoreCase("unshield")) {
            character.setShield(false);
            ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
            ctx.reply("You stow your shield, freeing a hand.");
            return;
        }
        if (name.equalsIgnoreCase("shield")) {
            if (!Hands.fits(character.getMainWeapon(), true, isBearer)) {
                ctx.reply(handsFullMessage("a shield", WeaponCatalog.handsRequired(character.getMainWeapon()),
                        true, isBearer));
                return;
            }
            character.setShield(true);
            ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
            ctx.reply("You raise your shield.");
            return;
        }

        String match = null;
        for (String item : character.getInventory()) {
            if (item.toLowerCase().contains(name.toLowerCase())) {
                match = item;
                break;
            }
        }
        if (match == null) {
            ctx.reply("You don't have **" + name + "** in your inventory.");
            return;
        }
        if (!Hands.fits(match, character.isShield(), isBearer)) {
            ctx.reply(handsFullMessage("**" + match + "**", WeaponCatalog.handsRequired(match),
                    character.isShield(), isBearer));
            return;
        }
        character.setMainWeapon(match);
        character.setMainWeaponDamage(WeaponCatalog.damageFor(match));
        ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
        ctx.reply("You wield **" + match + "**.");
    }

    private String handsFullMessage(String itemDescription, int weaponHands, boolean shield, boolean isBearer) {
        int needed = weaponHands + (shield ? 1 : 0) + (isBearer ? 1 : 0);
        StringBuilder sb = new StringBuilder(
                "You'd need " + needed + " hands for " + itemDescription + " (you only have 2");
        if (shield) {
            sb.append(", one's holding your shield");
        }
        if (isBearer) {
            sb.append(shield ? " and" : ",").append(" one's holding the party's light");
        }
        return sb.append(").").toString();
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<item name>");
        help.addUsage("shield");
        help.addUsage("unshield");
        help.addDescription("Wields a recognized item from your inventory as your main weapon "
                + "(melee, reach, or missile — see `party`/`order` for how rank affects what you can use). "
                + "Every character has two hands: a two-handed weapon, a shield, and carrying the party's "
                + "torch/lantern each cost one, so a change that would exceed two is refused. `wield shield`/"
                + "`wield unshield` raises or stows your shield.");
    }
}
