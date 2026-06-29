package dev.freitas.delve.command;

import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.SavingThrows;
import dev.freitas.delve.game.model.Character;
import java.awt.Color;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Renders a {@link Character} as a Discord embed, shared by {@code /roll-character} and {@code /sheet}. */
final class CharacterSheets {

    private CharacterSheets() {}

    static MessageEmbed embed(Character c) {
        var cls = c.getCharacterClass();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(c.isAlive() ? new Color(0x8B5A2B) : Color.DARK_GRAY);
        eb.setTitle(c.getName() + " — Level " + c.getLevel() + " " + cls.displayName());

        eb.addField("Hit Points", c.getCurrentHp() + " / " + c.getMaxHp(), true);
        eb.addField("Armor Class", c.armorClass() + " (asc " + c.ascendingArmorClass() + ")", true);
        eb.addField("THAC0", String.valueOf(c.thac0()), true);

        eb.addField("Abilities", abilitiesBlock(c.getAbilities()), true);
        eb.addField("Saving Throws", savesBlock(c), true);
        eb.addField("XP", c.getXp() + " / " + c.xpForNextLevel(), true);

        eb.addField("Gold", c.getGold() + " gp", true);
        String armorLine = c.getArmor().displayName() + (c.isShield() ? " + shield" : "");
        eb.addField("Worn", armorLine, true);
        eb.addBlankField(true);

        if (!c.getInventory().isEmpty()) {
            eb.addField("Equipment", String.join(", ", c.getInventory()), false);
        }
        if (!c.getSpellbook().isEmpty()) {
            eb.addField("Spellbook", String.join(", ", c.getSpellbook()), false);
        }
        return eb.build();
    }

    private static String abilitiesBlock(AbilityScores a) {
        StringBuilder sb = new StringBuilder("```\n");
        for (Ability ability : Ability.values()) {
            int score = a.score(ability);
            int mod = a.modifier(ability);
            sb.append(String.format("%-4s %2d (%+d)%n", ability.abbreviation(), score, mod));
        }
        return sb.append("```").toString();
    }

    private static String savesBlock(Character c) {
        SavingThrows.Saves s = SavingThrows.forCharacter(c.getCharacterClass(), c.getLevel());
        return "```\n"
                + String.format("Death/Poison   %2d%n", s.deathPoison())
                + String.format("Wands          %2d%n", s.wands())
                + String.format("Paralysis      %2d%n", s.paralysisPetrify())
                + String.format("Breath         %2d%n", s.breath())
                + String.format("Spells         %2d%n", s.spells())
                + "```";
    }
}
