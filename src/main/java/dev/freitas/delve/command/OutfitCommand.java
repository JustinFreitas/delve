package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.Outfitter;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Component;

/** Runs the same best-effort auto-gear {@code /roll-character} applies by default against an
    already-rolled PC: {@code /outfit [pc-name]}. For a PC rolled with the {@code bare} flag (or from
    before this feature existed), or one you've only partly shopped for by hand — spends whatever gold
    they currently have left, same as a fresh roll would. */
@Component
public class OutfitCommand extends Command {

    public OutfitCommand() {
        super("outfit", "gearup", "autogear");
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
            ctx.reply("You can only outfit up in town.");
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

        String summary = Outfitter.outfit(pc);
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply(pc.getName() + " " + summary);
        ctx.replyEmbed(CharacterSheets.embed(pc));
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[pc-name]");
        help.addDescription("Best-effort auto-gears an already-rolled PC with a class-appropriate "
                + "weapon, armor, shield, and torches, spending as much of their current gold as it can "
                + "afford (degrading tier-by-tier rather than buying nothing). The same thing "
                + "`/roll-character` does by default — useful for a PC rolled with `bare`, or one from "
                + "before this feature existed. In a multi-PC party, name which PC; defaults to your "
                + "first-rolled PC. Town only.");
    }
}
