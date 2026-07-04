package dev.freitas.delve.command;

import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.ExplorationService;
import org.springframework.stereotype.Component;

/** Rests for one dungeon turn, resetting the fatigue clock: {@code /rest}. */
@Component
public class RestCommand extends DungeonCommand {

    private final ExplorationService exploration;

    public RestCommand(ExplorationService exploration) {
        super("rest");
        this.exploration = exploration;
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = requireDelving(ctx);
        if (save == null) {
            return;
        }
        ExplorationResult result = exploration.rest(save);
        persist(ctx, save);
        ctx.reply(result.text());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("");
        help.addDescription("Rests for one dungeon turn. Characters must rest once every hour (6 turns) "
                + "or suffer a -1 penalty to attack and damage rolls until they do.");
    }
}
