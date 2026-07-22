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
        String moduleName = ctx.getArgumentText().trim();
        if (moduleName.isBlank()) {
            List<String> modules = ModuleLoader.listModules();
            if (modules.isEmpty()) {
                ctx.reply("No modules are installed. Convert a module PDF with the `importModule` Gradle "
                        + "task, then drop its JSON in `content/modules/`.");
                return;
            }
            StringBuilder sb = new StringBuilder("**Available modules**\n```\n");
            modules.forEach(m -> sb.append("• ").append(m).append('\n'));
            sb.append("```");
            sb.append("Run one with `").append(ctx.getPrefix()).append("enter <module>`, or inspect with `")
                    .append(ctx.getPrefix()).append("loadmodule <module>`.");
            ctx.reply(sb.toString());
            return;
        }

        ModuleLoader.LoadedModule loaded = ModuleLoader.load(moduleName);
        if (loaded == null) {
            ctx.reply("No module named **" + moduleName + "**. See `" + ctx.getPrefix()
                    + "loadmodule` for what's available.");
            return;
        }

        int totalRooms = loaded.dungeon().getLevels().stream()
                .mapToInt(l -> l.getRooms().size())
                .sum();
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(loaded.title()).append("** (`").append(moduleName).append("`)\n");
        sb.append("• Levels: ").append(loaded.dungeon().levelCount()).append("\n");
        sb.append("• Total Rooms: ").append(totalRooms).append("\n");
        if (!loaded.warnings().isEmpty()) {
            sb.append("\n**Warnings (" + loaded.warnings().size() + "):**\n");
            loaded.warnings().forEach(w -> sb.append("⚠️ _").append(w).append("_\n"));
        } else {
            sb.append("• Warnings: None\n");
        }
        ctx.reply(sb.toString());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[module]");
        help.addDescription("Lists available authored modules, or inspects details and warnings for a specific module.");
    }
}
