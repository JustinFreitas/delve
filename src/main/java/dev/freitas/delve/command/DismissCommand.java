package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.LightingService;
import org.springframework.stereotype.Component;

/** Releases a retainer from service: {@code /dismiss [pc-name] <name>}. An optional leading PC-name
    says who's doing the dismissing, in a multi-PC party — same idiom as {@code /hire}. A PC may only
    dismiss their own retainers (see {@link #canDismiss}). */
@Component
public class DismissCommand extends Command {

    private final LightingService lighting;

    public DismissCommand(LightingService lighting) {
        super("dismiss", "fire");
        this.lighting = lighting;
    }

    @Override
    public void invoke(CommandContext ctx) {
        long userId = ctx.getInvokerUserId();
        SaveGame save = ctx.getBeans().gameState.load(userId);
        if (!save.hasCharacter() || save.getRetainers().isEmpty()) {
            ctx.reply("You have no retainers to dismiss.");
            return;
        }
        String argsText = ctx.getArgumentText().trim();
        if (argsText.isBlank()) {
            ctx.reply("Dismiss whom? `dismiss [pc-name] <name>`. Use `" + ctx.getPrefix() + "party` to see names.");
            return;
        }

        SaveGame.PcNameToken resolved = resolveActorAndName(save, argsText);
        Character actor = resolved.actor();
        String name = resolved.remainder();
        if (name.isBlank()) {
            ctx.reply("Dismiss whom? `dismiss [pc-name] <name>`. Use `" + ctx.getPrefix() + "party` to see names.");
            return;
        }
        Retainer match = save.getRetainers().stream()
                .filter(r -> r.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (match == null) {
            ctx.reply("No retainer named **" + name + "** is in your party.");
            return;
        }
        if (!canDismiss(save, actor, match)) {
            Character owner = save.ownerOf(match);
            ctx.reply("**" + match.getName() + "** is " + owner.getName() + "'s retainer — name them "
                    + "first: `" + ctx.getPrefix() + "dismiss " + owner.getName() + " " + match.getName() + "`.");
            return;
        }
        save.getRetainers().remove(match);
        ExplorationResult result = new ExplorationResult();
        result.add("You release **" + match.getName() + "** from service.");
        lighting.reconcileBearer(save, result);
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply(result.text());
    }

    /** Whether {@code actor} may dismiss {@code retainer} — its owner, per {@link SaveGame#ownerOf}.
        Solo saves always permit it, since there's only one possible owner. Package-private: tests
        exercise this directly without a {@link CommandContext}. */
    static boolean canDismiss(SaveGame save, Character actor, Retainer retainer) {
        return save.getCharacters().size() == 1 || save.ownerOf(retainer) == actor;
    }

    /** Resolves the acting PC and target retainer name from the command's {@code [pc-name] <name>}
        argument text. A multi-word retainer name (e.g. hired via `/hire fighter Bram the Bold`) must
        not be split just because its first word happens to match a live PC's name, so the whole text is
        checked against real retainer names first; only when that whole-text match fails is a leading
        PC-name token peeled off via {@link SaveGame#peelLeadingPcName}. Package-private: tests exercise
        this directly without a {@link CommandContext}. */
    static SaveGame.PcNameToken resolveActorAndName(SaveGame save, String argsText) {
        Character defaultActor = save.getCharacter();
        boolean wholeTextIsARetainerName = save.getRetainers().stream()
                .anyMatch(r -> r.getName().equalsIgnoreCase(argsText));
        if (wholeTextIsARetainerName) {
            return new SaveGame.PcNameToken(defaultActor, argsText);
        }
        return save.peelLeadingPcName(argsText, defaultActor);
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[pc-name] <name>");
        help.addDescription("Dismisses a retainer from your party. In a multi-PC party, you may only "
                + "dismiss your own retainers — name the owning PC first if it isn't your first-rolled PC.");
    }
}
