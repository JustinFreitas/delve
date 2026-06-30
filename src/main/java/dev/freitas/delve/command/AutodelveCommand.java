package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.AutodelveService;
import org.springframework.stereotype.Component;

/**
 * Fast-forwards the invoker's character to a target level by simulating delves:
 * {@code /autodelve [level]} (default 5). The character earns its levels through real (simulated)
 * combat and can die. The result is persisted, so a survivor can be exported for the table.
 */
@Component
public class AutodelveCommand extends Command {

    private static final int DEFAULT_LEVEL = 5;

    private final AutodelveService autodelve;

    public AutodelveCommand(AutodelveService autodelve) {
        super("autodelve", "fastlevel", "grind");
        this.autodelve = autodelve;
    }

    @Override
    public void invoke(CommandContext ctx) {
        long userId = ctx.getInvokerUserId();
        SaveGame save = ctx.getBeans().gameState.load(userId);
        if (!save.hasCharacter()) {
            ctx.reply("You need a character first. Roll one with `" + ctx.getPrefix()
                    + "roll-character <class>` or build one with `" + ctx.getPrefix() + "pregen`.");
            return;
        }
        if (!save.getCharacter().isAlive()) {
            ctx.reply("Your character has died. Roll or pregen a new one to try again.");
            return;
        }

        // Args: "<level> [fast|bx]" — level defaults to 5, pace defaults to by-the-book B/X OSE.
        int target = DEFAULT_LEVEL;
        AutodelveService.Pace pace = AutodelveService.Pace.BX_OSE;
        for (String token : ctx.getArgumentText().trim().toLowerCase().split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if (token.equals("fast")) {
                pace = AutodelveService.Pace.FAST;
            } else if (token.equals("bx") || token.equals("ose")) {
                pace = AutodelveService.Pace.BX_OSE;
            } else {
                try {
                    target = Integer.parseInt(token);
                } catch (NumberFormatException e) {
                    ctx.reply("Usage: `" + ctx.getPrefix() + "autodelve [level] [fast|bx]` (default: level 5, B/X OSE pace).");
                    return;
                }
            }
        }
        if (target < 1 || target > 14) {
            ctx.reply("Target level must be between 1 and 14.");
            return;
        }
        if (save.getCharacter().getLevel() >= target) {
            ctx.reply(save.getCharacter().getName() + " is already level " + save.getCharacter().getLevel() + ".");
            return;
        }

        AutodelveService.Result result = autodelve.run(save, target, pace);
        ctx.getBeans().gameState.save(userId, save);

        StringBuilder sb = new StringBuilder();
        sb.append(switch (result.outcome()) {
            case REACHED_TARGET -> "**" + save.getCharacter().getName() + "** clawed up from level "
                    + result.startLevel() + " to **level " + result.finalLevel() + "** over "
                    + result.episodes() + " delves.";
            case DIED -> "**" + save.getCharacter().getName() + "** died on delve " + result.episodes()
                    + " at level " + result.finalLevel() + ". Roll or pregen a new character to try again.";
            case EXHAUSTED -> "After " + result.episodes() + " delves, **" + save.getCharacter().getName()
                    + "** reached level " + result.finalLevel() + " (" + result.finalXp()
                    + " XP). Run `" + ctx.getPrefix() + "autodelve " + target + "` again to push further.";
        });
        if (!result.log().isEmpty()) {
            sb.append("\n```\n");
            result.log().forEach(line -> sb.append(line).append('\n'));
            sb.append("```");
        }
        ctx.reply(sb.toString());
        if (save.getCharacter().isAlive()) {
            ctx.replyEmbed(CharacterSheets.embed(save.getCharacter()));
        }
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[level] [fast|bx]");
        help.addDescription("Simulates delves to level your character up to the target level (default 5). "
                + "Default pace is by-the-book B/X OSE (slow, authentic); add `fast` for roughly one "
                + "level every 3-4 delves. The character earns its levels and can die.");
    }
}
