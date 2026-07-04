package dev.freitas.delve.command;

import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.Formation;
import dev.freitas.delve.game.engine.Hands;
import dev.freitas.delve.game.engine.RetainerRules;
import dev.freitas.delve.game.engine.WeaponCatalog;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import java.util.List;

/** Renders the party listing (character + retainers, rank/hands/light status) shared by {@code /party}
    and {@code /autodelve}'s post-run summary. */
public final class PartySummary {

    private PartySummary() {}

    public static String text(SaveGame save) {
        Character pc = save.getCharacter();
        int max = RetainerRules.maxRetainers(pc.getAbilities().score(Ability.CHA));
        int width = save.getSession().isInDungeon() ? save.getSession().currentRoom().getCorridorWidth() : 3;
        List<Combatant> fullOrder = save.fullOrder();

        String bearerToken = save.getSession().getLightBearer();

        StringBuilder sb = new StringBuilder("**Your party**\n```\n");
        sb.append(String.format("%-16s L%-2d %-11s %3d/%-3d hp  AC %d  %s%n",
                pc.getName() + " (you)", pc.getLevel(), pc.getCharacterClass().displayName(),
                pc.getCurrentHp(), pc.getMaxHp(), pc.armorClass(),
                rankSummary(fullOrder, width, pc, SaveGame.PLAYER_SLOT.equals(bearerToken), save)));
        for (Retainer r : save.getRetainers()) {
            sb.append(String.format("%-16s L%-2d %-11s %3d/%-3d hp  AC %d  loyalty %d (%s)  %s%n",
                    r.getName(), r.getLevel(), r.getCharacterClass().displayName(),
                    r.getCurrentHp(), r.getMaxHp(), r.armorClass(),
                    r.getLoyalty(), RetainerRules.loyaltyDescriptor(r.getLoyalty()),
                    rankSummary(fullOrder, width, r, bearerToken != null && bearerToken.equalsIgnoreCase(r.getName()), save)));
        }
        sb.append("```");
        sb.append("Retainers: ").append(save.getRetainers().size()).append("/").append(max)
                .append(" (Charisma cap).");
        return sb.toString();
    }

    /** Nominal rank, engagement status, weapon-class glyph, and hand status for one party member. */
    private static String rankSummary(List<Combatant> fullOrder, int width, Combatant who, boolean isBearer, SaveGame save) {
        int rank = Formation.nominalRank(fullOrder, width, who);
        if (rank < 0) {
            return "";
        }
        boolean engaged = Formation.isEngaged(fullOrder, width, who);
        String glyph = switch (WeaponCatalog.classify(who.getMainWeapon()).weaponClass()) {
            case MELEE -> "melee";
            case REACH -> "reach";
            case MISSILE -> "missile";
        };
        String secondary = "";
        if (who instanceof Retainer r && r.getSecondaryWeapon() != null) {
            secondary = " (" + r.getSecondaryWeapon().toLowerCase() + " ready)";
        }
        boolean shield = who instanceof Character c ? c.isShield() : ((Retainer) who).isShield();
        int handsFree = Hands.free(who.getMainWeapon(), shield, isBearer);
        String bearing = isBearer && save.getSession().getActiveLight() != null
                ? " (bearing " + save.getSession().getActiveLight().displayName() + ")" : "";
        return "rank " + rank + (engaged ? " (engaged)" : "") + ", " + glyph + secondary
                + ", " + handsFree + "/2 hands free" + bearing;
    }
}
