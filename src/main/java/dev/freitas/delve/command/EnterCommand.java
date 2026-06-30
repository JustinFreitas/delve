package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.dungeon.ModuleLoader;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.ExplorationService;
import org.springframework.stereotype.Component;

/** Begins a fresh dungeon delve: {@code /enter [module]} (no arg = procedurally generated). */
@Component
public class EnterCommand extends Command {

    private final ExplorationService exploration;

    public EnterCommand(ExplorationService exploration) {
        super("enter", "delve", "descend");
        this.exploration = exploration;
    }

    @Override
    public void invoke(CommandContext ctx) {
        long userId = ctx.getInvokerUserId();
        SaveGame save = ctx.getBeans().gameState.load(userId);

        if (!save.hasCharacter()) {
            ctx.reply("You need a character first. Roll one with `" + ctx.getPrefix() + "roll-character <class>`.");
            return;
        }
        if (!save.getCharacter().isAlive()) {
            ctx.reply("Your character has died. Roll a new one with `" + ctx.getPrefix() + "roll-character <class>`.");
            return;
        }
        if (save.getSession().isInDungeon()) {
            ctx.reply("You are already delving (level " + (save.getSession().getCurrentLevel() + 1)
                    + ", turn " + save.getSession().getDungeonTurn() + "). Use `" + ctx.getPrefix() + "look`.");
            return;
        }

        String moduleName = ctx.getArgumentText().trim();
        ExplorationResult result;
        if (moduleName.isBlank()) {
            result = exploration.enter(save);
        } else {
            ModuleLoader.LoadedModule loaded = ModuleLoader.load(moduleName);
            if (loaded == null) {
                ctx.reply("No module named **" + moduleName + "**. See `" + ctx.getPrefix()
                        + "loadmodule` for what's available.");
                return;
            }
            result = exploration.enterModule(save, loaded.dungeon(), loaded.title());
            loaded.warnings().forEach(w -> result.add("_" + w + "_"));
        }

        ctx.getBeans().gameState.save(userId, save);
        ctx.reply(result.text());
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[module]");
        help.addDescription("Begins a delve. With no argument the dungeon is procedurally generated; "
                + "give a module name (see `loadmodule`) to run an authored adventure.");
    }
}
