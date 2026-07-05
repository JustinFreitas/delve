package dev.freitas.delve.command;

import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.ExplorationService;
import org.springframework.stereotype.Component;

/** Jams a spike into a door to hold it open or shut: {@code /spike <direction> <open|closed>}. */
@Component
public class SpikeCommand extends DungeonCommand {

    private final ExplorationService exploration;

    public SpikeCommand(ExplorationService exploration) {
        super("spike", "wedge");
        this.exploration = exploration;
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = requireDelving(ctx);
        if (save == null) {
            return;
        }
        String[] tokens = ctx.getArgumentText().trim().split("\\s+");
        if (tokens.length < 2 || tokens[0].isBlank()) {
            ctx.reply("Spike a door which way, open or closed? Try `spike north open`.");
            return;
        }
        Direction direction = Direction.parse(tokens[0]);
        if (direction == null) {
            ctx.reply("Spike a door in which direction? Try `spike north open`.");
            return;
        }
        boolean holdOpen;
        if (tokens[1].equalsIgnoreCase("open")) {
            holdOpen = true;
        } else if (tokens[1].equalsIgnoreCase("closed") || tokens[1].equalsIgnoreCase("shut")) {
            holdOpen = false;
        } else {
            ctx.reply("Spike it `open` or `closed`?");
            return;
        }

        Character pc = save.getCharacter();
        String spike = InventoryMatcher.find(pc, "spike");
        if (spike == null) {
            ctx.reply("You don't have any spikes. Buy some with `" + ctx.getPrefix() + "buy spike`.");
            return;
        }

        ExplorationResult result = exploration.spike(save, direction, holdOpen);
        if (result.isSuccess()) {
            pc.getInventory().remove(spike);
        }
        persist(ctx, save);
        ctx.reply(result.text());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<direction> <open|closed>");
        help.addDescription("Jams a spike into a door to hold it `open` (it won't swing shut behind "
                + "you) or `closed` (jams it stuck, blocking pursuit) for the rest of the delve. Consumes "
                + "one spike from your inventory (`buy spike`) and costs a turn. Can't be undone.");
    }
}
