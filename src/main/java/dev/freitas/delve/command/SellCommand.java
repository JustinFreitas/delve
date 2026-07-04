package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.GearCatalog;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Component;

/** Sells a recognized inventory item, worn armor, or a shield back for gold, in town: {@code /sell <item>}.
    Used equipment sells for 10% of its {@link GearCatalog} price, plus a haggle bonus from a 2d6 + CHA
    reaction-style roll. */
@Component
public class SellCommand extends Command {

    private static final int BASE_SALE_PERCENT = 10;

    private final Dice dice;

    public SellCommand(Dice dice) {
        super("sell");
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
        if (save.getSession().isInDungeon()) {
            ctx.reply("You can only sell gear in town.");
            return;
        }
        String name = ctx.getArgumentText().trim();
        if (name.isBlank()) {
            ctx.reply("Sell what? `sell <item>` — check `sheet` for your inventory. Or `sell armor`/`sell shield`.");
            return;
        }
        Character character = save.getCharacter();

        if (name.equalsIgnoreCase("shield")) {
            if (!character.isShield()) {
                ctx.reply("You don't have a shield to sell.");
                return;
            }
            sell(ctx, save, character, "Shield", () -> character.setShield(false));
            return;
        }
        if (name.equalsIgnoreCase("armor")) {
            if (character.getArmor() == Armor.NONE) {
                ctx.reply("You have no armor to sell.");
                return;
            }
            Armor worn = character.getArmor();
            sell(ctx, save, character, worn.displayName(), () -> character.setArmor(Armor.NONE));
            return;
        }

        String match = InventoryMatcher.find(character, name);
        if (match == null) {
            ctx.reply("You don't have **" + name + "** in your inventory.");
            return;
        }
        int price = GearCatalog.priceGp(match);
        if (price < 0) {
            ctx.reply("The trader can't appraise **" + match + "** — no sale.");
            return;
        }
        sell(ctx, save, character, match, () -> character.getInventory().remove(match));
    }

    private void sell(CommandContext ctx, SaveGame save, Character character, String itemLabel, Runnable remove) {
        int price = GearCatalog.priceGp(itemLabel);
        int bonusPercent = haggleBonusPercent(character);
        int amount = Math.max(1, price * (BASE_SALE_PERCENT + bonusPercent) / 100);

        remove.run();
        character.setGold(character.getGold() + amount);
        ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
        String haggleNote = bonusPercent > 0 ? " (haggled up from the base 10%)" : "";
        ctx.reply("You sell **" + itemLabel + "** for **" + amount + " gp**" + haggleNote + "; "
                + character.getGold() + " gp now.");
    }

    /** A 2d6 + CHA-modifier roll banded into a bonus percentage on top of the flat 10% sale rate —
        standing in for the rulebook's separate Jeweler/Trader haggling table (gems/jewelry aren't
        modeled as sellable items here, so only one table is needed). Mirrors
        {@code CombatService.reaction}'s exact roll shape. Package-private: tests exercise this
        directly without a {@link CommandContext}. */
    int haggleBonusPercent(Character character) {
        int roll = dice.roll2d6() + character.getAbilities().modifier(Ability.CHA);
        if (roll <= 2) {
            return 0;
        }
        if (roll <= 5) {
            return 5;
        }
        if (roll <= 8) {
            return 10;
        }
        if (roll <= 11) {
            return 15;
        }
        return 25;
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<item>");
        help.addUsage("armor");
        help.addUsage("shield");
        help.addDescription("Sells a recognized inventory item (or your worn armor/shield) back for "
                + BASE_SALE_PERCENT + "% of its price, plus a haggle bonus (2d6 + CHA modifier) of up to "
                + "+25% on a great roll. Items the trader can't appraise (loot/magic items with no listed "
                + "price) can't be sold this way.");
    }
}
