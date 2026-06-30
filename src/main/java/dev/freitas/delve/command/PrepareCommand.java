package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.Spell;
import dev.freitas.delve.game.engine.SpellTables;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.SpellService;
import org.springframework.stereotype.Component;

/** Memorizes a spell into an open slot: {@code /prepare <spell>} (not during combat). */
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
        if (!SpellTables.isCaster(save.getCharacter().getCharacterClass())) {
            ctx.reply("Your class cannot cast spells.");
            return;
        }
        if (save.getSession().getState() == SessionState.IN_COMBAT) {
            ctx.reply("You cannot prepare spells in the middle of a fight.");
            return;
        }
        String name = ctx.getArgumentText().trim();
        if (name.isBlank()) {
            ctx.reply("Prepare which spell? Available: " + availableList(save));
            return;
        }
        Spell spell = Spell.parse(name);
        if (spell == null) {
            ctx.reply("Unknown spell \"" + name + "\". Available: " + availableList(save));
            return;
        }
        ExplorationResult result = spells.prepare(save.getCharacter(), spell);
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply(result.text());
    }

    private String availableList(SaveGame save) {
        return spells.available(save.getCharacter()).stream()
                .map(Spell::displayName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("(none)");
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<spell>");
        help.addDescription("Memorizes a spell into a free slot. Resting in town refills all slots.");
    }
}
