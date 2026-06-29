package dev.freitas.delve.discord;

import dev.freitas.delve.data.PlayerSaveService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.springframework.stereotype.Component;

/**
 * Invocation context for a command. Commands are written against this abstraction
 * ({@link #getArgumentText()} plus the reply* methods), so the same body serves both the legacy
 * prefix/message path ({@link MessageCommandContext}) and Discord slash commands
 * ({@link SlashCommandContext}). Cf. ukulele's {@code CommandContext}.
 *
 * <p>Unlike ukulele (which keyed state by guild), delve is single-player, so the key identity is the
 * invoking Discord user ({@link #getInvokerUserId()}).
 */
public abstract class CommandContext {

    /**
     * Shared, Spring-managed services available to every command. {@code commandManager} is wired in
     * after construction to break the circular dependency (cf. ukulele's {@code CommandContext.Beans}).
     */
    @Component
    public static class Beans {
        public final PlayerSaveService playerSaves;
        CommandManager commandManager;

        public Beans(PlayerSaveService playerSaves) {
            this.playerSaves = playerSaves;
        }

        public CommandManager getCommandManager() {
            return commandManager;
        }
    }

    private final Beans beans;
    private final Guild guild;
    private final TextChannel channel;
    private final Member invoker;
    private final Command command;
    private final String prefix;
    private final String trigger;

    protected CommandContext(
            Beans beans,
            Guild guild,
            TextChannel channel,
            Member invoker,
            Command command,
            String prefix,
            String trigger) {
        this.beans = beans;
        this.guild = guild;
        this.channel = channel;
        this.invoker = invoker;
        this.command = command;
        this.prefix = prefix;
        this.trigger = trigger;
    }

    public Beans getBeans() {
        return beans;
    }

    public Guild getGuild() {
        return guild;
    }

    public TextChannel getChannel() {
        return channel;
    }

    public Member getInvoker() {
        return invoker;
    }

    /** The Discord user id that owns this game session — the persistence key. */
    public long getInvokerUserId() {
        return invoker.getIdLong();
    }

    public Command getCommand() {
        return command;
    }

    public String getPrefix() {
        return prefix;
    }

    /** Prefix + command name (or {@code /name} for slash). */
    public String getTrigger() {
        return trigger;
    }

    /** The command argument text (after the trigger for messages, or the {@code args} option). */
    public abstract String getArgumentText();

    public abstract void reply(String msg);

    public abstract void replyMsg(MessageCreateData msg);

    public abstract void replyEmbed(MessageEmbed embed);

    public void replyHelp() {
        replyHelp(command);
    }

    public void replyHelp(Command forCommand) {
        HelpContext help = new HelpContext(this, forCommand);
        forCommand.provideHelp(help);
        replyMsg(help.buildMessage());
    }

    public void handleException(Throwable t) {
        command.log.error("Handled exception occurred", t);
        reply("An exception occurred!\n`" + t.getMessage() + "`");
    }
}
