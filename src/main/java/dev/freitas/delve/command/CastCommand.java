package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.Spell;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.SpellService;
import org.springframework.stereotype.Component;

/** Casts a prepared spell: {@code /cast [pc-name] <spell> [target]}. Routes to combat or out-of-combat
    resolution. */
@Component
public class CastCommand extends Command {

    private final CombatService combat;
    private final SpellService spells;

    public CastCommand(CombatService combat, SpellService spells) {
        super("cast", "c");
        this.combat = combat;
        this.spells = spells;
    }

    @Override
    public void invoke(CommandContext ctx) {
        long userId = ctx.getInvokerUserId();
        SaveGame save = ctx.getBeans().gameState.load(userId);
        if (!save.hasCharacter()) {
            ctx.reply("Roll a character first with `" + ctx.getPrefix() + "roll-character <class>`.");
            return;
        }

        String arg = ctx.getArgumentText().trim();
        if (arg.isBlank()) {
            ctx.reply("Cast what? `cast <spell> [target]`. Prepared: " + preparedList(save));
            return;
        }

        // An optional leading PC-name names the acting caster in a multi-PC party.
        String actorToken = null;
        int firstSpace = arg.indexOf(' ');
        String firstToken = firstSpace > 0 ? arg.substring(0, firstSpace) : arg;
        if (firstSpace > 0 && save.resolve(firstToken) instanceof Character) {
            actorToken = firstToken;
            arg = arg.substring(firstSpace + 1).trim();
        }

        // A trailing number is an optional target index; the rest is the spell name.
        Integer target = null;
        String spellName = arg;
        int lastSpace = arg.lastIndexOf(' ');
        if (lastSpace > 0) {
            try {
                target = Integer.parseInt(arg.substring(lastSpace + 1).trim());
                spellName = arg.substring(0, lastSpace).trim();
            } catch (NumberFormatException ignored) {
                // no trailing target
            }
        }

        Spell spell = Spell.parse(spellName);
        if (spell == null) {
            ctx.reply("You don't know a spell called \"" + spellName + "\". Prepared: " + preparedList(save));
            return;
        }

        ExplorationResult result = save.getSession().getState() == SessionState.IN_COMBAT
                ? combat.castRound(save, actorToken, spell, target)
                : spells.castOutOfCombat(save, spell);
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply(result.text());
    }

    private String preparedList(SaveGame save) {
        if (save.getCharacter().getMemorizedSpells().isEmpty()) {
            return "(none — rest in town or `prepare`)";
        }
        return save.getCharacter().getMemorizedSpells().stream()
                .map(Spell::valueOf)
                .map(Spell::displayName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("(none)");
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[pc-name] <spell> [target]");
        help.addDescription("Casts one of your prepared spells. Damage/sleep spells need a fight; "
                + "healing and utility spells can be cast any time. In a multi-PC party, name a PC first "
                + "to have them cast — every other living PC and retainer still auto-attacks that round.");
    }
}
