package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.dungeon.ModuleLoader;
import java.util.List;
import org.springframework.stereotype.Component;

/** Lists the authored modules available to run: {@code /loadmodule}. */
@Component
public class LoadModuleCommand extends Command {

    public LoadModuleCommand() {
        super("loadmodule", "modules");
    }

    @Override
    public void invoke(CommandContext ctx) {
        List<String> modules = ModuleLoader.listModules();
        if (modules.isEmpty()) {
            ctx.reply("No modules are installed. Convert a module PDF with the `importModule` Gradle "
                    + "task, then drop its JSON in `content/modules/`.");
            return;
        }
        StringBuilder sb = new StringBuilder("**Available modules**\n```\n");
        modules.forEach(m -> sb.append("• ").append(m).append('\n'));
        sb.append("```");
        sb.append("Run one with `").append(ctx.getPrefix()).append("enter <module>`.");
        ctx.reply(sb.toString());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("");
        help.addDescription("Lists the authored modules you can run with `enter <module>`.");
    }
}
