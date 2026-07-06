package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.ContainerRules;
import dev.freitas.delve.game.engine.ContainerType;
import dev.freitas.delve.game.engine.GearCatalog;
import dev.freitas.delve.game.engine.Hands;
import dev.freitas.delve.game.engine.LightSource;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Container;
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
        ContainerType containerType = containerTypeFor(lower);
        if (containerType != null) {
            buyContainer(ctx, save, pc, containerType, qty, prefix);
            return;
        }

        int unitCost;
        String label;
        Runnable apply;
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
            buyGear(ctx, save, pc, item, qty, prefix);
            return;
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
        // Torch/lantern/oil fuel is a shared party resource drawn only from the primary PC's stock
        // (LightingService's existing design) — flag it plainly if bought for someone else instead.
        String lightCaveat = lightSupply && pc != save.getCharacter()
                ? " (note: only your first-rolled PC's light supplies fuel the party's shared torch/lantern today.)"
                : "";
        ctx.reply(prefix + "Bought " + qty + " " + label + " for **" + total + " gp**; " + pc.getGold()
                + " gp left." + lightCaveat);
    }

    private void buyContainer(CommandContext ctx, SaveGame save, Character pc, ContainerType type, int qty, String prefix) {
        int cost = GearCatalog.priceGp(type.displayName());
        int goldBefore = pc.getGold();
        int bought = buyContainer(save, pc, type, qty);
        String label = type.displayName().toLowerCase();
        if (bought == 0) {
            if (goldBefore < cost) {
                ctx.reply(prefix + "You need **" + cost + " gp** for a " + label + "; you have " + goldBefore + " gp.");
            } else {
                ctx.reply(prefix + "You have no free hand to carry another " + label + ".");
            }
            return;
        }
        ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
        ctx.reply(prefix + "Bought " + bought + " " + label + (bought == 1 ? "" : "s") + " for **"
                + (goldBefore - pc.getGold()) + " gp**; " + pc.getGold() + " gp left."
                + (bought < qty ? " (No room for the rest.)" : ""));
    }

    /** Buys up to {@code qty} of a backpack or sack: worn (no hand cost) if a worn slot of that type is
        still free (at most one worn backpack + one worn small sack; a large sack is never worn), else
        held if a hand is free for one more — stops (per unit) once neither is true or gold runs out.
        Mutates {@code pc}; returns how many were actually bought (0 if none). Package-private: tests
        exercise this directly without a {@link CommandContext}, matching this codebase's established
        pattern (see {@code MuleCommandTest}). */
    int buyContainer(SaveGame save, Character pc, ContainerType type, int qty) {
        int cost = GearCatalog.priceGp(type.displayName());
        boolean isBearer = save.tokenFor(pc).equalsIgnoreCase(save.getSession().getLightBearer());
        int bought = 0;
        for (int i = 0; i < qty && pc.getGold() >= cost; i++) {
            boolean worn = ContainerRules.canWearAnother(pc.getContainers(), type);
            if (!worn) {
                int heldSacks = ContainerRules.heldCount(pc.getContainers());
                if (!Hands.fits(pc.getMainWeapon(), pc.isShield(), isBearer, pc.getOffHandWeapon() != null, heldSacks + 1)) {
                    break;
                }
            }
            pc.getContainers().add(new Container(type, !worn, pc.getName()));
            pc.setGold(pc.getGold() - cost);
            bought++;
        }
        return bought;
    }

    private void buyGear(CommandContext ctx, SaveGame save, Character pc, String item, int qty, String prefix) {
        int price = GearCatalog.priceGp(item);
        if (price < 0) {
            ctx.reply(prefix + "Don't recognize **" + item + "**. Buy a weapon, armor, a shield, "
                    + "`torch`/`lantern`/`oil`, or common gear (check `help buy`).");
            return;
        }
        int goldBefore = pc.getGold();
        int bought = buyGear(save, pc, item, qty);
        if (bought == 0) {
            if (goldBefore < price) {
                ctx.reply(prefix + "You need **" + price + " gp** for " + item + "; you have " + goldBefore + " gp.");
            } else {
                ctx.reply(prefix + "You have nowhere to put that — buy a bigger sack.");
            }
            return;
        }
        ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
        String label = item + (bought == 1 ? "" : " ×" + bought);
        ctx.reply(prefix + "Bought " + label + " for **" + (goldBefore - pc.getGold()) + " gp**; " + pc.getGold()
                + " gp left. `wield " + item + "` to equip it." + (bought < qty ? " (No room for the rest.)" : ""));
    }

    /** Buys up to {@code qty} of any other {@link GearCatalog}-priced item (assumed already validated as
        recognized), gated by container room: an exempt item (see {@link ContainerRules#isExempt}) goes
        straight into the on-person {@code inventory} list as always; everything else needs a container
        with room (backpack, then a worn sack, then held sacks, via {@link ContainerRules#findRoomFor})
        and stops (per unit) — gold untouched for the rest — once none fits or gold runs out. Mutates
        {@code pc}; returns how many were actually bought (0 if none). Package-private: tests exercise
        this directly without a {@link CommandContext}. */
    int buyGear(SaveGame save, Character pc, String item, int qty) {
        int price = GearCatalog.priceGp(item);
        if (price < 0) {
            return 0;
        }
        boolean exempt = ContainerRules.isExempt(item);
        int weight = GearCatalog.weightCns(item);
        int bought = 0;
        for (int i = 0; i < qty && pc.getGold() >= price; i++) {
            Container home = exempt ? null : ContainerRules.findRoomFor(pc.getContainers(), weight);
            if (!exempt && home == null) {
                break;
            }
            if (exempt) {
                pc.getInventory().add(item);
            } else {
                home.getItems().add(item);
            }
            pc.setGold(pc.getGold() - price);
            bought++;
        }
        return bought;
    }

    /** Package-private: tests exercise this directly without a {@link CommandContext}. */
    ContainerType containerTypeFor(String lower) {
        if (lower.contains("backpack")) {
            return ContainerType.BACKPACK;
        }
        if (lower.contains("small") && lower.contains("sack")) {
            return ContainerType.SMALL_SACK;
        }
        if (lower.contains("large") && lower.contains("sack")) {
            return ContainerType.LARGE_SACK;
        }
        return null;
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
        help.addDescription("Buys supplies in town: a weapon or common gear (stowed in the first "
                + "container with room — `wield` a weapon to equip it; refused if nothing has space, "
                + "buy a bigger sack), armor (`leather`/`chain mail`/`plate mail`, worn immediately), "
                + "`shield` (needs a free hand), a `backpack`/`small sack`/`large sack` (a backpack and "
                + "one small sack are worn for free; any more are held, costing a hand each, and dropped "
                + "if a fight fills your hands), or light supplies: torches (" + LightSource.TORCH.itemCostGp()
                + " gp each, disposable, 6 turns of light), a lantern (" + LightSource.LANTERN.itemCostGp()
                + " gp, reusable), or oil flasks (" + LightSource.OIL_FLASK_COST_GP
                + " gp each, a lantern's fuel, 24 turns per flask). In a multi-PC party, name a PC first "
                + "to spend their gold instead of your first-rolled PC's (light supplies still only fuel "
                + "the party's shared torch/lantern when bought for the first-rolled PC).");
    }
}
