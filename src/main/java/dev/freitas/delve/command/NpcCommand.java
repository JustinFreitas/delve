package dev.freitas.delve.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.PregenExport;
import dev.freitas.delve.game.PregenService;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.model.Character;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * DM tool: generate a single named NPC at a target level — {@code /npc <class> <level> [name]}.
 * Stateless (does not touch the invoker's character); replies with the sheet embed, a copy-paste
 * stat block, and a JSON file.
 */
@Component
public class NpcCommand extends Command {

    private final PregenService pregen;
    private final ObjectMapper objectMapper;

    public NpcCommand(PregenService pregen, ObjectMapper objectMapper) {
        super("npc", "makenpc");
        this.pregen = pregen;
        this.objectMapper = objectMapper;
    }

    @Override
    public void invoke(CommandContext ctx) {
        String[] tokens = ctx.getArgumentText().trim().split("\\s+");
        if (tokens.length < 2) {
            ctx.reply("Usage: `" + ctx.getPrefix() + "npc <class> <level> [name]` — e.g. `npc cleric 6 Ahkmenrah`.");
            return;
        }
        CharacterClass cls = CharacterClass.parse(tokens[0]);
        if (cls == null) {
            ctx.reply("Unknown class \"" + tokens[0] + "\". Use a B/X class (cleric, fighter, magic-user, ...).");
            return;
        }
        int level;
        try {
            level = Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            ctx.reply("Level must be a number: `" + ctx.getPrefix() + "npc <class> <level> [name]`.");
            return;
        }
        if (level < 1 || level > pregen.maxLevel(cls)) {
            ctx.reply("Level must be between 1 and " + pregen.maxLevel(cls) + " for a " + cls.displayName() + ".");
            return;
        }
        String name = tokens.length > 2
                ? String.join(" ", java.util.Arrays.copyOfRange(tokens, 2, tokens.length))
                : cls.displayName() + " NPC";

        Character npc = pregen.create(name, cls, level);

        ctx.replyEmbed(CharacterSheets.embed(npc));
        ctx.reply(CharacterSheets.textBlock(npc));
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(PregenExport.of(npc));
            ctx.replyFile("npc-" + ExportCommand.slug(name) + "-L" + level + ".json",
                    json.getBytes(StandardCharsets.UTF_8), null);
        } catch (Exception e) {
            ctx.handleException(e);
        }
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<class> <level> [name]");
        help.addDescription("DM tool: generates a single named NPC at a level (embed + stat block + JSON). "
                + "Does not change your own character.");
    }
}
