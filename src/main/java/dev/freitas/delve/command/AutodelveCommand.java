package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.AutodelveService;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Runs simulated delves for the invoker's character: {@code /autodelve [level]}. A target at or below
 * the character's current level (the default, with no argument) runs a single delve; a higher target
 * fast-forwards through repeated delves until it's reached. The character earns its levels through
 * real (simulated) combat and can die. The result is persisted, so a survivor can be exported for the
 * table.
 */
@Component
public class AutodelveCommand extends Command {

    // A Discord message caps at 2000 chars; leave headroom for the outcome line and code fence.
    private static final int INLINE_LOG_LIMIT = 1600;

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

        // Args: "<level> [fast|bx] [verbose|milestones]" — level defaults to the character's current
        // level (a single delve), pace defaults to by-the-book B/X OSE, log detail to verbose.
        int target = save.getCharacter().getLevel();
        AutodelveService.Pace pace = AutodelveService.Pace.BX_OSE;
        AutodelveService.LogDetail detail = AutodelveService.LogDetail.VERBOSE;
        for (String token : ctx.getArgumentText().trim().toLowerCase().split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if (token.equals("fast")) {
                pace = AutodelveService.Pace.FAST;
            } else if (token.equals("bx") || token.equals("ose")) {
                pace = AutodelveService.Pace.BX_OSE;
            } else if (token.equals("verbose") || token.equals("full") || token.equals("detailed")) {
                detail = AutodelveService.LogDetail.VERBOSE;
            } else if (token.equals("milestones") || token.equals("summary")) {
                detail = AutodelveService.LogDetail.MILESTONES;
            } else {
                try {
                    target = Integer.parseInt(token);
                } catch (NumberFormatException e) {
                    ctx.reply("Usage: `" + ctx.getPrefix() + "autodelve [level] [fast|bx] [verbose|milestones]` "
                            + "(default: your current level, i.e. one delve; B/X OSE pace; verbose log).");
                    return;
                }
            }
        }
        if (target < 1 || target > 14) {
            ctx.reply("Target level must be between 1 and 14.");
            return;
        }

        AutodelveService.Result result = autodelve.run(save, target, pace, detail);
        ctx.getBeans().gameState.save(userId, save);

        String outcomeLine = switch (result.outcome()) {
            case REACHED_TARGET -> "**" + save.getCharacter().getName() + "** clawed up from level "
                    + result.startLevel() + " to **level " + result.finalLevel() + "** over "
                    + result.episodes() + " delves.";
            case DIED -> "**" + save.getCharacter().getName() + "** died on delve " + result.episodes()
                    + " at level " + result.finalLevel() + ". Roll or pregen a new character to try again.";
            case EXHAUSTED -> "After " + result.episodes() + " delves, **" + save.getCharacter().getName()
                    + "** reached level " + result.finalLevel() + " (" + result.finalXp()
                    + " XP). Run `" + ctx.getPrefix() + "autodelve " + target + "` again to push further.";
            case SINGLE_DELVE -> "**" + save.getCharacter().getName() + "** delved and returned to town at level "
                    + result.finalLevel() + " (" + result.finalXp() + " XP, " + save.getCharacter().getGold() + " gp).";
        };

        String logText = String.join("\n", result.log());
        if (logText.length() > INLINE_LOG_LIMIT) {
            // Too long for a single Discord message (common in verbose mode) — send the log as a file.
            ctx.reply(outcomeLine);
            ctx.replyFile(save.getCharacter().getName() + "-delve-log.txt",
                    logText.getBytes(StandardCharsets.UTF_8), null);
        } else if (!logText.isEmpty()) {
            ctx.reply(outcomeLine + "\n```\n" + logText + "\n```");
        } else {
            ctx.reply(outcomeLine);
        }
        if (save.getCharacter().isAlive()) {
            ctx.replyEmbed(CharacterSheets.embed(save.getCharacter()));
            ctx.reply(PartySummary.text(save));
        }
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[level] [fast|bx] [verbose|milestones]");
        help.addDescription("Simulates delves for your character. A target at or below your current level "
                + "(the default, with no argument) runs a single delve; a higher target fast-forwards "
                + "through repeated delves until reached. Default pace is by-the-book B/X OSE (slow, "
                + "authentic); add `fast` for roughly one level every 3-4 delves. Log detail defaults to "
                + "`verbose` (every action's full narration, every turn — sent as a file if long); "
                + "`milestones` reports only curated significant events instead. Shows your party "
                + "afterward. The character earns its levels and can die.");
    }
}
