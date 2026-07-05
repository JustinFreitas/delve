package dev.freitas.delve.game.session;

import dev.freitas.delve.game.Bestiary;
import dev.freitas.delve.game.engine.Advancement;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Hands;
import dev.freitas.delve.game.engine.Leveling;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.GameSession;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Headless autopilot that fast-forwards a character to a target level by simulating delves with the
 * real {@link ExplorationService}, {@link CombatService} and {@link TownService}. This is the
 * "earned" alternative to instant pregeneration: the character actually fights its way up (and can
 * die). Bounded by an episode cap so it always terminates.
 *
 * <p>Strategy: run a delve episode (explore, fight what's found, retreat to town to heal/re-supply
 * when battered or out of light), then repeat. Because returning to town generates a fresh dungeon
 * while XP persists, each episode supplies new monsters until the target level is reached.
 */
@Service
public class AutodelveService {

    private static final int MAX_EPISODES = 150;
    private static final int MAX_STEPS_PER_EPISODE = 80;
    private static final DamageRoll POTION = new DamageRoll(2, 4, 2);
    // A trapped treasure is worth retrying a few times (a non-Thief's flat disarm chance is low), but
    // capped so an unlucky streak doesn't burn the whole delve's step budget on one room.
    private static final int MAX_TREASURE_TRAP_ATTEMPTS = 3;

    private final Dice dice;
    private final ExplorationService exploration;
    private final CombatService combat;
    private final TownService town;

    public AutodelveService(
            Dice dice, ExplorationService exploration, CombatService combat, TownService town) {
        this.dice = dice;
        this.exploration = exploration;
        this.combat = combat;
        this.town = town;
    }

    /** Outcome of a simulated advancement run. */
    public enum Outcome {
        REACHED_TARGET,
        DIED,
        EXHAUSTED, // hit the episode cap without reaching the target
        SINGLE_DELVE // target was at or below the starting level: ran exactly one delve and survived
    }

    public record Result(Outcome outcome, int episodes, int startLevel, int finalLevel, int finalXp, List<String> log) {}

    /**
     * How quickly the character advances.
     *
     * <ul>
     *   <li>{@link #BX_OSE} — the default: pure by-the-book B/X OSE economy (monster XP + 1 XP per gp
     *       of recovered treasure). Authentic and slow; a run may end {@code EXHAUSTED}, to be resumed.
     *   <li>{@link #FAST} — a calibrated cadence of roughly one level every 3–4 survived delves at
     *       matched depth, on top of the organic XP. For quickly minting table-ready pregens.
     * </ul>
     */
    public enum Pace {
        BX_OSE,
        FAST
    }

    /**
     * How much of each delve gets written to {@link Result#log()}.
     *
     * <ul>
     *   <li>{@link #MILESTONES} — the default: curated significant events (encounters and their
     *       outcome, treasure recovered, level-ups, fleeing, etc.).
     *   <li>{@link #VERBOSE} — the finest resolution: every action's full narration, every turn (room
     *       descriptions, secret doors, traps, reactions, round-by-round combat text) — the same text a
     *       human player would see typing each command themselves.
     * </ul>
     */
    public enum LogDetail {
        MILESTONES,
        VERBOSE
    }

    /** Runs with the default B/X OSE pace and milestone-level logging. */
    public Result run(SaveGame save, int targetLevel) {
        return run(save, targetLevel, Pace.BX_OSE, LogDetail.MILESTONES);
    }

    /** Runs with milestone-level logging. */
    public Result run(SaveGame save, int targetLevel, Pace pace) {
        return run(save, targetLevel, pace, LogDetail.MILESTONES);
    }

