package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.ExplorationResult;
import org.springframework.stereotype.Component;

/** A Cleric attempts to turn an undead encounter: {@code /turn}. */
@Component
public class TurnCommand extends Command {

    private final CombatService combat;

    public TurnCommand(CombatService combat) {
        super("turn");
        this.combat = combat;
    }

    @Override
    public void invoke(CommandContext ctx) {
        long userId = ctx.getInvokerUserId();
        SaveGame save = ctx.getBeans().gameState.load(userId);
        if (!save.hasCharacter()) {
            ctx.reply("Roll a character first with `" + ctx.getPrefix() + "roll-character <class>`.");
            return;
        }
        ExplorationResult result = combat.turnRound(save);
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply(result.text());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("");
        help.addDescription("A Cleric attempts to turn an undead encounter — success breaks their morale "
                + "and they flee. Costs your action for the round, like an attack.");
    }
}
