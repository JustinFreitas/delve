package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.LightSource;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Component;

/** Buys light-source supplies in town: {@code /buy <torch|lantern|oil> [qty]}. */
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
            ctx.reply("Buy what? `buy <torch|lantern|oil> [qty]`.");
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
        int unitCost;
        String label;
        Runnable apply;
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
            ctx.reply("Don't recognize **" + item + "**. Buy `torch`, `lantern`, or `oil`.");
            return;
        }

        int total = unitCost * qty;
        if (pc.getGold() < total) {
            ctx.reply("You need **" + total + " gp** for " + qty + " " + label + "; you have " + pc.getGold() + " gp.");
            return;
        }
        pc.setGold(pc.getGold() - total);
        apply.run();
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply("Bought " + qty + " " + label + " for **" + total + " gp**; " + pc.getGold() + " gp left.");
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<torch|lantern|oil> [qty]");
        help.addDescription("Buys light supplies in town: torches (" + LightSource.TORCH.itemCostGp()
                + " gp each, disposable, 6 turns of light), a lantern (" + LightSource.LANTERN.itemCostGp()
                + " gp, reusable), or oil flasks (" + LightSource.OIL_FLASK_COST_GP
                + " gp each, a lantern's fuel, 24 turns per flask).");
    }
}
