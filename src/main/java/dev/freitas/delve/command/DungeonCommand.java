package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.game.model.SaveGame;

/** Base for commands that only make sense while actively delving; centralizes the load + guard. */
abstract class DungeonCommand extends Command {

    protected DungeonCommand(String name, String... aliases) {
        super(name, aliases);
    }

    /** Loads the save and ensures the player is mid-delve; replies and returns null otherwise. */
    protected SaveGame requireDelving(CommandContext ctx) {
        SaveGame save = ctx.getBeans().gameState.load(ctx.getInvokerUserId());
        if (!save.hasCharacter()) {
            ctx.reply("Roll a character first with `" + ctx.getPrefix() + "roll-character <class>`.");
            return null;
        }
        if (!save.getSession().isInDungeon()) {
            ctx.reply("You're in town. Use `" + ctx.getPrefix() + "enter` to begin a delve.");
            return null;
        }
        return save;
    }

    protected void persist(CommandContext ctx, SaveGame save) {
        ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
    }
}
