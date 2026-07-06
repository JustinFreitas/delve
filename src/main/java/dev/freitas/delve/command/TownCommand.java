package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.LodgingTier;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.TownService;
import org.springframework.stereotype.Component;

/** Returns to town to rest, recover, pay upkeep and re-prepare spells: {@code /town [days] [tier]}. */
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
        LodgingTier tier = LodgingTier.ROOM;
        String argsText = ctx.getArgumentText().trim();
        for (String token : argsText.isBlank() ? new String[0] : argsText.split("\\s+")) {
            LodgingTier parsedTier = lodgingTierFor(token);
            if (parsedTier != null) {
                tier = parsedTier;
                continue;
            }
            try {
                days = Math.max(1, Integer.parseInt(token));
            } catch (NumberFormatException e) {
                ctx.reply("`town [days] [dormitory|shared|room]` — '" + token + "' isn't a day count or "
                        + "a recognized lodging tier.");
                return;
            }
        }
        ExplorationResult result = town.returnToTown(save, days, tier);
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply(result.text());
    }

    /** Package-private: tests exercise this directly without a {@link CommandContext}. */
    LodgingTier lodgingTierFor(String token) {
        String lower = token.toLowerCase();
        if (lower.contains("dorm")) {
            return LodgingTier.DORMITORY;
        }
        if (lower.contains("shar")) {
            return LodgingTier.SHARED_ROOM;
        }
        if (lower.contains("room")) {
            return LodgingTier.ROOM;
        }
        return null;
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[days] [dormitory|shared|room]");
        help.addDescription("Returns to town: abandons the delve, rests (1d3 hp/day, default "
                + DEFAULT_REST_DAYS + " days — a shorter stay heals less), pays retainer upkeep, and "
                + "re-prepares spells. A retainer who fled a fight has only a 3-in-6 chance of making it "
                + "back at all.");
        help.addDescription("Lodging tier (either order, e.g. `town dormitory 3` or `town 3 dormitory`) "
                + "sets the whole party's stay: `room` (default, " + LodgingTier.ROOM.gpPerDay()
                + " gp/night per PC, no downside), `shared` (" + LodgingTier.SHARED_ROOM.gpPerDay()
                + " gp/night — once per full week, each PC's retainers may decide they're an unfit boss "
                + "and quit), or `dormitory` (" + LodgingTier.DORMITORY.gpPerDay() + " gp/night — cheapest, "
                + "but every retainer deserts rather than wait in a flophouse).");
        help.addDescription("Healing is capped by how much real time has actually passed since your "
                + "last town visit — typing a big number doesn't buy free rest; the reply says if your "
                + "stay was cut short.");
    }
}
