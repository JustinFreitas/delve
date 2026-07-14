package dev.freitas.delve.command;

import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.ExplorationResult;
import org.springframework.stereotype.Component;

/** Strikes in combat (starting the fight if needed): {@code /attack [pc-name] [target number]}. */
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
        String arg = ctx.getArgumentText().trim();

        // An optional leading PC-name names the acting attacker in a multi-PC party — with or without
        // a trailing target number (`/attack Bram 2` and plain `/attack Bram` both mean Bram acts).
        String actorToken = null;
        int firstSpace = arg.indexOf(' ');
        String firstToken = firstSpace > 0 ? arg.substring(0, firstSpace) : arg;
        if (!firstToken.isEmpty() && save.resolve(firstToken) instanceof Character) {
            actorToken = firstToken;
            arg = firstSpace > 0 ? arg.substring(firstSpace + 1).trim() : "";
        }

        Integer target = parseTarget(arg);
        ExplorationResult result = combat.attackRound(save, actorToken, target);
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
        help.addUsage("[pc-name] [target number]");
        help.addDescription("Attacks in combat. Resolves one full round; optionally target enemy #n. "
                + "In a multi-PC party, name a PC first to give them the explicit action — every other "
                + "living PC and retainer still auto-attacks that round.");
    }
}