    public Result run(SaveGame save, int targetLevel, Pace pace, LogDetail detail) {
        Character c = save.getCharacter();
        List<String> log = new ArrayList<>();
        int startLevel = c.getLevel();

        // A target at or below the current level means "just run one real delve" rather than grind
        // toward a level already reached — the natural default (see AutodelveCommand).
        boolean singleDelve = targetLevel <= startLevel;
        int episodeCap = singleDelve ? 1 : MAX_EPISODES;
        // In single-delve mode the delve must run its natural course (danger/health/darkness), not stop
        // the instant the (already-met) target-level check inside runEpisode fires.
        int episodeLevelCap = singleDelve ? Integer.MAX_VALUE : targetLevel;

        // Make sure we begin in town so each episode starts from a clean, supplied state.
        if (save.getSession().getState() != SessionState.IN_TOWN) {
            town.simulateReturnToTown(save);
        }

        int episode = 0;
        Outcome outcome = singleDelve ? Outcome.SINGLE_DELVE : Outcome.EXHAUSTED;
        while (episode < episodeCap) {
            if (!singleDelve && c.getLevel() >= targetLevel) {
                outcome = Outcome.REACHED_TARGET;
                break;
            }
            episode++;
            // Re-stock light before descending; the town rest already healed the party.
            town.simulateReturnToTown(save);
            if (c.getTorches() < 6) {
                c.setTorches(6);
            }
            // If nobody in the party (PC or retainers — e.g. an all-Fighter roster) has a free hand to
            // carry the torch, this delve would otherwise retreat in darkness on step one, forever,
            // with no visible cause. Simulate the sensible call a cautious player would make: the PC
            // drops their shield, then their off-hand weapon if still needed, rather than soft-locking
            // every future delve.
            boolean anyoneHasAFreeHand = Hands.free(c.getMainWeapon(), c.isShield(), false, c.getOffHandWeapon() != null) >= 1
                    || save.livingRetainers().stream()
                            .anyMatch(r -> Hands.free(r.getMainWeapon(), r.isShield(), false) >= 1);
            if (!anyoneHasAFreeHand && c.isShield()) {
                c.setShield(false);
                anyoneHasAFreeHand = Hands.free(c.getMainWeapon(), false, false, c.getOffHandWeapon() != null) >= 1;
            }
            if (!anyoneHasAFreeHand && c.getOffHandWeapon() != null) {
                c.setOffHandWeapon(null);
            }
            exploration.enter(save);

            int levelBefore = c.getLevel();
            List<String> milestones = new ArrayList<>();
            runEpisode(save, episodeLevelCap, milestones, detail);

            if (!c.isAlive()) {
                outcome = Outcome.DIED;
                milestones.add(c.getName() + " fell in the dungeon.");
            } else {
                if (pace == Pace.FAST && !singleDelve) {
                    awardDelveCompletion(c);
                }
                if (c.getLevel() > levelBefore) {
                    milestones.add("Reached level " + c.getLevel() + " (" + c.getXp() + " XP, " + c.getGold() + " gp).");
                }
                if (!singleDelve && c.getLevel() >= targetLevel) {
                    outcome = Outcome.REACHED_TARGET;
                }
            }

            log.add("Delve " + episode + ":");
            if (milestones.isEmpty()) {
                log.add("  - Nothing noteworthy.");
            } else {
                for (String milestone : milestones) {
                    log.add("  - " + milestone);
                }
            }
            if (!c.isAlive()) {
                break;
            }
        }

        // Always end safely in town.
        if (c.isAlive()) {
            town.simulateReturnToTown(save);
        }
        return new Result(outcome, episode, startLevel, c.getLevel(), c.getXp(), log);
    }

    /**
     * FAST pace: top up a survived delve so progress averages one level every 3–4 delves. The award
     * is a fraction of the current level band's XP cost, so the cadence holds at every level.
     */
    private void awardDelveCompletion(Character c) {
        int level = c.getLevel();
        int band = Advancement.xpForLevel(c.getCharacterClass(), level + 1)
                - Advancement.xpForLevel(c.getCharacterClass(), level);
        if (band <= 0) {
            return; // at class max level
        }
        int divisor = 3 + (dice.d(2) - 1); // 3 or 4 delves per level
        Leveling.awardXp(c, band / divisor, dice);
    }

    /** One delve: explore and fight until the level is exhausted, the character is badly hurt, or dead. */
    private void runEpisode(SaveGame save, int targetLevel, List<String> milestones, LogDetail detail) {
        Character c = save.getCharacter();
        GameSession session = save.getSession();
        Map<Integer, Integer> treasureTrapAttempts = new HashMap<>();

        for (int step = 0; step < MAX_STEPS_PER_EPISODE; step++) {
            if (!c.isAlive() || c.getLevel() >= targetLevel) {
                return;
            }
            if (session.getState() == SessionState.IN_COMBAT) {
                fightStep(save, milestones, detail);
                continue;
            }
            // Out of light or down to half health: end the episode and head back to town to recover.
            // Staying conservative keeps a fragile low-level character alive across many delves — this
            // also naturally bounds how many trapped-treasure retries can happen before backing off.
            if (session.isInDarkness() || c.getCurrentHp() * 2 <= c.getMaxHp()) {
                return;
            }
            Room room = session.currentRoom();
            if (room.hasLiveMonster()) {
                if (roomTooDangerous(room, c)) {
                    // Slip away from a deadly pack rather than picking the fight.
                    if (!exploreStep(save, milestones, detail)) {
                        return;
                    }
                } else {
                    boolean wandering = room.isFreshEncounter(); // checked before startCombat() consumes it
                    int count = room.getMonsterCount();
                    String name = room.getMonsterName().toLowerCase();
                    if (detail == LogDetail.MILESTONES) {
                        milestones.add(wandering
                                ? "A wandering " + count + " " + name + (count > 1 ? "s" : "") + " appears!"
                                : "Encountered " + count + " " + name + (count > 1 ? "s" : "") + ".");
                    }
                    // Engaging a beatable foe starts the fight.
                    logIfVerbose(milestones, detail, combat.attackRound(save, null, detail == LogDetail.VERBOSE));
                }
                continue;
            }
            if (needsSearching(room, treasureTrapAttempts)) {
                searchStep(save, milestones, treasureTrapAttempts, detail);
                continue;
            }
            if (!exploreStep(save, milestones, detail)) {
                return; // nowhere left to go on this level
            }
        }
    }

