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
    town: {@code /buy <item> [qty]}. */
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
        String[] tokens = ctx.getArgumentText().trim().split("\\s+", 2);
        String item = tokens[0];
        if (item.isBlank()) {
            ctx.reply("Buy what? `buy <item> [qty]` — a weapon, armor, a shield, torches/lanterns/oil, "
                    + "or common gear. Check `sheet` for your gold.");
            return;
        }
        int qty = 1;
        if (tokens.length > 1 && !tokens[1].isBlank()) {
            try {
                qty = Math.max(1, Integer.parseInt(tokens[1].trim()));
            } catch (NumberFormatException e) {
                ctx.reply("Quantity must be a number.");
                return;
            }
        }

        Character pc = save.getCharacter();
        String lower = item.toLowerCase();

        if (lower.equals("shield")) {
            buyShield(ctx, save, pc);
            return;
        }
        Armor armorTier = armorTierFor(lower);
        if (armorTier != null) {
            buyArmor(ctx, save, pc, armorTier);
            return;
        }

        int unitCost;
        String label;
        Runnable apply;
        boolean genericGear = false;
        if (item.equalsIgnoreCase("torch") || item.equalsIgnoreCase("torches")) {
            unitCost = LightSource.TORCH.itemCostGp();
            label = "torch" + (qty == 1 ? "" : "es");
            int finalQty = qty;
            apply = () -> pc.setTorches(pc.getTorches() + finalQty);
        } else if (item.equalsIgnoreCase("lantern") || item.equalsIgnoreCase("lanterns")) {
            unitCost = LightSource.LANTERN.itemCostGp();
            label = "lantern" + (qty == 1 ? "" : "s");
            int finalQty = qty;
            apply = () -> pc.setLanterns(pc.getLanterns() + finalQty);
        } else if (item.equalsIgnoreCase("oil") || item.equalsIgnoreCase("flask") || item.equalsIgnoreCase("flasks")) {
            unitCost = LightSource.OIL_FLASK_COST_GP;
            label = "flask" + (qty == 1 ? "" : "s") + " of oil";
            int finalQty = qty;
            apply = () -> pc.setOilFlasks(pc.getOilFlasks() + finalQty);
        } else {
            int price = GearCatalog.priceGp(item);
            if (price < 0) {
                ctx.reply("Don't recognize **" + item + "**. Buy a weapon, armor, a shield, "
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
            ctx.reply("You need **" + total + " gp** for " + qty + " " + label + "; you have " + pc.getGold() + " gp.");
            return;
        }
        pc.setGold(pc.getGold() - total);
        apply.run();
        ctx.getBeans().gameState.save(userId, save);
        String wieldHint = genericGear ? " `wield " + item + "` to equip it." : "";
        ctx.reply("Bought " + qty + " " + label + " for **" + total + " gp**; " + pc.getGold() + " gp left." + wieldHint);
    }

    private void buyShield(CommandContext ctx, SaveGame save, Character pc) {
        if (pc.isShield()) {
            ctx.reply("You already have a shield.");
            return;
        }
        int cost = GearCatalog.priceGp("shield");
        boolean isBearer = SaveGame.PLAYER_SLOT.equalsIgnoreCase(save.getSession().getLightBearer());
        if (!Hands.fits(pc.getMainWeapon(), true, isBearer, pc.getOffHandWeapon() != null)) {
            ctx.reply("You don't have a free hand for a shield.");
            return;
        }
        if (pc.getGold() < cost) {
            ctx.reply("You need **" + cost + " gp** for a shield; you have " + pc.getGold() + " gp.");
            return;
        }
        pc.setGold(pc.getGold() - cost);
        pc.setShield(true);
        ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
        ctx.reply("Bought a shield for **" + cost + " gp** and raise it; " + pc.getGold() + " gp left.");
    }

    private void buyArmor(CommandContext ctx, SaveGame save, Character pc, Armor tier) {
        if (pc.getArmor() == tier) {
            ctx.reply("You're already wearing " + tier.displayName().toLowerCase() + ".");
            return;
        }
        int cost = GearCatalog.priceGp(tier.displayName());
        if (pc.getGold() < cost) {
            ctx.reply("You need **" + cost + " gp** for " + tier.displayName().toLowerCase()
                    + "; you have " + pc.getGold() + " gp.");
            return;
        }
        pc.setGold(pc.getGold() - cost);
        pc.setArmor(tier);
        ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
        ctx.reply("Bought and put on **" + tier.displayName() + "** for **" + cost + " gp**; "
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
        help.addUsage("<item> [qty]");
        help.addDescription("Buys supplies in town: a weapon or common gear (added to inventory — "
                + "`wield` it to equip), armor (`leather`/`chain mail`/`plate mail`, worn immediately), "
                + "`shield` (needs a free hand), or light supplies: torches (" + LightSource.TORCH.itemCostGp()
                + " gp each, disposable, 6 turns of light), a lantern (" + LightSource.LANTERN.itemCostGp()
                + " gp, reusable), or oil flasks (" + LightSource.OIL_FLASK_COST_GP
                + " gp each, a lantern's fuel, 24 turns per flask).");
    }
}
