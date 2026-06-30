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
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * DM tool: mint a roster of pregenerated characters at a target level — {@code /roster <count> <level>
 * [class]}. Generation is stateless (it does not touch the invoker's own save); the party is returned
 * as a summary plus a combined JSON file for the DM to keep.
 */
@Component
public class RosterCommand extends Command {

    private static final int MAX_COUNT = 8;

    private final PregenService pregen;
    private final ObjectMapper objectMapper;

    public RosterCommand(PregenService pregen, ObjectMapper objectMapper) {
        super("roster", "party-gen");
        this.pregen = pregen;
        this.objectMapper = objectMapper;
    }

    @Override
    public void invoke(CommandContext ctx) {
        String[] tokens = ctx.getArgumentText().trim().split("\\s+");
        if (tokens.length < 2) {
            ctx.reply("Usage: `" + ctx.getPrefix() + "roster <count> <level> [class]` — e.g. `roster 4 5`.");
            return;
        }
        int count;
        int level;
        try {
            count = Integer.parseInt(tokens[0]);
            level = Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            ctx.reply("Count and level must be numbers: `" + ctx.getPrefix() + "roster <count> <level> [class]`.");
            return;
        }
        if (count < 1 || count > MAX_COUNT) {
            ctx.reply("Count must be between 1 and " + MAX_COUNT + ".");
            return;
        }
        CharacterClass fixedClass = tokens.length > 2 ? CharacterClass.parse(tokens[2]) : null;
        if (tokens.length > 2 && fixedClass == null) {
            ctx.reply("Unknown class \"" + tokens[2] + "\". Omit it for a mixed party, or use a B/X class.");
            return;
        }
        if (level < 1 || (fixedClass != null && level > pregen.maxLevel(fixedClass))) {
            ctx.reply("Level out of range for that class.");
            return;
        }

        List<Character> party = pregen.createBatch(count, level, fixedClass, null);

        StringBuilder summary = new StringBuilder("**Roster — " + count + " level-" + level + " pregens**\n```\n");
        java.util.List<Object> exports = new java.util.ArrayList<>();
        for (Character c : party) {
            summary.append(String.format("%-16s L%-2d %-11s %3d hp  AC %d  THAC0 %d%n",
                    c.getName(), c.getLevel(), c.getCharacterClass().displayName(),
                    c.getMaxHp(), c.armorClass(), c.thac0()));
            exports.add(PregenExport.of(c));
        }
        summary.append("```");

        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exports);
            ctx.replyFile("roster-L" + level + "-x" + count + ".json",
                    json.getBytes(StandardCharsets.UTF_8), summary.toString());
        } catch (Exception e) {
            ctx.reply(summary.toString());
            ctx.handleException(e);
        }
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<count> <level> [class]");
        help.addDescription("DM tool: generates a roster of pregens at a level (mixed classes, or a fixed "
                + "class). Returns a summary and a JSON file. Does not change your own character.");
    }
}
