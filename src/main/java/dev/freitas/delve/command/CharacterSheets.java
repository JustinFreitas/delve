package dev.freitas.delve.command;

import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Encumbrance;
import dev.freitas.delve.game.engine.SavingThrows;
import dev.freitas.delve.game.engine.Spell;
import dev.freitas.delve.game.model.Character;
import java.awt.Color;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Renders a {@link Character} as a Discord embed, shared by {@code /roll-character} and {@code /sheet}. */
public final class CharacterSheets {

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
        eb.addField("Delves", String.valueOf(c.getDelveCount()), true);

        eb.addField("Gold", c.getGold() + " gp", true);
        String armorLine = c.getArmor().displayName() + (c.isShield() ? " + shield" : "");
        eb.addField("Worn", armorLine, true);
        boolean heavy = Encumbrance.heavyLoad(c.getGold());
        eb.addField("Movement",
                Encumbrance.movementRate(c.getArmor(), heavy) + "'/turn (" + Encumbrance.descriptor(c.getArmor(), heavy) + ")",
                true);

        StringBuilder supplies = new StringBuilder();
        supplies.append("Weapon: ").append(c.getMainWeapon()).append(" (").append(c.getMainWeaponDamage()).append(")");
        supplies.append("\nTorches: ").append(c.getTorches());
        if (c.getLanterns() > 0 || c.getOilFlasks() > 0) {
            supplies.append("   Lanterns: ").append(c.getLanterns()).append("   Oil flasks: ").append(c.getOilFlasks());
        }
        if (c.getHealingPotions() > 0) {
            supplies.append("\nHealing potions: ").append(c.getHealingPotions());
        }
        eb.addField("Supplies", supplies.toString(), false);

        if (!c.getInventory().isEmpty()) {
            eb.addField("Equipment", String.join(", ", c.getInventory()), false);
        }
        if (!c.getMemorizedSpells().isEmpty()) {
            eb.addField("Prepared spells", spellNames(c), true);
        }
        if (!c.getSpellbook().isEmpty()) {
            eb.addField("Spellbook", String.join(", ", c.getSpellbook()), false);
        }
        return eb.build();
    }

    /** A paste-ready monospace stat block, wrapped in a Discord code fence. Reused by the web export. */
    public static String textBlock(Character c) {
        AbilityScores a = c.getAbilities();
        SavingThrows.Saves s = SavingThrows.forCharacter(c.getCharacterClass(), c.getLevel());
        boolean heavy = Encumbrance.heavyLoad(c.getGold());
        StringBuilder sb = new StringBuilder("```\n");
        sb.append(c.getName()).append(" — Level ").append(c.getLevel()).append(" ")
                .append(c.getCharacterClass().displayName()).append("\n");
        sb.append(String.format("STR %2d (%+d)   INT %2d (%+d)   WIS %2d (%+d)%n",
                a.score(Ability.STR), a.modifier(Ability.STR),
                a.score(Ability.INT), a.modifier(Ability.INT),
                a.score(Ability.WIS), a.modifier(Ability.WIS)));
        sb.append(String.format("DEX %2d (%+d)   CON %2d (%+d)   CHA %2d (%+d)%n",
                a.score(Ability.DEX), a.modifier(Ability.DEX),
                a.score(Ability.CON), a.modifier(Ability.CON),
                a.score(Ability.CHA), a.modifier(Ability.CHA)));
        sb.append(String.format("HP %d   AC %d (asc %d)   THAC0 %d%n",
                c.getMaxHp(), c.armorClass(), c.ascendingArmorClass(), c.thac0()));
        sb.append(String.format("Saves  D/P %d  Wands %d  Para %d  Breath %d  Spells %d%n",
                s.deathPoison(), s.wands(), s.paralysisPetrify(), s.breath(), s.spells()));
        sb.append("Weapon: ").append(c.getMainWeapon()).append(" (").append(c.getMainWeaponDamage()).append(")\n");
        sb.append("Armor: ").append(c.getArmor().displayName()).append(c.isShield() ? " + shield" : "")
                .append("   Move ").append(Encumbrance.movementRate(c.getArmor(), heavy)).append("'/turn\n");
        sb.append("Delves: ").append(c.getDelveCount()).append("\n");
        sb.append("Gold: ").append(c.getGold()).append(" gp   Torches: ").append(c.getTorches());
        if (c.getLanterns() > 0 || c.getOilFlasks() > 0) {
            sb.append("   Lanterns: ").append(c.getLanterns()).append("   Oil flasks: ").append(c.getOilFlasks());
        }
        if (c.getHealingPotions() > 0) {
            sb.append("   Healing potions: ").append(c.getHealingPotions());
        }
        sb.append("\n");
        if (!c.getInventory().isEmpty()) {
            sb.append("Equipment: ").append(String.join(", ", c.getInventory())).append("\n");
        }
        if (!c.getMemorizedSpells().isEmpty()) {
            sb.append("Prepared: ").append(spellNames(c)).append("\n");
        }
        if (!c.getSpellbook().isEmpty()) {
            sb.append("Spellbook: ").append(String.join(", ", c.getSpellbook())).append("\n");
        }
        return sb.append("```").toString();
    }

    private static String spellNames(Character c) {
        return c.getMemorizedSpells().stream()
                .map(Spell::valueOf)
                .map(Spell::displayName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("—");
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
