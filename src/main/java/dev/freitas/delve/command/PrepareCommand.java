package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.Spell;
import dev.freitas.delve.game.engine.SpellTables;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.SpellService;
import org.springframework.stereotype.Component;

/** Memorizes a spell into an open slot: {@code /prepare [pc-name] <spell>} (not during combat). */
@Component
public class PrepareCommand extends Command {

    private final SpellService spells;

    public PrepareCommand(SpellService spells) {
        super("prepare", "memorize");
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
        String argsText = ctx.getArgumentText().trim();
        boolean solo = save.getCharacters().size() == 1;

        // An optional leading PC-name names which caster prepares the spell in a multi-PC party.
        Character pc = save.getCharacter();
        int leadSpace = argsText.indexOf(' ');
        String leadToken = leadSpace > 0 ? argsText.substring(0, leadSpace) : argsText;
        if (leadSpace > 0 && save.resolve(leadToken) instanceof Character named) {
            pc = named;
            argsText = argsText.substring(leadSpace + 1).trim();
        }

        if (!SpellTables.isCaster(pc.getCharacterClass())) {
            ctx.reply((solo ? "Your" : pc.getName() + "'s") + " class cannot cast spells.");
            return;
        }
        if (save.getSession().getState() == SessionState.IN_COMBAT) {
            ctx.reply("You cannot prepare spells in the middle of a fight.");
            return;
        }
        String name = argsText;
        if (name.isBlank()) {
            ctx.reply("Prepare which spell? Available: " + availableList(pc));
            return;
        }
        Spell spell = Spell.parse(name);
        if (spell == null) {
            ctx.reply("Unknown spell \"" + name + "\". Available: " + availableList(pc));
            return;
        }
        ExplorationResult result = spells.prepare(pc, spell);
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply(solo ? result.text() : pc.getName() + ": " + result.text());
    }

    private String availableList(Character pc) {
        return spells.available(pc).stream()
                .map(Spell::displayName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("(none)");
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[pc-name] <spell>");
        help.addDescription("Memorizes a spell into a free slot. Resting in town refills all slots. In "
                + "a multi-PC party, name a caster PC first; defaults to your first-rolled PC.");
    }
}
