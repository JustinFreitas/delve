package dev.freitas.delve.command;

import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Component;

/** Toggles probing ahead with a 10-foot pole: {@code /pole [on|off]}. */
@Component
public class PoleCommand extends DungeonCommand {

    public PoleCommand() {
        super("pole");
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = requireDelving(ctx);
        if (save == null) {
            return;
        }
        String arg = ctx.getArgumentText().trim().toLowerCase();
        boolean poling;
        if (arg.isBlank()) {
            boolean current = save.getSession().isPolingFrontRank();
            ctx.reply("The front rank is " + (current ? "" : "not ") + "probing ahead with a 10-foot pole.");
            return;
        } else if (arg.equals("on")) {
            poling = true;
        } else if (arg.equals("off")) {
            poling = false;
        } else {
            ctx.reply("Usage: `pole [on|off]`.");
            return;
        }
        save.getSession().setPolingFrontRank(poling);
        persist(ctx, save);
        ctx.reply(poling
                ? "The front rank extends a 10-foot pole ahead, probing for traps — but won't have a weapon "
                        + "ready if surprised."
                : "The front rank puts the pole away, weapons back in hand.");
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[on|off]");
        help.addDescription("Toggles the front rank probing ahead with a 10-foot pole: passive trap "
                + "detection, at the risk of an AC penalty if surprised.");
    }
}
