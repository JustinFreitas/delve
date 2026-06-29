package dev.freitas.delve.command;

import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.ExplorationService;
import org.springframework.stereotype.Component;

/** Describes the current room without any time passing: {@code /look}. */
@Component
public class LookCommand extends DungeonCommand {

    private final ExplorationService exploration;

    public LookCommand(ExplorationService exploration) {
        super("look", "l");
        this.exploration = exploration;
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = requireDelving(ctx);
        if (save == null) {
            return;
        }
        ctx.reply(exploration.look(save.getSession()).text());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("");
        help.addDescription("Looks around the current room (costs no time).");
    }
}
