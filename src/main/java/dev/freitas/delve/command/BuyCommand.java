package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.GearCatalog;
import dev.freitas.delve.game.engine.Hands;
import dev.freitas.delve.game.engine.LightSource;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Component;

/** Buys light-source supplies, a shield, armor, or any {@link GearCatalog}-priced weapon/gear item in
    town: {@code /buy [pc-name] <item> [qty]}. */
@Component
public class BuyCommand extends Command {

    public BuyCommand() {
        super("buy", "shop");
    }

    @Override
    public void invoke(CommandContext ctx) {
        long userId = ctx.getInvokerUserId();
        SaveGame save = ctx.getBeans().gameState.load(userId);
        if (!save.hasCharacter()) {
            ctx.reply("Roll a character first with `" + ctx.getPrefix() + "roll-character <class>`.");
            return;
        }
        if (save.getSession().isInDungeon()) {
            ctx.reply("You can only buy supplies in town.");
            return;
        }
        String argsText = ctx.getArgumentText().trim();

        // An optional leading PC-name names whose gold pays and whose inventory/gear changes.
        Character resolved = save.getCharacter();
        int leadSpace = argsText.indexOf(' ');
        String leadToken = leadSpace > 0 ? argsText.substring(0, leadSpace) : argsText;
        if (leadSpace > 0 && save.resolve(leadToken) instanceof Character named) {
            resolved = named;
            argsText = argsText.substring(leadSpace + 1).trim();
        }
        final Character pc = resolved;
        boolean solo = save.getCharacters().size() == 1;
        String prefix = solo ? "" : pc.getName() + ": ";

        String[] tokens = argsText.split("\\s+", 2);
        String item = tokens[0];
        if (item.isBlank()) {
            ctx.reply(prefix + "Buy what? `buy [pc-name] <item> [qty]` — a weapon, armor, a shield, "
                    + "torches/lanterns/oil, or common gear. Check `sheet` for your gold.");
            return;
        }
        int qty = 1;
        if (tokens.length > 1 && !tokens[1].isBlank()) {
            try {
                qty = Math.max(1, Integer.parseInt(tokens[1].trim()));
            } catch (NumberFormatException e) {
                ctx.reply(prefix + "Quantity must be a number.");
                return;
            }
        }

        String lower = item.toLowerCase();

        if (lower.equals("shield")) {
            buyShield(ctx, save, pc, prefix);
            return;
        }
        Armor armorTier = armorTierFor(lower);
        if (armorTier != null) {
            buyArmor(ctx, save, pc, armorTier, prefix);
            return;
        }

        int unitCost;
        String label;
        Runnable apply;
        boolean genericGear = false;
        boolean lightSupply = false;
        if (item.equalsIgnoreCase("torch") || item.equalsIgnoreCase("torches")) {
            unitCost = LightSource.TORCH.itemCostGp();
            label = "torch" + (qty == 1 ? "" : "es");
            int finalQty = qty;
            apply = () -> pc.setTorches(pc.getTorches() + finalQty);
            lightSupply = true;
        } else if (item.equalsIgnoreCase("lantern") || item.equalsIgnoreCase("lanterns")) {
            unitCost = LightSource.LANTERN.itemCostGp();
            label = "lantern" + (qty == 1 ? "" : "s");
            int finalQty = qty;
            apply = () -> pc.setLanterns(pc.getLanterns() + finalQty);
            lightSupply = true;
        } else if (item.equalsIgnoreCase("oil") || item.equalsIgnoreCase("flask") || item.equalsIgnoreCase("flasks")) {
            unitCost = LightSource.OIL_FLASK_COST_GP;
            label = "flask" + (qty == 1 ? "" : "s") + " of oil";
            int finalQty = qty;
            apply = () -> pc.setOilFlasks(pc.getOilFlasks() + finalQty);
            lightSupply = true;
        } else {
            int price = GearCatalog.priceGp(item);
            if (price < 0) {
                ctx.reply(prefix + "Don't recognize **" + item + "**. Buy a weapon, armor, a shield, "
                        + "`torch`/`lantern`/`oil`, or common gear (check `help buy`).");
                return;
            }
            unitCost = price;
            label = item + (qty == 1 ? "" : " ×" + qty);
            int finalQty = qty;
            genericGear = true;
            apply = () -> {
                for (int i = 0; i < finalQty; i++) {
                    pc.getInventory().add(item);
                }
            };
        }

        int total = unitCost * qty;
        if (pc.getGold() < total) {
            ctx.reply(prefix + "You need **" + total + " gp** for " + qty + " " + label + "; you have "
                    + pc.getGold() + " gp.");
            return;
        }
        pc.setGold(pc.getGold() - total);
        apply.run();
        ctx.getBeans().gameState.save(userId, save);
        String wieldHint = genericGear ? " `wield " + item + "` to equip it." : "";
        // Torch/lantern/oil fuel is a shared party resource drawn only from the primary PC's stock
        // (LightingService's existing design) — flag it plainly if bought for someone else instead.
        String lightCaveat = lightSupply && pc != save.getCharacter()
                ? " (note: only your first-rolled PC's light supplies fuel the party's shared torch/lantern today.)"
                : "";
        ctx.reply(prefix + "Bought " + qty + " " + label + " for **" + total + " gp**; " + pc.getGold()
                + " gp left." + wieldHint + lightCaveat);
    }

