package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.Hands;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.LightingService;
import org.springframework.stereotype.Component;

/** Views or reassigns who carries the party's lit torch/lantern: {@code /torchbearer [name]}. */
@Component
public class TorchbearerCommand extends Command {

    private final LightingService lighting;

    public TorchbearerCommand(LightingService lighting) {
        super("torchbearer", "bearer");
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
        String name = ctx.getArgumentText().trim();
        if (name.isBlank()) {
            ctx.reply(describeStatus(save));
            return;
        }
        String token = resolveToken(save, name);
        if (token == null) {
            ctx.reply("Don't recognize **" + name + "**. Use `me`/your character's name, or a retainer's "
                    + "name from `" + ctx.getPrefix() + "party`.");
            return;
        }
        String failure = lighting.assignBearer(save, token);
        if (failure != null) {
            ctx.reply(failure);
            return;
        }
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply((token.equals(SaveGame.PLAYER_SLOT) ? "You take" : token + " takes") + " up the light.");
    }

    private String resolveToken(SaveGame save, String token) {
        if (token.equalsIgnoreCase("me") || token.equalsIgnoreCase(save.getCharacter().getName())) {
            return SaveGame.PLAYER_SLOT;
        }
        for (Retainer r : save.getRetainers()) {
            if (r.getName().equalsIgnoreCase(token)) {
                return r.getName();
            }
        }
        return null;
    }

    private String describeStatus(SaveGame save) {
        Character pc = save.getCharacter();
        String bearer = save.getSession().getLightBearer();
        StringBuilder sb = new StringBuilder("**Torchbearer**: ");
        sb.append(bearer == null ? "no one" : (SaveGame.PLAYER_SLOT.equals(bearer) ? "you" : bearer)).append("\n```\n");
        sb.append(String.format("%-16s %d/2 hands free%n", pc.getName() + " (you)",
                Hands.free(pc.getMainWeapon(), pc.isShield(), false, pc.getOffHandWeapon() != null)));
        for (Retainer r : save.livingRetainers()) {
            sb.append(String.format("%-16s %d/2 hands free%n", r.getName(),
                    Hands.free(r.getMainWeapon(), r.isShield(), false)));
        }
        return sb.append("```").toString();
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[name]");
        help.addDescription("Views who's carrying the party's lit torch/lantern, or reassigns it to "
                + "`me`/a retainer's name (rejected if they have no free hand). The party auto-picks a "
                + "free-handed retainer over the PC when nobody's assigned yet.");
    }
}
