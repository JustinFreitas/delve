package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.MuleFactory;
import dev.freitas.delve.game.engine.MuleRules;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Mule;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.MuleService;
import org.springframework.stereotype.Component;

/** Manages the party's pack mule: {@code /mule buy [pc-name] [name]}, {@code /mule load [pc-name]
    <gold>}, {@code /mule unload [pc-name] <gold>}, {@code /mule handler <name>}. Only one mule per party
    (gygax75-rules: "a single mule may be brought into a dungeon by a party"), bought in town for
    {@link MuleRules#PURCHASE_COST_GP} gp and carrying up to {@link MuleRules#CAPACITY_MAX_CNS} gold
    (1 coin = 1 cn) before it's too heavy to lead further. It's a real OSE-statted combatant (AC/HP/
    THAC0) that takes a marching-order slot and can be attacked — see {@link Mule}. */
@Component
public class MuleCommand extends Command {

    private static final String DEFAULT_NAME = "Mule";

    private final MuleService muleService;
    private final MuleFactory muleFactory;

    public MuleCommand(MuleService muleService, MuleFactory muleFactory) {
        super("mule");
        this.muleService = muleService;
        this.muleFactory = muleFactory;
    }

    @Override
    public void invoke(CommandContext ctx) {
        long userId = ctx.getInvokerUserId();
        SaveGame save = ctx.getBeans().gameState.load(userId);
        if (!save.hasCharacter()) {
            ctx.reply("Roll a character first with `" + ctx.getPrefix() + "roll-character <class>`.");
            return;
        }

        String argsText = ctx.getArgumentText().trim();
        String[] tokens = argsText.split("\\s+", 2);
        String action = tokens.length > 0 ? tokens[0].toLowerCase() : "";
        String rest = tokens.length > 1 ? tokens[1].trim() : "";

        switch (action) {
            case "buy" -> buy(ctx, save, rest);
            case "load" -> transfer(ctx, save, rest, true);
            case "unload" -> transfer(ctx, save, rest, false);
            case "handler" -> handler(ctx, save, rest);
            default -> ctx.reply("`mule buy [pc-name] [name]`, `mule load [pc-name] <gold>`, "
                    + "`mule unload [pc-name] <gold>`, or `mule handler <name>`. Check `" + ctx.getPrefix()
                    + "party` for its status.");
        }
    }

    private void buy(CommandContext ctx, SaveGame save, String argsText) {
        if (save.getSession().isInDungeon()) {
            ctx.reply("You can only buy a mule in town.");
            return;
        }
        if (save.getMules().size() >= SaveGame.MAX_MULES) {
            ctx.reply("Your party already has a mule — only one may come along at a time. "
                    + "`" + ctx.getPrefix() + "dismiss " + save.getMule().getName() + "` first.");
            return;
        }
        SaveGame.PcNameToken peeled = save.peelLeadingPcName(argsText, save.getCharacter());
        Character payer = peeled.actor();
        String name = peeled.remainder().isBlank() ? DEFAULT_NAME : peeled.remainder();
        boolean solo = save.getCharacters().size() == 1;

        if (payer.getGold() < MuleRules.PURCHASE_COST_GP) {
            ctx.reply("You need **" + MuleRules.PURCHASE_COST_GP + " gp** to buy a mule; "
                    + (solo ? "you have" : payer.getName() + " has") + " " + payer.getGold() + " gp.");
            return;
        }

        Mule mule = buyOne(save, payer, name);
        String handler = mule.getHandler();

        ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
        String handlerNote = handler != null
                ? " " + (save.resolve(handler) instanceof Character ? "You" : handler) + " take"
                        + (save.resolve(handler) instanceof Character ? "" : "s") + " the lead."
                : " Nobody has a free hand to lead it yet — see `" + ctx.getPrefix() + "mule handler <name>`.";
        ctx.reply("**" + name + "** (AC " + mule.armorClass() + ", " + mule.getMaxHp() + " hp) joins "
                + (solo ? "you" : payer.getName() + "'s party") + " for **" + MuleRules.PURCHASE_COST_GP
                + " gp**, carrying up to " + MuleRules.CAPACITY_MAX_CNS + " gold before it's too heavy to lead."
                + handlerNote);
    }