    private void buyShield(CommandContext ctx, SaveGame save, Character pc, String prefix) {
        if (pc.isShield()) {
            ctx.reply(prefix + "You already have a shield.");
            return;
        }
        int cost = GearCatalog.priceGp("shield");
        boolean isBearer = save.tokenFor(pc).equalsIgnoreCase(save.getSession().getLightBearer());
        if (!Hands.fits(pc.getMainWeapon(), true, isBearer, pc.getOffHandWeapon() != null)) {
            ctx.reply(prefix + "You don't have a free hand for a shield.");
            return;
        }
        if (pc.getGold() < cost) {
            ctx.reply(prefix + "You need **" + cost + " gp** for a shield; you have " + pc.getGold() + " gp.");
            return;
        }
        pc.setGold(pc.getGold() - cost);
        pc.setShield(true);
        ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
        ctx.reply(prefix + "Bought a shield for **" + cost + " gp** and raise it; " + pc.getGold() + " gp left.");
    }

    private void buyArmor(CommandContext ctx, SaveGame save, Character pc, Armor tier, String prefix) {
        if (pc.getArmor() == tier) {
            ctx.reply(prefix + "You're already wearing " + tier.displayName().toLowerCase() + ".");
            return;
        }
        int cost = GearCatalog.priceGp(tier.displayName());
        if (pc.getGold() < cost) {
            ctx.reply(prefix + "You need **" + cost + " gp** for " + tier.displayName().toLowerCase()
                    + "; you have " + pc.getGold() + " gp.");
            return;
        }
        pc.setGold(pc.getGold() - cost);
        pc.setArmor(tier);
        ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
        ctx.reply(prefix + "Bought and put on **" + tier.displayName() + "** for **" + cost + " gp**; "
                + pc.getGold() + " gp left.");
    }

    /** Package-private: tests exercise this directly without a {@link CommandContext}. */
    Armor armorTierFor(String lower) {
        if (lower.contains("plate")) {
            return Armor.PLATE_MAIL;
        }
        if (lower.contains("chain")) {
            return Armor.CHAIN_MAIL;
        }
        if (lower.contains("leather")) {
            return Armor.LEATHER;
        }
        return null;
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[pc-name] <item> [qty]");
        help.addDescription("Buys supplies in town: a weapon or common gear (added to inventory — "
                + "`wield` it to equip), armor (`leather`/`chain mail`/`plate mail`, worn immediately), "
                + "`shield` (needs a free hand), or light supplies: torches (" + LightSource.TORCH.itemCostGp()
                + " gp each, disposable, 6 turns of light), a lantern (" + LightSource.LANTERN.itemCostGp()
                + " gp, reusable), or oil flasks (" + LightSource.OIL_FLASK_COST_GP
                + " gp each, a lantern's fuel, 24 turns per flask). In a multi-PC party, name a PC first "
                + "to spend their gold instead of your first-rolled PC's (light supplies still only fuel "
                + "the party's shared torch/lantern when bought for the first-rolled PC).");
    }
}
