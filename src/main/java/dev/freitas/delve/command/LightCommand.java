package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.LightSource;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.GameSession;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.LightingService;
import org.springframework.stereotype.Component;

/** Views the party's light status, or lights a fresh torch/lantern: {@code /light [torch|lantern]}. */
@Component
public class LightCommand extends Command {

    private final LightingService lighting;

    public LightCommand(LightingService lighting) {
        super("light");
        this.lighting = lighting;
    }

    @Override
    public void invoke(CommandContext ctx) {
        long userId = ctx.getInvokerUserId();
        SaveGame save = ctx.getBeans().gameState.load(userId);
        if (!save.hasCharacter()) {
            ctx.reply("Roll a character first with `" + ctx.getPrefix() + "roll-character <class>`.");
            return;
        }
        String arg = ctx.getArgumentText().trim();
        if (arg.isBlank()) {
            ctx.reply(describeStatus(save));
            return;
        }
        LightSource type;
        if (arg.equalsIgnoreCase("torch")) {
            type = LightSource.TORCH;
        } else if (arg.equalsIgnoreCase("lantern")) {
            type = LightSource.LANTERN;
        } else {
            ctx.reply("Light what? `light torch` or `light lantern`.");
            return;
        }
        ExplorationResult result = new ExplorationResult();
        lighting.lightUp(save, type, result);
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply(result.text());
    }

    private String describeStatus(SaveGame save) {
        Character pc = save.getCharacter();
        GameSession session = save.getSession();
        LightSource active = session.getActiveLight();
        String bearer = session.getLightBearer();
        StringBuilder sb = new StringBuilder("**Light**: ");
        if (active == null) {
            sb.append("none — darkness.");
        } else {
            sb.append(active.displayName()).append(", ").append(session.getLightTurnsRemaining())
                    .append(" turns left, carried by ")
                    .append(bearer == null ? "no one" : (SaveGame.PLAYER_SLOT.equals(bearer) ? "you" : bearer))
                    .append(", ").append(active.radiusFeet()).append("' radius.");
        }
        sb.append("\nOwned: ").append(pc.getTorches()).append(" torch(es), ").append(pc.getLanterns())
                .append(" lantern(s), ").append(pc.getOilFlasks()).append(" oil flask(s).");
        return sb.toString();
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[torch|lantern]");
        help.addDescription("Shows the party's light status, or lights a fresh torch/lantern (needs "
                + "one owned — `buy` more in town — and a party member with a free hand to hold it). "
                + "A torch is cheap and burns 6 turns per unit; a lantern costs more but burns 24 turns "
                + "per flask of oil.");
    }
}
