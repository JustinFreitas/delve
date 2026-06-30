package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.TownService;
import org.springframework.stereotype.Component;

/** Returns to town to rest, recover, pay upkeep and re-prepare spells: {@code /town}. */
@Component
public class TownCommand extends Command {

    private final TownService town;

    public TownCommand(TownService town) {
        super("town", "return", "rest");
        this.town = town;
    }

    @Override
    public void invoke(CommandContext ctx) {
        long userId = ctx.getInvokerUserId();
        SaveGame save = ctx.getBeans().gameState.load(userId);
        if (!save.hasCharacter()) {
            ctx.reply("Roll a character first with `" + ctx.getPrefix() + "roll-character <class>`.");
            return;
        }
        if (save.getSession().getState() == SessionState.IN_COMBAT) {
            ctx.reply("You can't stroll back to town mid-fight! `flee` first.");
            return;
        }
        ExplorationResult result = town.returnToTown(save);
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply(result.text());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("");
        help.addDescription("Returns to town: abandons the delve, heals the party to full, pays retainer "
                + "upkeep, and re-prepares spells.");
    }
}
