package dev.freitas.delve.command;

import dev.freitas.delve.game.CharacterFactory;
import dev.freitas.delve.game.Outfitter;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.GameSession;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import dev.freitas.delve.game.session.SpellService;
import org.springframework.stereotype.Component;

/**
 * Rolls up a new level-1 B/X character: {@code /roll-character <class> [name] [bare]}. Abilities are
 * rolled 3d6 in order; if they fail the chosen class's minimum requirements the roll is rejected (re-run
 * to try again, as in B/X). The first roll starts a fresh party; every roll after that adds another PC
 * to the party (up to {@link SaveGame#MAX_CHARACTERS}) instead of replacing anyone. By default the new
 * PC is immediately best-effort auto-geared ({@link Outfitter}) so they're ready to delve without a
 * manual shopping trip; a trailing {@code bare} token skips that for players who want to pick their own
 * gear via {@code /buy}.
 */
@Component
public class RollCharacterCommand extends Command {

    private final Dice dice;
    private final CharacterFactory characterFactory;
    private final SpellService spells;

    public RollCharacterCommand(Dice dice, CharacterFactory characterFactory, SpellService spells) {
        super("roll-character", "rollchar", "newchar");
        this.dice = dice;
        this.characterFactory = characterFactory;
        this.spells = spells;
    }

    @Override
    public void invoke(CommandContext ctx) {
        String[] tokens = ctx.getArgumentText().trim().split("\\s+", 2);
        String classToken = tokens.length > 0 ? tokens[0] : "";

        CharacterClass characterClass = CharacterClass.parse(classToken);
        if (characterClass == null) {
            ctx.reply(classListMessage());
            return;
        }

        String rest = tokens.length > 1 ? tokens[1].trim() : "";
        boolean bare = false;
        if (rest.equalsIgnoreCase("bare")) {
            bare = true;
            rest = "";
        } else if (rest.toLowerCase(java.util.Locale.ROOT).endsWith(" bare")) {
            bare = true;
            rest = rest.substring(0, rest.length() - " bare".length()).trim();
        }
        String name = rest.isBlank() ? ctx.getInvoker().getEffectiveName() : rest;

        AbilityScores abilities = AbilityScores.roll(dice);
        if (!characterClass.meetsRequirements(abilities)) {
            ctx.reply("Those rolls don't qualify for a **" + characterClass.displayName() + "**: needs "
                    + characterClass.unmetRequirements(abilities)
                    + ".\nRun `" + ctx.getTrigger() + " " + classToken + "` again to re-roll, or pick another class.");
            return;
        }

        SaveGame existing = ctx.getBeans().gameState.load(ctx.getInvokerUserId());
        boolean firstPc = !existing.hasCharacter();
        if (!firstPc && existing.getCharacters().size() >= SaveGame.MAX_CHARACTERS) {
            ctx.reply("Your party already has the maximum of **" + SaveGame.MAX_CHARACTERS
                    + "** characters. `dismiss` one first.");
            return;
        }
        // A second+ PC left unnamed defaults to the player's own Discord display name (above) -- the
        // same name every time, for the same player -- which would silently break name-based ownership
        // lookups (SaveGame.ownerOf/resolve) between this PC and any earlier one sharing it.
        if (!firstPc && nameCollides(existing, name)) {
            ctx.reply("**" + name + "** is already a name in your party — every PC and retainer needs "
                    + "a distinct name. Try `" + ctx.getTrigger() + " " + classToken + " <a different name>`.");
            return;
        }

        Character character = characterFactory.createBare(name, characterClass, abilities);
        spells.autoPrepare(character); // a fresh caster is ready to cast (no-op for non-casters)
        int startingGold = character.getGold();
        String outfitSummary = bare ? null : Outfitter.outfit(character);

        int partySizeAfter = existing.getCharacters().size() + 1;
        // Only the very first roll starts a fresh dungeon session — otherwise it would blow away an
        // in-progress delve's state (dungeon position, active combat, who's carrying the light, etc.)
        // for a party that's already adventuring.
        ctx.getBeans().gameState.mutate(ctx.getInvokerUserId(), save -> {
            if (firstPc) {
                save.setCharacter(character);
                save.setSession(new GameSession());
            } else {
                save.addCharacter(character);
            }
        });

        String gearLine = bare
                ? "You start with **" + startingGold + " gp** and no gear — head to `" + ctx.getPrefix()
                        + "buy <item>` to kit up (weapon, armor, a shield, torches, and the rest) before "
                        + "your first delve."
                : "Started with **" + startingGold + " gp** and " + outfitSummary;
        String reply = firstPc
                ? "Rolled up **" + name + "**, a level 1 " + characterClass.displayName() + "! " + gearLine
                : "Rolled up **" + name + "**, a level 1 " + characterClass.displayName()
                        + ", and added them to the party (" + partySizeAfter + "/" + SaveGame.MAX_CHARACTERS
                        + "). " + gearLine + " (note: `buy`/`wield` and a few other commands still act on "
                        + "your first-rolled PC only; naming a specific PC for those is coming soon).";
        ctx.reply(reply);
        ctx.replyEmbed(CharacterSheets.embed(character));
    }

    /** Whether {@code name} collides (case-insensitively) with any PC or retainer already in
        {@code save} — checked before adding a second+ PC, since name-based ownership lookups
        ({@link SaveGame#ownerOf}, {@link SaveGame#resolve}) require every party member's name to be
        unique. Package-private: tests exercise this directly without a {@link CommandContext}. */
    static boolean nameCollides(SaveGame save, String name) {
        return save.getCharacters().stream().anyMatch(c -> c.getName().equalsIgnoreCase(name))
                || save.getRetainers().stream().anyMatch(r -> r.getName().equalsIgnoreCase(name));
    }

    private String classListMessage() {
        StringBuilder sb = new StringBuilder("Choose a class: `roll-character <class> [name]`\n```\n");
        for (CharacterClass c : CharacterClass.values()) {
            String req = c.minimumScores().isEmpty()
                    ? ""
                    : "  requires " + c.minimumScores().entrySet().stream()
                            .map(e -> e.getKey().abbreviation() + " " + e.getValue() + "+")
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
            sb.append(String.format("%-11s d%-2d  prime: %s%s%n",
                    c.displayName(), c.hitDie(), c.primeRequisites().get(0).abbreviation(), req));
        }
        return sb.append("```").toString();
    }

    @Override
    public void provideHelp(HelpContext help) {
        help.addUsage("<class> [name] [bare]");
        help.addDescription("Rolls a new level-1 character of the given B/X class (Cleric, Fighter, "
                + "Magic-User, Thief, Dwarf, Elf, Halfling). The first roll starts your party; every "
                + "roll after that adds another PC (up to " + SaveGame.MAX_CHARACTERS + ") instead of "
                + "replacing anyone. By default the new PC is immediately best-effort auto-geared "
                + "(a class-appropriate weapon, armor, shield, and torches, spending as much of their "
                + "rolled gold as it can afford); add `bare` at the end to skip that and shop for "
                + "yourself instead with `buy`.");
    }
}
