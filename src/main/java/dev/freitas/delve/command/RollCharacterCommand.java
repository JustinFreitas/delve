package dev.freitas.delve.command;

import dev.freitas.delve.game.CharacterFactory;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.discord.Command;
import dev.freitas.delve.discord.CommandContext;
import dev.freitas.delve.discord.HelpContext;
import org.springframework.stereotype.Component;

/**
 * Rolls up a new level-1 B/X character: {@code /roll-character <class> [name]}. Abilities are rolled
 * 3d6 in order; if they fail the chosen class's minimum requirements the roll is rejected (re-run to
 * try again, as in B/X). The finished character is saved, replacing any previous one.
 */
@Component
public class RollCharacterCommand extends Command {

    private final Dice dice;
    private final CharacterFactory characterFactory;

    public RollCharacterCommand(Dice dice, CharacterFactory characterFactory) {
        super("roll-character", "rollchar", "newchar");
        this.dice = dice;
        this.characterFactory = characterFactory;
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

        Character character = characterFactory.create(name, characterClass, abilities);

        boolean replaced = ctx.getBeans().gameState.load(ctx.getInvokerUserId()).hasCharacter();
        ctx.getBeans().gameState.mutate(ctx.getInvokerUserId(), save -> save.setCharacter(character));

        ctx.reply("Rolled up **" + name + "**, a level 1 " + characterClass.displayName() + "!"
                + (replaced ? " _(your previous character was replaced)_" : ""));
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
                + "Magic-User, Thief, Dwarf, Elf, Halfling). Replaces your current character.");
    }
}
