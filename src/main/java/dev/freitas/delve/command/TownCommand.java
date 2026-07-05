package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.TownService;
import org.springframework.stereotype.Component;

/** Returns to town to rest, recover, pay upkeep and re-prepare spells: {@code /town [days]}. */
@Component
public class TownCommand extends Command {

    private static final int DEFAULT_REST_DAYS = 7;

    private final TownService town;

    public TownCommand(TownService town) {
        super("town", "return");
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
        int days = DEFAULT_REST_DAYS;
        String arg = ctx.getArgumentText().trim();
        if (!arg.isBlank()) {
            try {
                days = Math.max(1, Integer.parseInt(arg));
            } catch (NumberFormatException e) {
                ctx.reply("Number of days must be a number.");
                return;
            }
        }
        ExplorationResult result = town.returnToTown(save, days);
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply(result.text());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[days]");
        help.addDescription("Returns to town: abandons the delve, rests (1d3 hp/day, default "
                + DEFAULT_REST_DAYS + " days — a shorter stay heals less), pays retainer upkeep, and "
                + "re-prepares spells. A retainer who fled a fight has only a 3-in-6 chance of making it "
                + "back at all.");
        help.addDescription("Healing is capped by how much real time has actually passed since your "
                + "last town visit — typing a big number doesn't buy free rest; the reply says if your "
                + "stay was cut short.");
    }
}
