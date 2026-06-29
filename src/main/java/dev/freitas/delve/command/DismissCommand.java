package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Component;

/** Releases a retainer from service: {@code /dismiss <name>}. */
@Component
public class DismissCommand extends Command {

    public DismissCommand() {
        super("dismiss", "fire");
    }

    @Override
    public void invoke(CommandContext ctx) {
        long userId = ctx.getInvokerUserId();
        SaveGame save = ctx.getBeans().gameState.load(userId);
        if (!save.hasCharacter() || save.getRetainers().isEmpty()) {
            ctx.reply("You have no retainers to dismiss.");
            return;
        }
        String name = ctx.getArgumentText().trim();
        if (name.isBlank()) {
            ctx.reply("Dismiss whom? `dismiss <name>`. Use `" + ctx.getPrefix() + "party` to see names.");
            return;
        }
        Retainer match = save.getRetainers().stream()
                .filter(r -> r.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (match == null) {
            ctx.reply("No retainer named **" + name + "** is in your party.");
            return;
        }
        save.getRetainers().remove(match);
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply("You release **" + match.getName() + "** from service.");
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<name>");
        help.addDescription("Dismisses a retainer from your party.");
    }
}
