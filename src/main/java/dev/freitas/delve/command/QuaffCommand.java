package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Component;

/** Drinks a potion of healing: {@code /quaff}. */
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
        Character c = save.getCharacter();
        if (c.getHealingPotions() <= 0) {
            ctx.reply("You have no potions of healing.");
            return;
        }
        c.setHealingPotions(c.getHealingPotions() - 1);
        int healed = POTION.roll(dice);
        int before = c.getCurrentHp();
        c.setCurrentHp(Math.min(c.getMaxHp(), c.getCurrentHp() + healed));
        ctx.getBeans().gameState.save(userId, save);
        ctx.reply("You quaff a potion of healing and recover " + (c.getCurrentHp() - before) + " hp ("
                + c.getCurrentHp() + "/" + c.getMaxHp() + "). " + c.getHealingPotions() + " left.");
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("");
        help.addDescription("Drinks a potion of healing (2d4+2 hp), if you have one.");
    }
}
