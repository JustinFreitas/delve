package dev.freitas.delve.command;

import dev.freitas.delve.game.CharacterFactory;
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
 * Rolls up a new level-1 B/X character: {@code /roll-character <class> [name]}. Abilities are rolled
 * 3d6 in order; if they fail the chosen class's minimum requirements the roll is rejected (re-run to
 * try again, as in B/X). The first roll starts a fresh party; every roll after that adds another PC to
 * the party (up to {@link SaveGame#MAX_CHARACTERS}) instead of replacing anyone.
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

        String name = tokens.length > 1 && !tokens[1].isBlank()
                ? tokens[1].trim()
                : ctx.getInvoker().getEffectiveName();

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

        Character character = characterFactory.createBare(name, characterClass, abilities);
        spells.autoPrepare(character); // a fresh caster is ready to cast (no-op for non-casters)

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

        String reply = firstPc
                ? "Rolled up **" + name + "**, a level 1 " + characterClass.displayName() + "! "
                        + "You start with **" + character.getGold() + " gp** and no gear — head to `"
                        + ctx.getPrefix() + "buy <item>` to kit up (weapon, armor, a shield, torches, and "
                        + "the rest) before your first delve."
                : "Rolled up **" + name + "**, a level 1 " + characterClass.displayName()
                        + ", and added them to the party (" + partySizeAfter + "/" + SaveGame.MAX_CHARACTERS
                        + "). They start with **" + character.getGold() + " gp** and no gear — gear them up "
                        + "before their first fight (note: `buy`/`wield` and a few other commands still "
                        + "act on your first-rolled PC only; naming a specific PC for those is coming soon).";
        ctx.reply(reply);
        ctx.replyEmbed(CharacterSheets.embed(character));
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
        help.addUsage("<class> [name]");
        help.addDescription("Rolls a new level-1 character of the given B/X class (Cleric, Fighter, "
                + "Magic-User, Thief, Dwarf, Elf, Halfling). The first roll starts your party; every "
                + "roll after that adds another PC (up to " + SaveGame.MAX_CHARACTERS + ") instead of "
                + "replacing anyone.");
    }
}
