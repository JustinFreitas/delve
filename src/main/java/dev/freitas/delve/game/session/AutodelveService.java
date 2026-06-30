package dev.freitas.delve.game.session;

import dev.freitas.delve.game.Bestiary;
import dev.freitas.delve.game.engine.Advancement;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Leveling;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.GameSession;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import java.util.ArrayList;
import java.util.List;
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
        EXHAUSTED // hit the episode cap without reaching the target
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

    /** Runs with the default B/X OSE pace. */
    public Result run(SaveGame save, int targetLevel) {
        return run(save, targetLevel, Pace.BX_OSE);
    }

    public Result run(SaveGame save, int targetLevel, Pace pace) {
        Character c = save.getCharacter();
        List<String> log = new ArrayList<>();
        int startLevel = c.getLevel();

        // Make sure we begin in town so each episode starts from a clean, supplied state.
        if (save.getSession().getState() != SessionState.IN_TOWN) {
            town.returnToTown(save);
        }

        int episode = 0;
        Outcome outcome = Outcome.EXHAUSTED;
        while (episode < MAX_EPISODES) {
            if (c.getLevel() >= targetLevel) {
                outcome = Outcome.REACHED_TARGET;
                break;
            }
            episode++;
            // Re-stock light before descending; the town rest already healed the party.
            town.returnToTown(save);
            if (c.getTorches() < 6) {
                c.setTorches(6);
            }
            exploration.enter(save);

            int levelBefore = c.getLevel();
            runEpisode(save, targetLevel);

            if (!c.isAlive()) {
                outcome = Outcome.DIED;
                log.add("Delve " + episode + ": " + c.getName() + " fell in the dungeon.");
                break;
            }
            if (pace == Pace.FAST) {
                awardDelveCompletion(c);
            }
            if (c.getLevel() > levelBefore) {
                log.add("Delve " + episode + ": reached level " + c.getLevel()
                        + " (" + c.getXp() + " XP, " + c.getGold() + " gp).");
            }
            if (c.getLevel() >= targetLevel) {
                outcome = Outcome.REACHED_TARGET;
                break;
            }
        }

        // Always end safely in town.
        if (c.isAlive()) {
            town.returnToTown(save);
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
    private void runEpisode(SaveGame save, int targetLevel) {
        Character c = save.getCharacter();
        GameSession session = save.getSession();

        for (int step = 0; step < MAX_STEPS_PER_EPISODE; step++) {
            if (!c.isAlive() || c.getLevel() >= targetLevel) {
                return;
            }
            if (session.getState() == SessionState.IN_COMBAT) {
                fightStep(save);
                continue;
            }
            // Out of light or down to half health: end the episode and head back to town to recover.
            // Staying conservative keeps a fragile low-level character alive across many delves.
            if (session.isInDarkness() || c.getCurrentHp() * 2 <= c.getMaxHp()) {
                return;
            }
            Room room = session.currentRoom();
            if (room.hasLiveMonster()) {
                if (roomTooDangerous(room, c)) {
                    // Slip away from a deadly pack rather than picking the fight.
                    if (!exploreStep(save)) {
                        return;
                    }
                } else {
                    combat.attackRound(save, null); // engaging a beatable foe starts the fight
                }
                continue;
            }
            if (!room.isSearched()) {
                exploration.search(save);
                continue;
            }
            if (!exploreStep(save)) {
                return; // nowhere left to go on this level
            }
        }
    }

    private void fightStep(SaveGame save) {
        Character c = save.getCharacter();

        // Quaff a potion first if badly hurt and one is on hand.
        if (c.getCurrentHp() * 4 <= c.getMaxHp() && c.getHealingPotions() > 0) {
            c.setHealingPotions(c.getHealingPotions() - 1);
            c.setCurrentHp(Math.min(c.getMaxHp(), c.getCurrentHp() + POTION.roll(dice)));
        }

        // Disengage from a fight we could lose: if the enemies' expected damage this round rivals our
        // remaining hit points, or we're already at half, flee rather than trade blows. A fragile
        // low-level delver survives far more delves this way (and the FAST pace rewards survival).
        double incoming = expectedIncomingDamage(save);
        if (incoming >= c.getCurrentHp() || c.getCurrentHp() * 2 <= c.getMaxHp()) {
            combat.flee(save);
            return;
        }
        combat.attackRound(save, null);
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
    private boolean exploreStep(SaveGame save) {
        GameSession session = save.getSession();
        Character c = save.getCharacter();
        Room room = session.currentRoom();

        // Only press deeper once strong enough; deeper levels are proportionally more dangerous.
        if (room.isStairsDown() && c.getLevel() >= session.getCurrentLevel() + 2 && dice.d(2) == 1) {
            exploration.useStairs(save, true);
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
            exploration.move(save, safeUnvisited.getDirection());
            return true;
        }
        if (closedToSafe != null) {
            exploration.open(save, closedToSafe.getDirection());
            return true;
        }
        if (safeVisited != null) {
            exploration.move(save, safeVisited.getDirection());
            return true;
        }
        return false; // boxed in by danger — end the episode
    }
}