    /** In {@link LogDetail#VERBOSE}, appends every narrated line from an action (room descriptions,
        secret doors, traps, reactions, combat rounds — whatever the same command would show a human
        player); a no-op in {@link LogDetail#MILESTONES}. */
    private void logIfVerbose(List<String> milestones, LogDetail detail, ExplorationResult result) {
        if (detail != LogDetail.VERBOSE) {
            return;
        }
        for (String line : result.getLines()) {
            if (!line.isBlank()) {
                milestones.add(line);
            }
        }
    }

    /** Whether this room is still worth a `/search`: unsearched, or a trapped treasure worth one more
        disarm attempt (up to {@link #MAX_TREASURE_TRAP_ATTEMPTS}) — a room being merely "searched"
        doesn't mean an unresolved trapped treasure has been dealt with. */
    private boolean needsSearching(Room room, Map<Integer, Integer> treasureTrapAttempts) {
        if (!room.isSearched()) {
            return true;
        }
        if (!room.isHasTreasure() || room.isLooted() || !room.isTreasureTrapped() || room.isTreasureTrapDisarmed()) {
            return false;
        }
        return treasureTrapAttempts.getOrDefault(room.getId(), 0) < MAX_TREASURE_TRAP_ATTEMPTS;
    }

    /** Searches the current room, noting any treasure recovered (or a trapped treasure abandoned after
        repeated failed disarm attempts) as a milestone. */
    private void searchStep(
            SaveGame save, List<String> milestones, Map<Integer, Integer> treasureTrapAttempts, LogDetail detail) {
        Character c = save.getCharacter();
        Room room = save.getSession().currentRoom();
        boolean isTreasureTrapAttempt = room.isHasTreasure() && !room.isLooted()
                && room.isTreasureTrapped() && !room.isTreasureTrapDisarmed();

        int goldBefore = c.getGold();
        int potionsBefore = c.getHealingPotions();
        ExplorationResult result = exploration.search(save, detail == LogDetail.VERBOSE);
        logIfVerbose(milestones, detail, result);

        if (isTreasureTrapAttempt) {
            int attempts = treasureTrapAttempts.merge(room.getId(), 1, Integer::sum);
            boolean stillTrapped = room.isHasTreasure() && !room.isLooted() && !room.isTreasureTrapDisarmed();
            if (detail == LogDetail.MILESTONES && stillTrapped && attempts >= MAX_TREASURE_TRAP_ATTEMPTS) {
                milestones.add("Gave up on a trapped treasure after " + attempts + " failed disarm attempts.");
            }
        }

        if (detail == LogDetail.MILESTONES) {
            int goldFound = c.getGold() - goldBefore;
            if (goldFound > 0) {
                String potionNote = c.getHealingPotions() > potionsBefore ? " (plus a potion of healing)" : "";
                milestones.add("Recovered " + goldFound + " gp" + potionNote + ".");
            }
        }
    }

