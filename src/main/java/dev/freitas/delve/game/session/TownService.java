package dev.freitas.delve.game.session;

import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The town interlude between delves: abandon the dungeon, heal the party to full over a week's rest,
 * pay retainer upkeep, and re-prepare spells. This is the B/X loop of returning to safety to recover
 * and re-supply before descending again.
 */
@Service
public class TownService {

    private static final int RETAINER_UPKEEP = 10; // gp per retainer for a week in town

    private final SpellService spells;

    public TownService(SpellService spells) {
        this.spells = spells;
    }

    public ExplorationResult returnToTown(SaveGame save) {
        Character c = save.getCharacter();
        ExplorationResult result = new ExplorationResult();

        boolean wasDelving = save.getSession().isInDungeon();
        save.getSession().setState(SessionState.IN_TOWN);
        save.getSession().setDungeon(null);
        save.getSession().setCombat(null);
        result.add(wasDelving
                ? "You make the long climb back to the surface and return to town."
                : "You spend a quiet week in town.");

        // Full rest heals the whole party.
        c.setCurrentHp(c.getMaxHp());
        for (Retainer r : save.getRetainers()) {
            r.setCurrentHp(r.getMaxHp());
            r.setFled(false);
        }
        result.add("The party rests and recovers to full health.");

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
}
