package dev.freitas.delve.discord;

import java.util.List;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** JDA listener that registers slash commands on ready and routes messages/interactions. */
@Service
public class EventHandler extends ListenerAdapter {

    private final Logger log = LoggerFactory.getLogger(EventHandler.class);
    private final CommandManager commandManager;

    public EventHandler(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    public void onReady(ReadyEvent event) {
        // Register slash commands globally (propagation can take up to ~1h on first publish).
        List<SlashCommandData> commands =
                commandManager.getCommands().stream().map(Command::buildSlashData).toList();
        event.getJDA()
                .updateCommands()
                .addCommands(commands)
                .queue(
                        ok -> log.info("Registered {} slash commands", commands.size()),
                        err -> log.error("Failed to register slash commands", err));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.isWebhookMessage() || event.getAuthor().isBot()) {
            return;
        }
        if (event.getChannelType() != ChannelType.TEXT) {
            return;
        }
        commandManager.onMessage(
                event.getGuild(), event.getChannel().asTextChannel(), event.getMember(), event.getMessage());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Commands can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        // Acknowledge within Discord's 3s window; replies then go through the interaction hook.
        event.deferReply().queue();
        commandManager.onSlash(event);
    }
}