    private void fightStep(SaveGame save, List<String> milestones, LogDetail detail) {
        Character c = save.getCharacter();

        // Quaff a potion first if badly hurt and one is on hand.
        if (c.getCurrentHp() * 4 <= c.getMaxHp() && c.getHealingPotions() > 0) {
            c.setHealingPotions(c.getHealingPotions() - 1);
            c.setCurrentHp(Math.min(c.getMaxHp(), c.getCurrentHp() + POTION.roll(dice)));
            if (detail == LogDetail.MILESTONES) {
                milestones.add("Quaffed a healing potion at " + c.getCurrentHp() + "/" + c.getMaxHp() + " hp.");
            }
        }

        // Disengage from a fight we could lose: if the enemies' expected damage this round rivals our
        // remaining hit points, or we're already at half, flee rather than trade blows. A fragile
        // low-level delver survives far more delves this way (and the FAST pace rewards survival).
        String foe = currentFoeLabel(save);
        double incoming = expectedIncomingDamage(save);
        if (incoming >= c.getCurrentHp() || c.getCurrentHp() * 2 <= c.getMaxHp()) {
            ExplorationResult fleeResult = combat.flee(save);
            if (detail == LogDetail.MILESTONES) {
                milestones.add("Fled a losing fight with " + foe + ".");
            } else {
                logIfVerbose(milestones, detail, fleeResult);
            }
            return;
        }
        ExplorationResult roundResult = combat.attackRound(save, null, detail == LogDetail.VERBOSE);
        if (detail == LogDetail.MILESTONES) {
            if (save.getSession().getState() == SessionState.EXPLORING) {
                milestones.add("Defeated " + foe + ".");
            }
        } else {
            logIfVerbose(milestones, detail, roundResult);
        }
    }

    /** A short label for the current encounter's monster group, for the milestone log. */
    private String currentFoeLabel(SaveGame save) {
        var encounter = save.getSession().getCombat();
        if (encounter == null || encounter.getMonsterName() == null) {
            return "the enemy";
        }
        String name = encounter.getMonsterName().toLowerCase();
        return encounter.getInitialCount() > 1 ? (name + "s") : ("a " + name);
    }

    /** Whether an unfought monster room is too dangerous to engage — a round could cost most of our HP. */
    private boolean roomTooDangerous(Room room, Character c) {
        var type = Bestiary.byName(room.getMonsterName());
        if (type == null) {
            return false;
        }
        double incoming = type.attack().average() * Math.max(1, room.getMonsterCount());
        return incoming * 4 >= c.getCurrentHp() * 3; // avoid if ~75%+ of current HP could be lost in a round
    }

    /** Sum of the alive monsters' average per-round damage — a rough threat estimate. */
    private double expectedIncomingDamage(SaveGame save) {
        if (save.getSession().getCombat() == null) {
            return 0;
        }
        double total = 0;
        for (var monster : save.getSession().getCombat().aliveMonsters()) {
            total += monster.getType().attack().average();
        }
        return total;
    }

    /**
     * Moves toward an unexplored room, avoiding any destination holding a deadly pack. Returns false
     * when there is no safe move left (so the episode ends and the delver retreats to town).
     */
    private boolean exploreStep(SaveGame save, List<String> milestones, LogDetail detail) {
        GameSession session = save.getSession();
        Character c = save.getCharacter();
        Room room = session.currentRoom();

        // Only press deeper once strong enough; deeper levels are proportionally more dangerous.
        if (room.isStairsDown() && c.getLevel() >= session.getCurrentLevel() + 2 && dice.d(2) == 1) {
            ExplorationResult result = exploration.useStairs(save, true);
            if (detail == LogDetail.MILESTONES) {
                milestones.add("Descended to dungeon level " + (session.getCurrentLevel() + 1) + ".");
            } else {
                logIfVerbose(milestones, detail, result);
            }
            return true;
        }

        Exit safeUnvisited = null;
        Exit safeVisited = null;
        Exit closedToSafe = null;
        for (Exit exit : room.getExits().values()) {
            if (!exit.isKnown()) {
                continue;
            }
            Room dest = session.currentLevel().room(exit.getDestinationRoomId());
            boolean deadly = dest != null && dest.hasLiveMonster() && roomTooDangerous(dest, c);
            if (deadly) {
                continue; // never walk knowingly into a deadly room
            }
            if (exit.isPassable()) {
                if (dest != null && !dest.isVisited()) {
                    safeUnvisited = exit;
                } else if (safeVisited == null) {
                    safeVisited = exit;
                }
            } else if (closedToSafe == null) {
                closedToSafe = exit; // a shut door onto a safe (or unknown-but-not-deadly) room
            }
        }

        if (safeUnvisited != null) {
            logIfVerbose(milestones, detail, exploration.move(save, safeUnvisited.getDirection()));
            return true;
        }
        if (closedToSafe != null) {
            logIfVerbose(milestones, detail, exploration.open(save, closedToSafe.getDirection()));
            return true;
        }
        if (safeVisited != null) {
            logIfVerbose(milestones, detail, exploration.move(save, safeVisited.getDirection()));
            return true;
        }
        return false; // boxed in by danger — end the episode
    }
}
