package dev.freitas.delve.game.session;

import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.GameClock;
import dev.freitas.delve.game.engine.LodgingTier;
import dev.freitas.delve.game.engine.MuleRules;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Mule;
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
 *
 * <p>A <em>real</em> town visit ({@link #returnToTown}) only heals for as many days as have actually
 * passed in real wall-clock time since the party's last real visit ({@link GameClock},
 * {@link SaveGame#getLastTownVisitMillis()}) — typing a large day count doesn't buy free healing.
 * {@link #simulateReturnToTown} is the escape hatch for {@code AutodelveService}'s compressed
 * multi-delve runs, which fictionally represent time passing between episodes without the player
 * actually waiting; it always grants a full rest and never reads or writes the real-time clock.
 */
@Service
public class TownService {

    private static final int RETAINER_UPKEEP = 10; // gp per retainer for a week in town
    private static final int DEFAULT_REST_DAYS = 7;
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    /** A hired helper who fled a dungeon has only a 3-in-6 chance of making it back to town at all. */
    private static final int FLEE_SURVIVAL_CHANCE = 3;

    private final SpellService spells;
    private final Dice dice;
    private final GameClock clock;

    public TownService(SpellService spells, Dice dice, GameClock clock) {
        this.spells = spells;
        this.dice = dice;
        this.clock = clock;
    }

    /** As {@link #returnToTown(SaveGame, int)}, requesting the default (a full week). */
    public ExplorationResult returnToTown(SaveGame save) {
        return returnToTown(save, DEFAULT_REST_DAYS);
    }

    /** As {@link #returnToTown(SaveGame, int, LodgingTier)}, defaulting to {@link LodgingTier#ROOM} —
        "Standard for PCs," no consequence beyond its gold cost. */
    public ExplorationResult returnToTown(SaveGame save, int requestedDays) {
        return returnToTown(save, requestedDays, LodgingTier.ROOM);
    }

    /** A real town visit: rests at most {@code requestedDays}, further capped by however many real days
        have actually passed since {@link SaveGame#getLastTownVisitMillis()} (null — no real visit has
        ever resolved — is treated as "now," i.e. no backlog, same dynamic-fallback spirit as
        {@link SaveGame#ownerOf}). Advances that clock by the days actually rested, banking any leftover
        real time toward a future visit rather than discarding it. {@code tier} is the whole party's
        chosen lodging for the stay — see the per-tier consequences in {@link #rest}. */
    public ExplorationResult returnToTown(SaveGame save, int requestedDays, LodgingTier tier) {
        long now = clock.nowMillis();
        Long lastVisit = save.getLastTownVisitMillis();
        long baseline = lastVisit != null ? lastVisit : now;
        int availableDays = (int) Math.max(0, (now - baseline) / DAY_MILLIS);
        int days = Math.max(0, Math.min(requestedDays, availableDays));

        String timeNote = null;
        if (days < requestedDays) {
            timeNote = days == 0
                    ? "No real time has passed since you last visited town — resting won't help yet."
                    : "Only " + days + " day" + (days == 1 ? "" : "s") + " have passed in real time "
                            + "since your last visit; the rest is cut short.";
        }
        ExplorationResult result = rest(save, days, timeNote, tier);
        save.setLastTownVisitMillis(baseline + days * DAY_MILLIS);
        return result;
    }

    /** {@code AutodelveService}'s compressed multi-delve runs call {@link #returnToTown} many times in
        one real-time instant; gating each of those on real elapsed time would leave every delve after
        the first un-healed. This always grants a full rest instead, and never touches the real-time
        clock ({@link SaveGame#getLastTownVisitMillis()}) — a later real {@link #returnToTown} is judged
        purely against the player's own last real visit, unaffected by any simulated runs in between.
        Always {@link LodgingTier#ROOM} — a simulated multi-delve grind has no lodging choice to make.
        Package-private, not public: this is an internal escape hatch for {@code game.session}'s own
        simulation code, not a general-purpose alternative to the real, time-gated {@link #returnToTown}
        — the command/API layers live in other packages and so cannot reach it, which is deliberate:
        it structurally rules out a future command or web endpoint calling the wrong one by mistake. */
    ExplorationResult simulateReturnToTown(SaveGame save) {
        return rest(save, DEFAULT_REST_DAYS, null, LodgingTier.ROOM);
    }

    private ExplorationResult rest(SaveGame save, int days, String timeNote, LodgingTier tier) {
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

        if (timeNote != null) {
            result.add(timeNote);
        }

        // Rest heals 1d3 hp per full day, capped at max — not an instant full heal.
        boolean solo = save.getCharacters().size() == 1;
        for (int i = 0; i < days; i++) {
            for (Character pc : save.getCharacters()) {
                healOneDay(pc);
            }
            for (Retainer r : save.getRetainers()) {
                healOneDay(r);
            }
        }
        if (days > 0) {
            String dayLabel = "After " + days + " day" + (days == 1 ? "" : "s") + " of rest";
            if (solo) {
                result.add(dayLabel + ", " + c.getName() + " is at " + c.getCurrentHp() + "/" + c.getMaxHp() + " hp.");
            } else {
                List<String> hpParts = new ArrayList<>();
                for (Character pc : save.getCharacters()) {
                    hpParts.add(pc.getName() + " " + pc.getCurrentHp() + "/" + pc.getMaxHp() + " hp");
                }
                result.add(dayLabel + ": " + String.join(", ", hpParts) + ".");
            }
        }

        // Retainer upkeep — each PC pays for their own retainers, out of their own gold. Flat per visit,
        // not scaled by days rested (a quick supply run costs the same as a long stay) -- but skipped
        // entirely at days=0 (the real-time clamp found no real time has passed): that's not a new visit,
        // just the same one being asked about again, and shouldn't bill a second time for zero benefit.
        List<Retainer> quitters = new ArrayList<>();
        if (days > 0) {
            for (Character pc : save.getCharacters()) {
                List<Retainer> owned = save.retainersOwnedBy(pc);
                if (owned.isEmpty()) {
                    continue;
                }
                int upkeep = owned.size() * RETAINER_UPKEEP;
                int paid = Math.min(upkeep, pc.getGold());
                pc.setGold(pc.getGold() - paid);
                result.add((solo ? "You pay " : pc.getName() + " pays ") + paid + " gp in retainer upkeep"
                        + (solo ? "" : " for " + owned.size() + " retainer" + (owned.size() == 1 ? "" : "s")) + ".");
                if (paid < upkeep) {
                    // Unpaid retainers lose loyalty and the most disgruntled may quit.
                    List<Retainer> pcQuitters = new ArrayList<>();
                    for (Retainer r : owned) {
                        r.setLoyalty(r.getLoyalty() - 1);
                        if (r.getLoyalty() <= 2) {
                            pcQuitters.add(r);
                        }
                    }
                    quitters.addAll(pcQuitters);
                    result.add((solo ? "You" : pc.getName()) + " couldn't cover everyone's wages — morale suffers"
                            + (pcQuitters.isEmpty() ? "." : " and " + pcQuitters.size() + " retainer(s) quit."));
                }
            }
        }
        save.getRetainers().removeAll(quitters);

        // Inn-tier lodging — each PC pays for their own stay, scaled by days actually rested (unlike
        // the flat-per-visit upkeep above, lodging really is a per-night cost). The cheaper tiers carry
        // a real, tier-specific consequence for the PC's *own* retainers, beyond the gold: gygax75-rules
        // frames this as "where is the Boss staying," not a per-retainer choice.
        List<Retainer> lodgingDeserters = new ArrayList<>();
        if (days > 0) {
            for (Character pc : save.getCharacters()) {
                int lodgingCost = tier.gpPerDay() * days;
                int paidLodging = Math.min(lodgingCost, pc.getGold());
                pc.setGold(pc.getGold() - paidLodging);
                result.add((solo ? "You pay " : pc.getName() + " pays ") + paidLodging + " gp for a "
                        + tier.displayName().toLowerCase() + (days == 1 ? "" : " (" + days + " nights)") + ".");

                List<Retainer> owned = save.retainersOwnedBy(pc);
                if (owned.isEmpty()) {
                    continue;
                }
                if (tier == LodgingTier.DORMITORY) {
                    lodgingDeserters.addAll(owned);
                    result.add((solo ? "Your" : pc.getName() + "'s") + " retainer" + (owned.size() == 1 ? "" : "s")
                            + " won't wait in a flophouse while " + (solo ? "you're" : pc.getName() + " is")
                            + " away — " + (owned.size() == 1 ? "they quit" : "they all quit") + ".");
                } else if (tier == LodgingTier.SHARED_ROOM) {
                    int weeks = days / 7;
                    for (int week = 0; week < weeks; week++) {
                        for (Retainer r : owned) {
                            if (!lodgingDeserters.contains(r) && dice.roll2d6() > r.getLoyalty()) {
                                lodgingDeserters.add(r);
                                result.add(r.getName() + " decides " + (solo ? "you're" : pc.getName() + " is")
                                        + " no fit boss to share a room with, and leaves for good.");
                            }
                        }
                    }
                }
            }
        }
        save.getRetainers().removeAll(lodgingDeserters);

        // Mule stabling/fodder, same flat-per-visit idiom as retainer upkeep above — paid by its owner.
        // A mule has no loyalty to lose, so an unpaid stay just runs up a reported shortfall rather than
        // any desertion equivalent.
        if (days > 0) {
            for (Mule mule : save.getMules()) {
                Character owner = save.ownerOf(mule);
                int paid = Math.min(MuleRules.UPKEEP_GP_PER_WEEK, owner.getGold());
                owner.setGold(owner.getGold() - paid);
                String payerLabel = solo ? "You pay " : owner.getName() + " pays ";
                result.add(payerLabel + paid + " gp stabling " + mule.getName()
                        + (paid < MuleRules.UPKEEP_GP_PER_WEEK ? " — short of the full fee." : "."));
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
