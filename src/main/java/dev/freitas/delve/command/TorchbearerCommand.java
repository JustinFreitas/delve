package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.ContainerRules;
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
        var candidate = save.resolve(name);
        if (candidate == null) {
            ctx.reply("Don't recognize **" + name + "**. Use `me`/a character's name, or a retainer's "
                    + "name from `" + ctx.getPrefix() + "party`.");
            return;
        }
        String token = save.tokenFor(candidate);
        String failure = lighting.assignBearer(save, token);
        if (failure != null) {
            ctx.reply(failure);
            return;
        }
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply((token.equals(SaveGame.PLAYER_SLOT) ? "You take" : candidate.getName() + " takes")
                + " up the light.");
    }

    private String describeStatus(SaveGame save) {
        String bearer = save.getSession().getLightBearer();
        boolean solo = save.getCharacters().size() == 1;
        StringBuilder sb = new StringBuilder("**Torchbearer**: ");
        sb.append(bearer == null ? "no one" : (SaveGame.PLAYER_SLOT.equals(bearer) ? "you" : bearer)).append("\n```\n");
        for (Character c : save.getCharacters()) {
            String label = solo ? c.getName() + " (you)" : c.getName();
            sb.append(String.format("%-16s %d/2 hands free%n", label,
                    Hands.free(c.getMainWeapon(), c.isShield(), false, c.getOffHandWeapon() != null,
                            ContainerRules.heldCount(c.getContainers()))));
        }
        for (Retainer r : save.livingRetainers()) {
            sb.append(String.format("%-16s %d/2 hands free%n", r.getName(),
                    Hands.free(r.getMainWeapon(), r.isShield(), false, ContainerRules.heldCount(r.getContainers()))));
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
