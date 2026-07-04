package dev.freitas.delve.command;

import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.ExplorationService;
import org.springframework.stereotype.Component;

/** Listens at a door for sounds beyond it: {@code /listen <direction>} (no turn cost). */
@Component
public class ListenCommand extends DungeonCommand {

    private final ExplorationService exploration;

    public ListenCommand(ExplorationService exploration) {
        super("listen");
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
            ctx.reply("Listen at a door in which direction? Try `listen north`.");
            return;
        }
        ExplorationResult result = exploration.listen(save, direction);
        persist(ctx, save);
        ctx.reply(result.text());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<north|south|east|west>");
        help.addDescription("Listens at a door for sounds beyond it: a 1-in-6 chance, one attempt per door. "
                + "Doesn't cost a turn.");
    }
}
