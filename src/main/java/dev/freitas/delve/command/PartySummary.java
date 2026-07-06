package dev.freitas.delve.command;

import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.Formation;
import dev.freitas.delve.game.engine.Hands;
import dev.freitas.delve.game.engine.MuleRules;
import dev.freitas.delve.game.engine.RetainerRules;
import dev.freitas.delve.game.engine.WeaponCatalog;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Mule;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import java.util.List;

/** Renders the party listing (character + retainers, rank/hands/light status) shared by {@code /party}
    and {@code /autodelve}'s post-run summary. */
public final class PartySummary {

    private PartySummary() {}

    public static String text(SaveGame save) {
        // Each PC's Charisma cap is enforced independently (see SaveGame#atRetainerCap) -- every PC can
        // fill their own cap regardless of what anyone else in the party has hired -- so the party's real
        // reachable ceiling is the SUM of every PC's own cap, not just the highest single one.
        int max = save.getCharacters().stream()
                .mapToInt(save::retainerCapFor)
                .sum();
        int width = save.getSession().isInDungeon() ? save.getSession().currentRoom().getCorridorWidth() : 3;
        List<Combatant> fullOrder = save.fullOrder();
        boolean solo = save.getCharacters().size() == 1;

        String bearerToken = save.getSession().getLightBearer();

        StringBuilder sb = new StringBuilder("**Your party**\n```\n");
        for (Character pc : save.getCharacters()) {
            boolean isBearer = bearerToken != null && bearerToken.equalsIgnoreCase(save.tokenFor(pc));
            String label = solo ? pc.getName() + " (you)" : pc.getName();
            // In a multi-PC party, each PC's own Charisma can authorize more hires (see HireCommand's
            // optional pc-name argument) — surface how many more THIS PC could bring on right now, since
            // it's otherwise hard to tell who to name in `/hire`.
            String openSlots = "";
            if (!solo) {
                int open = Math.max(0, save.retainerCapFor(pc) - save.retainersOwnedBy(pc).size());
                openSlots = ", " + open + " open slot" + (open == 1 ? "" : "s");
            }
            sb.append(String.format("%-16s L%-2d %-11s %3d/%-3d hp  AC %d  %s%s%n",
                    label, pc.getLevel(), pc.getCharacterClass().displayName(),
                    pc.getCurrentHp(), pc.getMaxHp(), pc.armorClass(),
                    rankSummary(fullOrder, width, pc, isBearer, save), openSlots));
            // Each PC's own retainers are grouped right under their row, rather than one flat trailing
            // list, now that retainers have a real owner.
            String indent = solo ? "" : "  ";
            for (Retainer r : save.retainersOwnedBy(pc)) {
                sb.append(indent).append(String.format("%-16s L%-2d %-11s %3d/%-3d hp  AC %d  loyalty %d (%s)  %s%n",
                        r.getName(), r.getLevel(), r.getCharacterClass().displayName(),
                        r.getCurrentHp(), r.getMaxHp(), r.armorClass(),
                        r.getLoyalty(), RetainerRules.loyaltyDescriptor(r.getLoyalty()),
                        rankSummary(fullOrder, width, r, bearerToken != null && bearerToken.equalsIgnoreCase(r.getName()), save)));
            }
        }
        sb.append("```");
        sb.append("Retainers: ").append(save.getRetainers().size()).append("/").append(max)
                .append(" (Charisma cap).");
        Mule mule = save.getMule();
        if (mule != null) {
            sb.append("\n").append(muleLine(mule, fullOrder, width));
        }
        return sb.toString();
    }

    private static String muleLine(Mule mule, List<Combatant> fullOrder, int width) {
        int rank = Formation.nominalRank(fullOrder, width, mule);
        String rankPart = rank < 0 ? "" : "rank " + rank
                + (Formation.isEngaged(fullOrder, width, mule) ? " (engaged)" : "") + "  ";
        String handler = mule.getHandler() == null ? "no handler" : "handler " + mule.getHandler();
        return "Mule: **" + mule.getName() + "** (" + handler + ") — " + rankPart + "AC " + mule.armorClass()
                + "  " + mule.getCurrentHp() + "/" + mule.getMaxHp() + " hp — " + mule.getCarriedGold() + "/"
                + MuleRules.CAPACITY_MAX_CNS + " gp, " + MuleRules.descriptor(mule.getCarriedGold())
                + " (" + MuleRules.movementRate(mule.getCarriedGold()) + "'/turn).";
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
        Character pc = who instanceof Character c ? c : null;
        boolean offHandEquipped = pc != null && pc.offHandDescription() != null;
        if (offHandEquipped) {
            secondary = pc.offHandDescription();
        }
        boolean shield = pc != null ? pc.isShield() : ((Retainer) who).isShield();
        int handsFree = Hands.free(who.getMainWeapon(), shield, isBearer, offHandEquipped);
        String bearing = isBearer && save.getSession().getActiveLight() != null
                ? " (bearing " + save.getSession().getActiveLight().displayName() + ")" : "";
        return "rank " + rank + (engaged ? " (engaged)" : "") + ", " + glyph + secondary
                + ", " + handsFree + "/2 hands free" + bearing;
    }
}
