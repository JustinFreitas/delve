package dev.freitas.delve.discord;

import dev.freitas.delve.config.BotProps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Discovers all {@link Command} beans, registers them by name + alias, and dispatches both the legacy
 * prefix/mention message path and Discord slash commands (cf. ukulele's {@code CommandManager}).
 */
@Service
public class CommandManager {

    private final Logger log = LoggerFactory.getLogger(CommandManager.class);

    private final CommandContext.Beans contextBeans;
    private final BotProps botProps;
    private final CommandExecutor executor;
    private final Map<String, Command> registry = new LinkedHashMap<>();

    public CommandManager(
            CommandContext.Beans contextBeans,
            BotProps botProps,
            CommandExecutor executor,
            List<Command> commands) {
        this.contextBeans = contextBeans;
        this.botProps = botProps;
        this.executor = executor;

        for (Command c : commands) {
            for (String n : c.allNames()) {
                registry.put(n, c);
            }
        }
        log.info("Registered {} commands with {} names", commands.size(), registry.size());
        contextBeans.commandManager = this;
    }

    public Command get(String commandName) {
        return registry.get(commandName);
    }

    public List<Command> getCommands() {
        return registry.values().stream().distinct().toList();
    }

    /** Dispatch a legacy prefix/mention message command. */
    public void onMessage(Guild guild, TextChannel channel, Member member, Message message) {
        String prefix = botProps.getPrefix();
        String raw = message.getContentRaw();

        String name;
        String trigger;

        // A mention of us at the very beginning counts as a prefix.
        Pattern mentionPattern = Pattern.compile("^(<@!?" + guild.getSelfMember().getId() + ">\\s*)");
        Matcher mention = mentionPattern.matcher(raw);
        if (mention.find()) {
            String mentionText = mention.group(1);
            String commandText = raw.substring(mentionText.length());
            if (commandText.isEmpty()) {
                channel.sendMessage(
                                "The prefix here is `" + prefix + "`, or just mention me followed by a command.")
                        .queue();
                return;
            }
            name = takeWhileNotWhitespace(commandText.trim());
            trigger = mentionText + name;
        } else if (raw.startsWith(prefix)) {
            name = takeWhileNotWhitespace(raw.substring(prefix.length()));
            trigger = prefix + name;
        } else {
            return;
        }

        Command command = registry.get(name);
        if (command == null) {
            return;
        }

        CommandContext ctx =
                new MessageCommandContext(contextBeans, guild, channel, member, message, command, prefix, trigger);
        log.info("Invocation: {}", raw);
        executor.submit(name, () -> invoke(command, ctx));
    }

    /** Dispatch a Discord slash command. The caller (EventHandler) must have already deferred the reply. */
    public void onSlash(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            return;
        }
        Command command = registry.get(event.getName());
        if (command == null) {
            return;
        }

        if (event.getChannelType() != ChannelType.TEXT) {
            event.getHook().sendMessage("This command can only be used in a text channel.").queue();
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        CommandContext ctx =
                new SlashCommandContext(
                        contextBeans, guild, channel, member, event, command, "/", "/" + event.getName());
        var argOption = event.getOption("args");
        log.info("Slash invocation: /{} {}", event.getName(), argOption != null ? argOption.getAsString() : "");
        executor.submit(event.getName(), () -> invoke(command, ctx));
    }

    private void invoke(Command command, CommandContext ctx) {
        try {
            command.invoke(ctx);
        } catch (Throwable t) {
            ctx.handleException(t);
        }
    }

    private static String takeWhileNotWhitespace(String s) {
        int i = 0;
        while (i < s.length() && !Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(0, i);
    }

    /** Distinct registered commands' display names (primary + aliases), for help listings. */
    public List<List<String>> getCommandNameGroups() {
        List<List<String>> groups = new ArrayList<>();
        for (Command c : getCommands()) {
            groups.add(c.allNames());
        }
        return groups;
    }
}
