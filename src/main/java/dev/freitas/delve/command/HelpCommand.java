package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.springframework.stereotype.Component;

/** Lists commands, or shows detailed help for one (cf. ukulele's HelpCommand). */
@Component
public class HelpCommand extends Command {

    public HelpCommand() {
        super("help", "h", "?");
    }

    @Override
    public void invoke(CommandContext ctx) {
        String arg = ctx.getArgumentText().trim();
        if (!arg.isBlank()) {
            Command target = ctx.getBeans().getCommandManager().get(arg);
            ctx.replyHelp(target != null ? target : ctx.getCommand());
            return;
        }

        StringBuilder list = new StringBuilder();
        for (var group : ctx.getBeans().getCommandManager().getCommandNameGroups()) {
            list.append(String.join(", ", group)).append('\n');
        }
        var msg =
                new MessageCreateBuilder()
                        .addContent("Available commands:")
                        .addContent(MarkdownUtil.codeblock(list.toString()))
                        .addContent("\nUse '" + ctx.getTrigger() + " <command>' to see more details.")
                        .build();
        ctx.replyMsg(msg);
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("");
        help.addDescription("Displays a list of commands and aliases.");
        help.addUsage("<command>");
        help.addDescription("Displays help about a specific command.");
    }
}
