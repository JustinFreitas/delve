package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Component;

/** Shows a character sheet: {@code /sheet [pc-name]}. */
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
        String token = ctx.getArgumentText().trim();
        Character pc = token.isBlank() ? save.getCharacter()
                : (save.resolve(token) instanceof Character c ? c : null);
        if (pc == null) {
            ctx.reply("Don't recognize **" + token + "**. Use `me`/a character's name from `"
                    + ctx.getPrefix() + "party`.");
            return;
        }
        ctx.replyEmbed(CharacterSheets.embed(pc));
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[pc-name]");
        help.addDescription("Displays a character sheet — your first-rolled PC by default, or name a "
                + "specific PC from `party` in a multi-PC party.");
    }
}
