package dev.freitas.delve.command;

import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.RetainerFactory;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.RetainerRules;
import dev.freitas.delve.game.engine.Toughness;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Recruits a retainer in town: {@code /hire [pc-name] <class> [name]}, or bulk-hires with
    {@code ... all}. An optional leading PC-name says whose gold pays the hiring fee and whose Charisma
    governs the loyalty roll and retainer-count cap for this hire, in a multi-PC party — the retainer
    still joins the party's one shared retainer pool afterward. */
@Component
public class HireCommand extends Command {

    static final int HIRING_FEE = 25; // gp advance paid up front
    private static final List<String> NAMES = List.of(
            "Aldo", "Bryn", "Cora", "Doran", "Esna", "Fendrel", "Gwen", "Holt",
            "Isolde", "Jorath", "Kael", "Lyra", "Mott", "Nessa", "Orin", "Petra");

    /** How `... all` picks a class per open slot. */
    enum BulkMode {
        SINGLE,
        SMART,
        RANDOM
    }

    /** All classes, tankiest-first (lower armor-class proxy, then higher hit-die), the same ranking
        {@link Toughness} applies to live combatants — filling every slot naturally hires one of each,
        tankiest-first; fewer slots hires the N tankiest classes. */
    static final List<CharacterClass> SMART_HIRE_ORDER = smartHireOrder();

    private static List<CharacterClass> smartHireOrder() {
        List<CharacterClass> classes = new ArrayList<>(List.of(CharacterClass.values()));
        classes.sort(Toughness.byToughness(HireCommand::retainerArmorProxy, CharacterClass::hitDie));
        return List.copyOf(classes);
    }

    /** Ascending-AC proxy for a class's retainer starting kit, from the same table
        {@link RetainerFactory#equipmentFor} uses for a real hire — no second armor table to maintain. */
    private static int retainerArmorProxy(CharacterClass cls) {
        RetainerFactory.Equipment eq = RetainerFactory.equipmentFor(cls);
        return eq.armor().baseArmorClass() - (eq.shield() ? 1 : 0);
    }

    private final RetainerFactory retainerFactory;
    private final Dice dice;

    public HireCommand(RetainerFactory retainerFactory, Dice dice) {
        super("hire", "recruit");
        this.retainerFactory = retainerFactory;
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
            ctx.reply("You can only hire retainers in town, between delves.");
            return;
        }

        String argsText = ctx.getArgumentText().trim();
        boolean solo = save.getCharacters().size() == 1;

        // An optional leading PC-name says whose gold pays the hiring fee and whose Charisma governs
        // the loyalty roll and retainer-count cap for this hire, in a multi-PC party.
        Character payer = save.getCharacter();
        int leadSpace = argsText.indexOf(' ');
        String leadToken = leadSpace > 0 ? argsText.substring(0, leadSpace) : argsText;
        if (leadSpace > 0 && save.resolve(leadToken) instanceof Character named) {
            payer = named;
            argsText = argsText.substring(leadSpace + 1).trim();
        }

        int max = RetainerRules.maxRetainers(payer.getAbilities().score(Ability.CHA));
        if (save.getRetainers().size() >= max) {
            ctx.reply((solo ? "Your" : payer.getName() + "'s") + " Charisma supports at most **" + max
                    + "** retainers. Dismiss one first.");
            return;
        }

        String[] tokens = argsText.split("\\s+", 2);
        String first = tokens.length > 0 ? tokens[0] : "";
        String rest = tokens.length > 1 ? tokens[1].trim() : "";

        // --- bulk-hire dispatch: "all" is a reserved keyword in this position (see provideHelp) ---
        if (first.equalsIgnoreCase("all") && rest.isBlank()) {
            runBulkHire(ctx, save, payer, max, BulkMode.SMART, null);
            return;
        }
        if (first.equalsIgnoreCase("smart") && rest.equalsIgnoreCase("all")) {
            runBulkHire(ctx, save, payer, max, BulkMode.SMART, null);
            return;
        }
        if (first.equalsIgnoreCase("random") && rest.equalsIgnoreCase("all")) {
            runBulkHire(ctx, save, payer, max, BulkMode.RANDOM, null);
            return;
        }
        CharacterClass cls = CharacterClass.parse(first);
        if (cls != null && rest.equalsIgnoreCase("all")) {
            runBulkHire(ctx, save, payer, max, BulkMode.SINGLE, cls);
            return;
        }

        // --- existing single-hire path ---
        if (cls == null) {
            ctx.reply("Hire whom? `hire [pc-name] <class> [name]` — e.g. `hire fighter Bryn`. "
                    + "Classes: cleric, fighter, magic-user, thief, dwarf, elf, halfling. "
                    + "Or bulk-hire with `hire all`, `hire smart all`, `hire random all`, or `hire <class> all`.");
            return;
        }
        if (payer.getGold() < HIRING_FEE) {
            ctx.reply("You need a **" + HIRING_FEE + " gp** advance to hire a retainer; "
                    + (solo ? "you have" : payer.getName() + " has") + " " + payer.getGold() + " gp.");
            return;
        }

        String name = !rest.isBlank() ? rest : NAMES.get(dice.d(NAMES.size()) - 1);
        int goldBefore = payer.getGold();
        Retainer retainer = hireOne(save, payer, cls, name);
        int banked = payer.getGold() - goldBefore + HIRING_FEE;
        ctx.getBeans().gameState.save(userId, save);

