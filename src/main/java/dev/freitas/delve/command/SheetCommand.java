package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Component;

/** Shows the invoker's current character sheet: {@code /sheet}. */
@Component
public class SheetCommand extends Command {

    public SheetCommand() {
        super("sheet", "character", "char");
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = ctx.getBeans().gameState.load(ctx.getInvokerUserId());
        if (!save.hasCharacter()) {
            ctx.reply("You don't have a character yet. Roll one with `" + ctx.getPrefix()
                    + "roll-character <class>`.");
            return;
        }
        ctx.replyEmbed(CharacterSheets.embed(save.getCharacter()));
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("");
        help.addDescription("Displays your current character sheet.");
    }
}
