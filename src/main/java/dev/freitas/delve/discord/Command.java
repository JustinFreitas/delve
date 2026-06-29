package dev.freitas.delve.discord;

import java.util.List;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for every bot command. Each concrete command is a Spring {@code @Component}, so they
 * are auto-discovered and registered by {@link CommandManager} (cf. ukulele's {@code Command}).
 *
 * <p>Commands are written against {@link CommandContext}, so the same body serves both the legacy
 * prefix/message path and Discord slash commands.
 */
public abstract class Command {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final String name;
    private final List<String> aliases;

    protected Command(String name, String... aliases) {
        this.name = name;
        this.aliases = List.of(aliases);
    }

    public String getName() {
        return name;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public abstract void invoke(CommandContext ctx);

    public abstract void provideHelp(HelpContext help);

    /**
     * Builds the Discord slash-command registration for this command. Every command gets a single
     * optional STRING option {@code args} which maps to {@link CommandContext#getArgumentText()}, so
     * command bodies stay unchanged. Aliases are not registered (Discord has no alias concept); they
     * remain for the prefix path.
     */
    public SlashCommandData buildSlashData() {
        return Commands.slash(name, shortDescription())
                .addOption(OptionType.STRING, "args", "Optional command arguments", false);
    }

    /** A <=100 char description for Discord, derived from this command's {@link #provideHelp}. */
    private String shortDescription() {
        HelpContext help = new HelpContext(null, this);
        provideHelp(help);
        String description = help.firstDescription();
        if (description == null || description.isBlank()) {
            description = name + " command";
        }
        return description.length() > 100 ? description.substring(0, 100) : description;
    }

    /** All registrable names for this command (primary name + aliases). */
    List<String> allNames() {
        var names = new java.util.ArrayList<String>();
        names.add(name);
        names.addAll(aliases);
        return names;
    }
}