    /** Creates and buys one mule for {@code payer}, deducting {@link MuleRules#PURCHASE_COST_GP} and
        auto-assigning an eligible handler. Caller must already have checked gold/town/cap. Package-
        private: tests exercise this directly without a {@link CommandContext}. */
    Mule buyOne(SaveGame save, Character payer, String name) {
        Mule mule = muleFactory.create(name);
        mule.setOwner(payer.getName());
        payer.setGold(payer.getGold() - MuleRules.PURCHASE_COST_GP);
        save.getMules().add(mule);
        mule.setHandler(muleService.pickEligibleHandler(save));
        return mule;
    }

    private void transfer(CommandContext ctx, SaveGame save, String argsText, boolean loading) {
        Mule mule = save.getMule();
        if (mule == null) {
            ctx.reply("You don't have a mule. `" + ctx.getPrefix() + "mule buy` to get one in town.");
            return;
        }
        SaveGame.PcNameToken peeled = save.peelLeadingPcName(argsText, save.getCharacter());
        Character pc = peeled.actor();
        String amountText = peeled.remainder();
        int amount;
        try {
            amount = Integer.parseInt(amountText.trim());
        } catch (NumberFormatException e) {
            ctx.reply("How much gold? `mule " + (loading ? "load" : "unload") + " [pc-name] <gold>`.");
            return;
        }
        if (amount <= 0) {
            ctx.reply("Amount must be a positive number of gold.");
            return;
        }

        boolean solo = save.getCharacters().size() == 1;
        String who = solo ? "You" : pc.getName();
        if (loading) {
            int moved = muleService.load(mule, pc, amount);
            if (moved == 0) {
                ctx.reply(mule.getName() + " can't carry any more — it's at its "
                        + MuleRules.CAPACITY_MAX_CNS + " gold limit, or " + who.toLowerCase() + " "
                        + (solo ? "have" : "has") + " no gold to load.");
                return;
            }
            ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
            String shortNote = moved < amount ? " (" + mule.getName() + " couldn't take the rest — it's full.)" : "";
            ctx.reply(who + " load" + (solo ? "" : "s") + " **" + moved + " gp** onto " + mule.getName()
                    + " (" + mule.getCarriedGold() + "/" + MuleRules.CAPACITY_MAX_CNS + " gp, "
                    + MuleRules.descriptor(mule.getCarriedGold()) + ")." + shortNote);
        } else {
            int moved = muleService.unload(mule, pc, amount);
            if (moved == 0) {
                ctx.reply(mule.getName() + " isn't carrying any gold to unload.");
                return;
            }
            ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
            String shortNote = moved < amount ? " (that's all " + mule.getName() + " was carrying.)" : "";
            ctx.reply(who + " unload" + (solo ? "" : "s") + " **" + moved + " gp** from " + mule.getName()
                    + " (" + mule.getCarriedGold() + "/" + MuleRules.CAPACITY_MAX_CNS + " gp left on it)."
                    + shortNote);
        }
    }

    private void handler(CommandContext ctx, SaveGame save, String name) {
        Mule mule = save.getMule();
        if (mule == null) {
            ctx.reply("You don't have a mule. `" + ctx.getPrefix() + "mule buy` to get one in town.");
            return;
        }
        if (name.isBlank()) {
            String current = mule.getHandler();
            ctx.reply(current == null
                    ? mule.getName() + " has no handler right now — nobody has a free hand."
                    : mule.getName() + "'s handler is **" + current + "**.");
            return;
        }
        String failure = muleService.assignHandler(save, mule, name);
        if (failure != null) {
            ctx.reply(failure);
            return;
        }
        ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
        ctx.reply("**" + mule.getHandler() + "** now leads " + mule.getName() + ".");
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("buy [pc-name] [name]");
        help.addUsage("load [pc-name] <gold>");
        help.addUsage("unload [pc-name] <gold>");
        help.addUsage("handler <name>");
        help.addDescription("Buys a pack mule in town (" + MuleRules.PURCHASE_COST_GP + " gp; only one per "
                + "party) to haul gold so it doesn't weigh your PCs down. AC " + MuleRules.ARMOR_CLASS
                + ", takes a marching-order slot (`/order`) and can be attacked and killed — it won't fight "
                + "back on its own, and losing it spills whatever gold it's carrying: living PCs recover as "
                + "much as their own remaining carry capacity allows, the rest is left behind. Carries up to "
                + MuleRules.CAPACITY_MAX_CNS + " gold — past " + MuleRules.CAPACITY_LIGHT_CNS
                + " it's heavily loaded and slows to 60'/turn (affects the party's flee speed); load/unload "
                + "gold onto it, and name a handler (a PC or retainer with a free hand) to lead it.");
    }
}
