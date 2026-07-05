package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Component;

/** Drinks a potion of healing: {@code /quaff [pc-name]}. Each PC carries their own potions. */
@Component
public class QuaffCommand extends Command {

    private static final DamageRoll POTION = new DamageRoll(2, 4, 2); // potion of healing: 2d4+2

    private final Dice dice;

    public QuaffCommand(Dice dice) {
        super("quaff", "drink");
        this.dice = dice;
    }

    @Override
    public void invoke(CommandContext ctx) {
        long userId = ctx.getInvokerUserId();
        SaveGame save = ctx.getBeans().gameState.load(userId);
        if (!save.hasCharacter()) {
            ctx.reply("Roll a character first with `" + ctx.getPrefix() + "roll-character <class>`.");
            return;
        }
        String token = ctx.getArgumentText().trim();
        Character c = token.isBlank() ? save.getCharacter()
                : (save.resolve(token) instanceof Character named ? named : null);
        if (c == null) {
            ctx.reply("Don't recognize **" + token + "**. Use `me`/a character's name from `"
                    + ctx.getPrefix() + "party`.");
            return;
        }
        boolean solo = save.getCharacters().size() == 1;
        if (c.getHealingPotions() <= 0) {
            ctx.reply((solo ? "You have" : c.getName() + " has") + " no potions of healing.");
            return;
        }
        c.setHealingPotions(c.getHealingPotions() - 1);
        int healed = POTION.roll(dice);
        int before = c.getCurrentHp();
        c.setCurrentHp(Math.min(c.getMaxHp(), c.getCurrentHp() + healed));
        ctx.getBeans().gameState.save(userId, save);
        String who = solo ? "You" : c.getName();
        ctx.reply(who + " " + (solo ? "quaff" : "quaffs") + " a potion of healing and "
                + (solo ? "recover" : "recovers") + " " + (c.getCurrentHp() - before) + " hp ("
                + c.getCurrentHp() + "/" + c.getMaxHp() + "). " + c.getHealingPotions() + " left.");
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[pc-name]");
        help.addDescription("Drinks a potion of healing (2d4+2 hp), if you have one — your first-rolled "
                + "PC by default, or name a specific PC in a multi-PC party.");
    }
}
