package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.PregenService;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.model.Character;
import org.springframework.stereotype.Component;

/**
 * Instantly builds a finished, table-ready character at a target level: {@code /pregen <class> [level]
 * [name]} (default level 5). Sets it as the invoker's character (replacing any existing one), so it
 * can then be viewed with {@code /sheet} and exported with {@code /export}.
 */
@Component
public class PregenCommand extends Command {

    private static final int DEFAULT_LEVEL = 5;

    private final PregenService pregen;

    public PregenCommand(PregenService pregen) {
        super("pregen", "makepc");
        this.pregen = pregen;
    }

    @Override
    public void invoke(CommandContext ctx) {
        String[] tokens = ctx.getArgumentText().trim().split("\\s+");
        CharacterClass characterClass = tokens.length > 0 ? CharacterClass.parse(tokens[0]) : null;
        if (characterClass == null) {
            ctx.reply("Usage: `" + ctx.getPrefix() + "pregen <class> [level] [name]`. "
                    + "Classes: cleric, fighter, magic-user, thief, dwarf, elf, halfling.");
            return;
        }

        int level = DEFAULT_LEVEL;
        int nameStart = 1;
        if (tokens.length > 1) {
            try {
                level = Integer.parseInt(tokens[1]);
                nameStart = 2;
            } catch (NumberFormatException e) {
                // tokens[1] is part of the name; keep the default level.
            }
        }
        int max = pregen.maxLevel(characterClass);
        if (level < 1 || level > max) {
            ctx.reply("Level must be between 1 and " + max + " for a " + characterClass.displayName() + ".");
            return;
        }

        String name = nameStart < tokens.length
                ? String.join(" ", java.util.Arrays.copyOfRange(tokens, nameStart, tokens.length))
                : ctx.getInvoker().getEffectiveName();

        Character character = pregen.create(name, characterClass, level);

        boolean replaced = ctx.getBeans().gameState.load(ctx.getInvokerUserId()).hasCharacter();
        ctx.getBeans().gameState.mutate(ctx.getInvokerUserId(), save -> save.setCharacter(character));

        ctx.reply("Generated **" + name + "**, a level " + level + " " + characterClass.displayName()
                + ", ready for the table." + (replaced ? " _(replaced your previous character)_" : "")
                + "\nExport with `" + ctx.getPrefix() + "export text` or `" + ctx.getPrefix() + "export json`.");
        ctx.replyEmbed(CharacterSheets.embed(character));
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<class> [level] [name]");
        help.addDescription("Instantly builds a finished character at the given level (default 5) with "
                + "level-appropriate hit points, gear, wealth and magic items. Sets it as your character.");
    }
}
