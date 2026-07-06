package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.BankService;
import org.springframework.stereotype.Component;

/** Views a PC's bank balance, or deposits/withdraws gold at a town bank — gygax75-rules: banked gold no
    longer counts toward what you're carrying, at the cost of a 3% deposit fee (withdrawing is free):
    {@code /bank [pc-name]}, {@code /bank deposit [pc-name] <gold>}, {@code /bank withdraw [pc-name]
    <gold>}. */
@Component
public class BankCommand extends Command {

    private final BankService bank;

    public BankCommand(BankService bank) {
        super("bank");
        this.bank = bank;
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
            case "deposit" -> transfer(ctx, save, rest, true);
            case "withdraw" -> transfer(ctx, save, rest, false);
            default -> view(ctx, save, argsText);
        }
    }

    /** Works anywhere (informational, like {@code /sheet}) — viewing your own balance needs no trip to
        town, only moving gold in or out of it does. */
    private void view(CommandContext ctx, SaveGame save, String argsText) {
        SaveGame.PcNameToken peeled = save.peelLeadingPcName(argsText, save.getCharacter());
        Character pc = peeled.actor();
        boolean solo = save.getCharacters().size() == 1;
        String prefix = solo ? "" : pc.getName() + ": ";
        ctx.reply(prefix + "**" + pc.getGold() + " gp** on hand, **" + pc.getBankedGold() + " gp** banked.");
    }

    private void transfer(CommandContext ctx, SaveGame save, String argsText, boolean depositing) {
        if (save.getSession().isInDungeon()) {
            ctx.reply("You can only reach the bank in town.");
            return;
        }
        SaveGame.PcNameToken peeled = save.peelLeadingPcName(argsText, save.getCharacter());
        Character pc = peeled.actor();
        String amountText = peeled.remainder();
        int amount;
        try {
            amount = Integer.parseInt(amountText.trim());
        } catch (NumberFormatException e) {
            ctx.reply("How much gold? `bank " + (depositing ? "deposit" : "withdraw") + " [pc-name] <gold>`.");
            return;
        }
        if (amount <= 0) {
            ctx.reply("Amount must be a positive number of gold.");
            return;
        }

        boolean solo = save.getCharacters().size() == 1;
        String who = solo ? "You" : pc.getName();
        if (depositing) {
            BankService.DepositResult result = bank.deposit(pc, amount);
            if (result.deposited() == 0) {
                ctx.reply(who + (solo ? " have" : " has") + " no gold on hand to deposit.");
                return;
            }
            ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
            String shortNote = result.deposited() < amount ? " (that's all " + (solo ? "you have" : "they have")
                    + " on hand.)" : "";
            ctx.reply(who + (solo ? " deposit" : " deposits") + " **" + result.deposited() + " gp**"
                    + (result.fee() > 0 ? " (a " + result.fee() + " gp fee, " + result.credited() + " gp credited)"
                            : "") + " — " + pc.getBankedGold() + " gp now banked, " + pc.getGold()
                    + " gp on hand." + shortNote);
        } else {
            int moved = bank.withdraw(pc, amount);
            if (moved == 0) {
                ctx.reply(who + (solo ? " have" : " has") + " no gold banked to withdraw.");
                return;
            }
            ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
            String shortNote = moved < amount ? " (that's all " + (solo ? "you had" : "they had") + " banked.)" : "";
            ctx.reply(who + (solo ? " withdraw" : " withdraws") + " **" + moved + " gp** from the bank — "
                    + pc.getGold() + " gp on hand, " + pc.getBankedGold() + " gp still banked." + shortNote);
        }
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[pc-name]");
        help.addUsage("deposit [pc-name] <gold>");
        help.addUsage("withdraw [pc-name] <gold>");
        help.addDescription("Views a PC's bank balance (works anywhere), or deposits/withdraws gold at a "
                + "town bank (town only). Banked gold no longer counts toward what you're carrying — it "
                + "isn't weighing you down or available to spend until withdrawn. Depositing takes a 3% "
                + "handling fee off the top; withdrawing is free. In a multi-PC party, name a PC first; "
                + "defaults to your first-rolled PC.");
    }
}
