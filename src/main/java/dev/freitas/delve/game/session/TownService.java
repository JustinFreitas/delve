package dev.freitas.delve.game.session;

import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The town interlude between delves: abandon the dungeon, roll survival for anyone who fled, rest the
 * party (1d3 hp/day, capped at max), pay retainer upkeep, and re-prepare spells. This is the B/X loop of
 * returning to safety to recover and re-supply before descending again.
 */
@Service
public class TownService {

    private static final int RETAINER_UPKEEP = 10; // gp per retainer for a week in town
    private static final int DEFAULT_REST_DAYS = 7;

    /** A hired helper who fled a dungeon has only a 3-in-6 chance of making it back to town at all. */
    private static final int FLEE_SURVIVAL_CHANCE = 3;

    private final SpellService spells;
    private final Dice dice;

    public TownService(SpellService spells, Dice dice) {
        this.spells = spells;
        this.dice = dice;
    }

    /** As {@link #returnToTown(SaveGame, int)}, resting the default (a full week). */
    public ExplorationResult returnToTown(SaveGame save) {
        return returnToTown(save, DEFAULT_REST_DAYS);
    }

    public ExplorationResult returnToTown(SaveGame save, int restDays) {
        Character c = save.getCharacter();
        ExplorationResult result = new ExplorationResult();

        boolean wasDelving = save.getSession().isInDungeon();
        save.getSession().setState(SessionState.IN_TOWN);
        save.getSession().setDungeon(null);
        save.getSession().setCombat(null);
        result.add(wasDelving
                ? "You make the long climb back to the surface and return to town."
                : "You spend some time in town.");

        // A retainer who fled a fight only has a 3-in-6 chance of actually making it back.
        List<Retainer> lost = new ArrayList<>();
        for (Retainer r : save.getRetainers()) {
            if (r.isFled()) {
                if (dice.d(6) <= FLEE_SURVIVAL_CHANCE) {
                    r.setFled(false);
                    result.add(r.getName() + " stumbles back into the fold, shaken but alive.");
                } else {
                    lost.add(r);
                    result.add(r.getName() + " never made it back — presumed dead.");
                }
            }
        }
        save.getRetainers().removeAll(lost);

        // Rest heals 1d3 hp per full day, capped at max — not an instant full heal.
        int days = Math.max(1, restDays);
        for (int i = 0; i < days; i++) {
            healOneDay(c);
            for (Retainer r : save.getRetainers()) {
                healOneDay(r);
            }
        }
        result.add("After " + days + " day" + (days == 1 ? "" : "s") + " of rest, " + c.getName()
                + " is at " + c.getCurrentHp() + "/" + c.getMaxHp() + " hp.");

        // Retainer upkeep.
        int upkeep = save.getRetainers().size() * RETAINER_UPKEEP;
        if (upkeep > 0) {
            int paid = Math.min(upkeep, c.getGold());
            c.setGold(c.getGold() - paid);
            result.add("You pay " + paid + " gp in retainer upkeep.");
            if (paid < upkeep) {
                // Unpaid retainers lose loyalty and the most disgruntled may quit.
                List<Retainer> quitters = new ArrayList<>();
                for (Retainer r : save.getRetainers()) {
                    r.setLoyalty(r.getLoyalty() - 1);
                    if (r.getLoyalty() <= 2) {
                        quitters.add(r);
                    }
                }
                save.getRetainers().removeAll(quitters);
                result.add("You couldn't cover everyone's wages — morale suffers"
                        + (quitters.isEmpty() ? "." : " and " + quitters.size() + " retainer(s) quit."));
            }
        }

        // Re-prepare the player's spells for the next delve. (Retainer spellcasting is summarized in
        // combat rather than tracked per slot, so they need no preparation step.)
        List<String> prepared = spells.autoPrepare(c);
        if (!prepared.isEmpty()) {
            result.add("You re-prepare spells: " + String.join(", ", prepared) + ".");
        }
        return result;
    }

    private void healOneDay(dev.freitas.delve.game.engine.Combatant c) {
        if (c.getCurrentHp() >= c.getMaxHp() || c.getCurrentHp() <= 0) {
            return;
        }
        c.setCurrentHp(Math.min(c.getMaxHp(), c.getCurrentHp() + dice.roll(1, 3)));
    }
}
