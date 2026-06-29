package dev.freitas.delve.command;

import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.ExplorationResult;
import org.springframework.stereotype.Component;

/** Strikes in combat (starting the fight if needed): {@code /attack [target number]}. */
@Component
public class AttackCommand extends DungeonCommand {

    private final CombatService combat;

    public AttackCommand(CombatService combat) {
        super("attack", "a", "fight");
        this.combat = combat;
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = requireDelving(ctx);
        if (save == null) {
            return;
        }
        Integer target = parseTarget(ctx.getArgumentText());
        ExplorationResult result = combat.attackRound(save, target);
        persist(ctx, save);
        ctx.reply(result.text());
    }

    private Integer parseTarget(String arg) {
        try {
            return arg == null || arg.isBlank() ? null : Integer.parseInt(arg.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[target number]");
        help.addDescription("Attacks in combat. Resolves one full round; optionally target enemy #n.");
    }
}
