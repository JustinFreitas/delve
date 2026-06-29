package dev.freitas.delve.command;

import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.ExplorationService;
import org.springframework.stereotype.Component;

/** Searches the current room for secret doors, traps and treasure: {@code /search} (one dungeon turn). */
@Component
public class SearchCommand extends DungeonCommand {

    private final ExplorationService exploration;

    public SearchCommand(ExplorationService exploration) {
        super("search", "s");
        this.exploration = exploration;
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = requireDelving(ctx);
        if (save == null) {
            return;
        }
        ExplorationResult result = exploration.search(save);
        persist(ctx, save);
        ctx.reply(result.text());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("");
        help.addDescription("Searches the room for secret doors, traps and treasure. Takes one dungeon turn.");
    }
}
