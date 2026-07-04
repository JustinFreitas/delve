package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.Formation;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Views or sets the party's marching order: {@code /order} or {@code /order <name1> <name2> ...}. */
@Component
public class OrderCommand extends Command {

    public OrderCommand() {
        super("order", "formation");
    }

    @Override
    public void invoke(CommandContext ctx) {
        SaveGame save = ctx.getBeans().gameState.load(ctx.getInvokerUserId());
        if (!save.hasCharacter()) {
            ctx.reply("Roll a character first with `" + ctx.getPrefix() + "roll-character <class>`.");
            return;
        }
        if (save.getSession().getState() == SessionState.IN_COMBAT) {
            ctx.reply("You can't reorder the party mid-fight.");
            return;
        }
        String args = ctx.getArgumentText().trim();
        if (args.isBlank()) {
            ctx.reply(describeOrder(save));
            return;
        }

        String[] tokens = args.split("\\s+");
        List<String> newOrder = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String token : tokens) {
            String resolved = resolveToken(save, token);
            if (resolved == null) {
                unknown.add(token);
            } else if (seen.add(resolved)) {
                newOrder.add(resolved);
            }
        }
        if (!unknown.isEmpty()) {
            ctx.reply("Don't recognize: " + String.join(", ", unknown)
                    + ". Use `me`/your character's name, or a retainer's name from `" + ctx.getPrefix() + "party`.");
            return;
        }

        List<String> unlisted = new ArrayList<>();
        if (!seen.contains(SaveGame.PLAYER_SLOT)) {
            unlisted.add(save.getCharacter().getName());
        }
        for (Retainer r : save.livingRetainers()) {
            if (!seen.contains(r.getName())) {
                unlisted.add(r.getName());
            }
        }

        save.setMarchingOrder(newOrder);
        ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);

        StringBuilder reply = new StringBuilder("Marching order set.\n").append(describeOrder(save));
        if (!unlisted.isEmpty()) {
            reply.append("\n(").append(String.join(", ", unlisted))
                    .append(unlisted.size() == 1 ? " was" : " were").append(" not listed — placed at the back.)");
        }
        ctx.reply(reply.toString());
    }

    /** Resolves "me"/the character's own name to the reserved player slot, else a retainer's stored name. */
    private String resolveToken(SaveGame save, String token) {
        if (token.equalsIgnoreCase("me") || token.equalsIgnoreCase(save.getCharacter().getName())) {
            return SaveGame.PLAYER_SLOT;
        }
        for (Retainer r : save.getRetainers()) {
            if (r.getName().equalsIgnoreCase(token)) {
                return r.getName();
            }
        }
        return null;
    }

    private String describeOrder(SaveGame save) {
        int width = save.getSession().isInDungeon() ? save.getSession().currentRoom().getCorridorWidth() : 2;
        List<List<Combatant>> ranks = Formation.ranks(save.fullOrder(), width);
        if (ranks.isEmpty()) {
            return "Your marching order is empty.";
        }
        StringBuilder sb = new StringBuilder("**Marching order** (width " + width + ")\n");
        for (int i = 0; i < ranks.size(); i++) {
            List<String> names = new ArrayList<>();
            for (Combatant c : ranks.get(i)) {
                names.add(c instanceof Character ? "You" : c.getName());
            }
            sb.append(i == 0 ? "Front" : "Rank " + (i + 1)).append(": ").append(String.join(", ", names)).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[name1 name2 ...]");
        help.addDescription("Views or sets your marching order, front to back (`me`/your name for yourself, "
                + "retainer names otherwise). Unlisted living members are placed at the back. Not usable mid-combat.");
    }
}
