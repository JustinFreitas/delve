package dev.freitas.delve.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

/** Context for the legacy prefix/mention message command path (cf. ukulele). */
public class MessageCommandContext extends CommandContext {

    private final Message message;
    private String argumentText;

    public MessageCommandContext(
            Beans beans,
            Guild guild,
            TextChannel channel,
            Member invoker,
            Message message,
            Command command,
            String prefix,
            String trigger) {
        super(beans, guild, channel, invoker, command, prefix, trigger);
        this.message = message;
    }

    @Override
    public String getArgumentText() {
        if (argumentText == null) {
            String raw = message.getContentRaw();
            argumentText = raw.length() >= getTrigger().length()
                    ? raw.substring(getTrigger().length()).trim()
                    : "";
        }
        return argumentText;
    }

    @Override
    public void reply(String msg) {
        getChannel().sendMessage(msg).queue();
    }

    @Override
    public void replyMsg(MessageCreateData msg) {
        getChannel().sendMessage(msg).queue();
    }

    @Override
    public void replyEmbed(MessageEmbed embed) {
        getChannel().sendMessageEmbeds(embed).queue();
    }
}
