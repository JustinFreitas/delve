package dev.freitas.delve.command;

import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.ExplorationService;
import org.springframework.stereotype.Component;

/** Moves between rooms or up/down stairs: {@code /move <north|south|east|west|up|down>}. */
@Component
public class MoveCommand extends DungeonCommand {

    private final ExplorationService exploration;

    public MoveCommand(ExplorationService exploration) {
        super("move", "go", "m");
        this.exploration = exploration;
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = requireDelving(ctx);
        if (save == null) {
            return;
        }
        String arg = ctx.getArgumentText().trim().toLowerCase();
        if (arg.isBlank()) {
            ctx.reply("Move where? Try `move north`, or `move down` at stairs.");
            return;
        }

        ExplorationResult result;
        if (arg.equals("down") || arg.equals("up")) {
            result = exploration.useStairs(save, arg.equals("down"));
        } else {
            Direction direction = Direction.parse(arg);
            if (direction == null) {
                ctx.reply("That's not a direction. Use north/south/east/west, or up/down at stairs.");
                return;
            }
            result = exploration.move(save, direction);
        }

        persist(ctx, save);
        ctx.reply(result.text());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<north|south|east|west|up|down>");
        help.addDescription("Moves to an adjacent room or takes stairs. Each move is one dungeon turn.");
    }
}
