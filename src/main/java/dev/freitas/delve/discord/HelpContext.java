package dev.freitas.delve.discord;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

/**
 * Accumulates a command's usage/description lines for help output and for deriving the Discord
 * slash-command description (cf. ukulele's {@code HelpContext}).
 */
public class HelpContext {

    // Nullable so slash-command registration can run provideHelp() purely to extract the
    // description text, without constructing a full live CommandContext.
    private final CommandContext commandContext;
    private final Command command;
    private final List<String> lines = new ArrayList<>();

    public HelpContext(CommandContext commandContext, Command command) {
        this.commandContext = commandContext;
        this.command = command;
    }

    public void addUsage(String usage) {
        String prefix = commandContext != null ? commandContext.getPrefix() : "/";
        lines.add(prefix + command.getName() + " " + usage.trim());
    }

    public void addDescription(String text) {
        lines.add("# " + text.trim());
    }

    /** First description line (without the leading "# "), used as a slash command description. */
    public String firstDescription() {
        return lines.stream()
                .filter(it -> it.startsWith("# "))
                .map(it -> it.substring(2).trim())
                .filter(it -> !it.isBlank())
                .findFirst()
                .orElse(null);
    }

    public MessageCreateData buildMessage() {
        return new MessageCreateBuilder()
                .addContent(MarkdownUtil.codeblock("md", toString()))
                .build();
    }

    @Override
    public String toString() {
        return String.join("\n", lines);
    }
}
