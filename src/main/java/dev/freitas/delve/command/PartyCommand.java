package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.RetainerRules;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Component;

/** Lists the player character and their retainers: {@code /party}. */
@Component
public class PartyCommand extends Command {

    public PartyCommand() {
        super("party", "p");
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = ctx.getBeans().gameState.load(ctx.getInvokerUserId());
        if (!save.hasCharacter()) {
            ctx.reply("Roll a character first with `" + ctx.getPrefix() + "roll-character <class>`.");
            return;
        }
        Character pc = save.getCharacter();
        int max = RetainerRules.maxRetainers(pc.getAbilities().score(Ability.CHA));

        StringBuilder sb = new StringBuilder("**Your party**\n```\n");
        sb.append(String.format("%-16s L%-2d %-11s %3d/%-3d hp  AC %d%n",
                pc.getName() + " (you)", pc.getLevel(), pc.getCharacterClass().displayName(),
                pc.getCurrentHp(), pc.getMaxHp(), pc.armorClass()));
        for (Retainer r : save.getRetainers()) {
            sb.append(String.format("%-16s L%-2d %-11s %3d/%-3d hp  AC %d  loyalty %d (%s)%n",
                    r.getName(), r.getLevel(), r.getCharacterClass().displayName(),
                    r.getCurrentHp(), r.getMaxHp(), r.armorClass(),
                    r.getLoyalty(), RetainerRules.loyaltyDescriptor(r.getLoyalty())));
        }
        sb.append("```");
        sb.append("Retainers: ").append(save.getRetainers().size()).append("/").append(max)
                .append(" (Charisma cap).");
        ctx.reply(sb.toString());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("");
        help.addDescription("Shows your character and hired retainers.");
    }
}