        ctx.reply("**" + name + "**, a level 1 " + cls.displayName() + ", joins "
                + (solo ? "you" : payer.getName() + "'s party") + " for a " + HIRING_FEE
                + " gp advance (and a half-share of spoils), bringing **" + banked
                + " gp** banked into the party's purse.\n"
                + name + ": " + retainer.getMaxHp() + " hp, AC " + retainer.armorClass()
                + ", loyalty " + retainer.getLoyalty() + " (" + RetainerRules.loyaltyDescriptor(retainer.getLoyalty()) + ").");
    }

    static int slotsAvailable(int max, int currentRetainerCount) {
        return max - currentRetainerCount;
    }

    static int affordableHires(int gold) {
        return gold / HIRING_FEE;
    }

    /** Hires one retainer of {@code cls} named {@code name}, deducting {@link #HIRING_FEE} from and
        banking a starting-gold roll into {@code payer}'s purse, seeding loyalty from their Charisma.
        Caller must already have checked gold/cap. Package-private: tests exercise this directly
        without a {@link CommandContext}. */
    Retainer hireOne(SaveGame save, Character payer, CharacterClass cls, String name) {
        int loyalty = RetainerRules.baseLoyalty(payer.getAbilities().score(Ability.CHA));
        Retainer retainer = retainerFactory.create(name, cls, 1, loyalty);
        int banked = dice.roll(3, 6);
        payer.setGold(payer.getGold() - HIRING_FEE + banked);
        save.getRetainers().add(retainer);
        return retainer;
    }

    /** Hires up to {@code count} retainers per {@code mode}, deducting {@link #HIRING_FEE} gold per
        hire as it goes via {@link #hireOne}. {@code count} must already be clamped to both the CHA
        cap and affordable gold by the caller. Package-private: tests exercise this directly without a
        {@link CommandContext}. */
    List<Retainer> bulkHire(SaveGame save, Character pc, BulkMode mode, CharacterClass fixedClass, int count) {
        List<String> pool = new ArrayList<>(NAMES);
        List<Retainer> hired = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CharacterClass cls = switch (mode) {
                case SINGLE -> fixedClass;
                case SMART -> SMART_HIRE_ORDER.get(i % SMART_HIRE_ORDER.size());
                case RANDOM -> CharacterClass.values()[dice.d(CharacterClass.values().length) - 1];
            };
            // Draw without replacement: <= 7 hires (the CHA cap ceiling), 16 names -> never a duplicate.
            String name = pool.remove(dice.d(pool.size()) - 1);
            hired.add(hireOne(save, pc, cls, name));
        }
        return hired;
    }

    private void runBulkHire(
            CommandContext ctx, SaveGame save, Character pc, int max, BulkMode mode, CharacterClass fixedClass) {
        boolean solo = save.getCharacters().size() == 1;
        int affordable = affordableHires(pc.getGold());
        if (affordable <= 0) {
            ctx.reply("You need a **" + HIRING_FEE + " gp** advance to hire a retainer; "
                    + (solo ? "you have" : pc.getName() + " has") + " " + pc.getGold() + " gp.");
            return;
        }
        int count = Math.min(slotsAvailable(max, save.getRetainers().size()), affordable);
        int goldBefore = pc.getGold();
        List<Retainer> hired = bulkHire(save, pc, mode, fixedClass, count);
        // Fees paid, offset by each retainer's banked coin joining the party's purse.
        int totalBanked = pc.getGold() - goldBefore + count * HIRING_FEE;
        ctx.getBeans().gameState.save(ctx.getInvokerUserId(), save);
        ctx.reply(formatBulkHireReply(hired, mode, fixedClass, pc.getGold(), totalBanked));
    }

    private String formatBulkHireReply(
            List<Retainer> hired, BulkMode mode, CharacterClass fixedClass, int remainingGold, int totalBanked) {
        int spent = hired.size() * HIRING_FEE;
        StringBuilder sb = new StringBuilder("Hired **" + hired.size() + "** retainer" + (hired.size() == 1 ? "" : "s")
                + " (" + modeLabel(mode, fixedClass) + ") for **" + spent + " gp** (offset by **" + totalBanked
                + " gp** banked from their savings); " + remainingGold + " gp left.\n```\n");
        for (Retainer r : hired) {
            sb.append(String.format("%-16s %-11s %3d hp  AC %d  loyalty %d (%s)%n",
                    r.getName(), r.getCharacterClass().displayName(), r.getMaxHp(), r.armorClass(),
                    r.getLoyalty(), RetainerRules.loyaltyDescriptor(r.getLoyalty())));
        }
        return sb.append("```").toString();
    }

    private String modeLabel(BulkMode mode, CharacterClass fixedClass) {
        return switch (mode) {
            case SINGLE -> fixedClass.displayName().toLowerCase() + "s";
            case SMART -> "smart mix";
            case RANDOM -> "random mix";
        };
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("[pc-name] <class> [name]");
        help.addUsage("[pc-name] <class> all");
        help.addUsage("[pc-name] [smart|random] all");
        help.addDescription("Hires a retainer to adventure with you (in town). They fight alongside you "
                + "and earn a half-share of XP. Your Charisma caps how many you can lead. In a multi-PC "
                + "party, name a PC first to hire using their gold and Charisma instead of your "
                + "first-rolled PC's.");
        help.addDescription("Bulk-hire with `... all`, filling every open slot you can afford (" + HIRING_FEE
                + " gp/head): `<class> all` hires one class throughout, `smart all` (also just `all` — the "
                + "default) picks a front-loaded, tankiest-first mix, `random all` picks a uniformly random "
                + "class per slot. \"all\" is reserved here and can't be used as a retainer's name.");
    }
}
