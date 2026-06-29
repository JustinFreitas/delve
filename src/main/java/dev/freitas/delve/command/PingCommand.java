package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import org.springframework.stereotype.Component;

/** Minimal liveness check (cf. ukulele's simplest commands). */
@Component
public class PingCommand extends Command {

    public PingCommand() {
        super("ping");
    }

    @Override
    public void invoke(CommandContext ctx) {
        long latency = ctx.getGuild().getJDA().getGatewayPing();
        ctx.reply("Pong! Gateway latency: " + latency + "ms.");
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("");
        help.addDescription("Checks that the bot is alive and reports gateway latency.");
    }
}
