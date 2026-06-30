package dev.freitas.delve.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.PregenExport;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Exports the invoker's current character for use at the table: {@code /export [embed|text|json]}
 * (default {@code text}). The JSON form attaches a downloadable file with a clean B/X stat block.
 */
@Component
public class ExportCommand extends Command {

    private final ObjectMapper objectMapper;

    public ExportCommand(ObjectMapper objectMapper) {
        super("export", "exportpc");
        this.objectMapper = objectMapper;
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = ctx.getBeans().gameState.load(ctx.getInvokerUserId());
        if (!save.hasCharacter()) {
            ctx.reply("You have no character to export. Build one with `" + ctx.getPrefix()
                    + "pregen <class> <level>` or `" + ctx.getPrefix() + "roll-character <class>`.");
            return;
        }
        Character c = save.getCharacter();
        String format = ctx.getArgumentText().trim().toLowerCase();

        switch (format) {
            case "embed" -> ctx.replyEmbed(CharacterSheets.embed(c));
            case "json" -> exportJson(ctx, c);
            default -> ctx.reply(CharacterSheets.textBlock(c)); // "text" or anything else
        }
    }

    private void exportJson(CommandContext ctx, Character c) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(PregenExport.of(c));
            String fileName = "pregen-" + slug(c.getName()) + "-L" + c.getLevel() + ".json";
            ctx.replyFile(fileName, json.getBytes(StandardCharsets.UTF_8),
                    "Exported **" + c.getName() + "** (level " + c.getLevel() + " "
                            + c.getCharacterClass().displayName() + ").");
        } catch (Exception e) {
            ctx.handleException(e);
        }
    }

    static String slug(String name) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "character" : slug;
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[embed|text|json]");
        help.addDescription("Exports your current character: an embed, a copy-paste stat block (default), "
                + "or a downloadable JSON file.");
    }
}
