package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Component;

/** Lists the player character and their retainers: {@code /party}. */
@Component
public class PartyCommand extends Command {

    public PartyCommand() {
        super("party", "p");
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = ctx.getBeans().gameState.load(ctx.getInvokerUserId());
        if (!save.hasCharacter()) {
            ctx.reply("Roll a character first with `" + ctx.getPrefix() + "roll-character <class>`.");
            return;
        }
        ctx.reply(PartySummary.text(save));
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("");
        help.addDescription("Shows your character and hired retainers.");
    }
}
