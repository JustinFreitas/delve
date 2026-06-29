package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.RetainerFactory;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.RetainerRules;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/** Recruits a retainer in town: {@code /hire <class> [name]}. */
@Component
public class HireCommand extends Command {

    private static final int HIRING_FEE = 25; // gp advance paid up front
    private static final List<String> NAMES = List.of(
            "Aldo", "Bryn", "Cora", "Doran", "Esna", "Fendrel", "Gwen", "Holt",
            "Isolde", "Jorath", "Kael", "Lyra", "Mott", "Nessa", "Orin", "Petra");

    private final RetainerFactory retainerFactory;

    public HireCommand(RetainerFactory retainerFactory) {
        super("hire", "recruit");
        this.retainerFactory = retainerFactory;
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
            ctx.reply("You can only hire retainers in town, between delves.");
            return;
        }
        Character pc = save.getCharacter();
        int max = RetainerRules.maxRetainers(pc.getAbilities().score(Ability.CHA));
        if (save.getRetainers().size() >= max) {
            ctx.reply("Your Charisma supports at most **" + max + "** retainers. Dismiss one first.");
            return;
        }

        String[] tokens = ctx.getArgumentText().trim().split("\\s+", 2);
        CharacterClass cls = CharacterClass.parse(tokens.length > 0 ? tokens[0] : "");
        if (cls == null) {
            ctx.reply("Hire whom? `hire <class> [name]` — e.g. `hire fighter Bryn`. "
                    + "Classes: cleric, fighter, magic-user, thief, dwarf, elf, halfling.");
            return;
        }
        if (pc.getGold() < HIRING_FEE) {
            ctx.reply("You need a **" + HIRING_FEE + " gp** advance to hire a retainer; you have "
                    + pc.getGold() + " gp.");
            return;
        }

        String name = tokens.length > 1 && !tokens[1].isBlank()
                ? tokens[1].trim()
                : NAMES.get(ThreadLocalRandom.current().nextInt(NAMES.size()));
        int loyalty = RetainerRules.baseLoyalty(pc.getAbilities().score(Ability.CHA));
        Retainer retainer = retainerFactory.create(name, cls, 1, loyalty);

        pc.setGold(pc.getGold() - HIRING_FEE);
        save.getRetainers().add(retainer);
        ctx.getBeans().gameState.save(userId, save);

        ctx.reply("**" + name + "**, a level 1 " + cls.displayName() + ", joins you for a " + HIRING_FEE
                + " gp advance (and a half-share of spoils).\n"
                + name + ": " + retainer.getMaxHp() + " hp, AC " + retainer.armorClass()
                + ", loyalty " + loyalty + " (" + RetainerRules.loyaltyDescriptor(loyalty) + ").");
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<class> [name]");
        help.addDescription("Hires a retainer to adventure with you (in town). They fight alongside you "
                + "and earn a half-share of XP. Your Charisma caps how many you can lead.");
    }
}
