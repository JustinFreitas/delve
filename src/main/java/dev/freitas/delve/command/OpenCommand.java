package dev.freitas.delve.command;

import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.ExplorationService;
import org.springframework.stereotype.Component;

/** Opens (or forces) a door in a direction: {@code /open <direction>}. */
@Component
public class OpenCommand extends DungeonCommand {

    private final ExplorationService exploration;

    public OpenCommand(ExplorationService exploration) {
        super("open", "o");
        this.exploration = exploration;
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = requireDelving(ctx);
        if (save == null) {
            return;
        }
        Direction direction = Direction.parse(ctx.getArgumentText().trim());
        if (direction == null) {
            ctx.reply("Open a door in which direction? Try `open north`.");
            return;
        }
        ExplorationResult result = exploration.open(save, direction);
        persist(ctx, save);
        ctx.reply(result.text());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<north|south|east|west>");
        help.addDescription("Opens a closed door, or tries to force a stuck one (forcing takes a turn).");
    }
}
