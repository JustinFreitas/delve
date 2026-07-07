package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.SaveGame;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Component;

/** Rollbacks the last action: {@code /undo} or {@code /rewind}. Restricted to administrators. */
@Component
public class UndoCommand extends Command {

    public UndoCommand() {
        super("undo", "rewind");
    }

    @Override
    public void invoke(CommandContext ctx) {
        Member invoker = ctx.getInvoker();
        boolean isAdmin = invoker != null && (invoker.isOwner() || invoker.hasPermission(Permission.ADMINISTRATOR));
        if (!isAdmin) {
            ctx.reply("❌ Only server administrators can use the rollback command.");
            return;
        }

        long userId = ctx.getInvokerUserId();
        SaveGame undone = ctx.getBeans().gameState.undo(userId);
        if (undone == null) {
            ctx.reply("❌ No history available to undo.");
            return;
        }

        ctx.reply("🔮 **Time Rewound**: Rolled back the last action.");
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("");
        help.addDescription("Rollbacks the last action. Restricted to server administrators.");
    }
}
