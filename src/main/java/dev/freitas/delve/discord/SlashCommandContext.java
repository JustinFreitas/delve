package dev.freitas.delve.discord;

import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

/**
 * Context for Discord slash commands. The dispatcher calls {@code deferReply()} first, so replies go
 * through the interaction hook: the first reply edits the deferred ("thinking") response and any
 * further replies are sent as follow-ups (cf. ukulele).
 */
public class SlashCommandContext extends CommandContext {

    private final SlashCommandInteractionEvent event;
    private final AtomicBoolean firstReply = new AtomicBoolean(true);
    private final String argumentText;

    public SlashCommandContext(
            Beans beans,
            Guild guild,
            TextChannel channel,
            Member invoker,
            SlashCommandInteractionEvent event,
            Command command,
            String prefix,
            String trigger) {
        super(beans, guild, channel, invoker, command, prefix, trigger);
        this.event = event;
        var option = event.getOption("args");
        this.argumentText = option != null ? option.getAsString().trim() : "";
    }

    @Override
    public String getArgumentText() {
        return argumentText;
    }

    @Override
    public void reply(String msg) {
        if (firstReply.getAndSet(false)) {
            event.getHook().editOriginal(msg).queue();
        } else {
            event.getHook().sendMessage(msg).queue();
        }
    }

    @Override
    public void replyMsg(MessageCreateData msg) {
        if (firstReply.getAndSet(false)) {
            event.getHook().editOriginal(MessageEditData.fromCreateData(msg)).queue();
        } else {
            event.getHook().sendMessage(msg).queue();
        }
    }

    @Override
    public void replyEmbed(MessageEmbed embed) {
        if (firstReply.getAndSet(false)) {
            event.getHook().editOriginalEmbeds(embed).queue();
        } else {
            event.getHook().sendMessageEmbeds(embed).queue();
        }
    }

    @Override
    public void replyFile(String fileName, byte[] data, String message) {
        FileUpload upload = FileUpload.fromData(data, fileName);
        if (firstReply.getAndSet(false)) {
            // editOriginal replaces the deferred "thinking" message and attaches the file.
            var action = event.getHook().editOriginalAttachments(upload);
            if (message != null && !message.isBlank()) {
                action = action.setContent(message);
            }
            action.queue();
        } else {
            var action = event.getHook().sendFiles(upload);
            if (message != null && !message.isBlank()) {
                action = action.setContent(message);
            }
            action.queue();
        }
    }
}
