package dev.freitas.delve.command;

import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.ExplorationResult;
import org.springframework.stereotype.Component;

/** Attempts to flee the current fight: {@code /flee}. */
@Component
public class FleeCommand extends DungeonCommand {

    private final CombatService combat;

    public FleeCommand(CombatService combat) {
        super("flee", "run");
        this.combat = combat;
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = requireDelving(ctx);
        if (save == null) {
            return;
        }
        ExplorationResult result = combat.flee(save);
        persist(ctx, save);
        ctx.reply(result.text());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("");
        help.addDescription("Flees combat to an adjacent room; the enemy gets parting attacks.");
    }
}
