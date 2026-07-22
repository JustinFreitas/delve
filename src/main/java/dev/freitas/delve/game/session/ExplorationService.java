package dev.freitas.delve.game.session;

import dev.freitas.delve.game.Bestiary;
import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.Advanceable;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Formation;
import dev.freitas.delve.game.engine.Leveling;
import dev.freitas.delve.game.engine.LightSource;
import dev.freitas.delve.game.engine.ReactionTier;
import dev.freitas.delve.game.engine.SavingThrows;
import dev.freitas.delve.game.engine.ThiefSkills;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.ContentType;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.ActiveEffect;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.GameSession;
import dev.freitas.delve.game.model.MonsterDisposition;
import dev.freitas.delve.game.model.MonsterType;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The exploration rules: starting a delve, looking, moving, searching, opening doors, and descending
 * stairs — together with the B/X bookkeeping each action triggers (10-minute dungeon turns, torch
 * burn, and a wandering-monster check every other turn). A hostile encounter hands off to
 * {@link CombatService#startCombat}; every other action (everything but {@code look}) is refused
 * while that fight is live — see {@link #requireNotInCombat}.
 */
@Service
public class ExplorationService {

    private static final int LEVELS = 3;
    private static final int ROOMS_PER_LEVEL = 10;

    /** Proposed default: a non-Thief's flat chance to disarm a treasure trap. */
    private static final int NON_THIEF_REMOVE_TRAPS_CHANCE = 5;

    /** Proposed default: passive trap sense (pole or Dwarf racial bonus) while walking, not searching. */
    private static final int PASSIVE_TRAP_SENSE_CHANCE = 1;

    private final Dice dice;
    private final DungeonGenerator generator;
    private final CombatService combat;
    private final LightingService lighting;
    private final MuleService mules;

    public ExplorationService(
            Dice dice, DungeonGenerator generator, CombatService combat, LightingService lighting, MuleService mules) {
        this.dice = dice;
        this.generator = generator;
        this.combat = combat;
        this.lighting = lighting;
        this.mules = mules;
    }

    /** Begins a fresh procedurally-generated dungeon run for the character, lighting the first torch. */
    public ExplorationResult enter(SaveGame save) {
        Dungeon dungeon = generator.generate(LEVELS, ROOMS_PER_LEVEL);
        return beginDelve(save, dungeon, "You light a torch and descend into the dungeon...");
    }

    /** Begins a run through a pre-built (authored module) dungeon. */
    public ExplorationResult enterModule(SaveGame save, Dungeon dungeon, String title) {
        String intro = title == null || title.isBlank()
                ? "You light a torch and step into the adventure..."
                : "You light a torch and enter **" + title + "**...";
        return beginDelve(save, dungeon, intro);
    }

    /** Shared setup for any dungeon source: install it in the session and describe the entrance. */
    private ExplorationResult beginDelve(SaveGame save, Dungeon dungeon, String introLine) {
        Character character = save.getCharacter();
        GameSession session = save.getSession();

        character.setDelveCount(character.getDelveCount() + 1);
        session.setDungeon(dungeon);
        session.setCurrentLevel(0);
        session.setCurrentRoomId(dungeon.level(0).getEntranceRoomId());
        session.setDungeonTurn(0);
        session.setInDarkness(false);
        session.setState(SessionState.EXPLORING);
        session.setPolingFrontRank(false);
        session.setTurnsSinceRest(0);

        ExplorationResult result = new ExplorationResult();
        result.add(introLine);
        lighting.lightUp(save, LightSource.TORCH, result);
        session.currentRoom().setVisited(true);
        result.add("");
        result.add(describeRoom(session));
        return result;
    }

    /** Describes the current room (no time passes). */
    public ExplorationResult look(GameSession session) {
        return ExplorationResult.of(describeRoom(session));
    }

    /** Shared guard for every action that mustn't happen mid-fight (all of them except `look`):
        non-null failure while a combat is live, null otherwise. Leaving the fight is `flee`'s job —
        it alone applies parting blows and the evasion check. */
    private ExplorationResult requireNotInCombat(GameSession session) {
        if (session.getState() == SessionState.IN_COMBAT) {
            return ExplorationResult.failure("You are locked in combat — `attack` or `flee` first.");
        }
        return null;
    }

    /** Moves through a cardinal exit, advancing one dungeon turn. */
    public ExplorationResult move(SaveGame save, Direction direction) {
        GameSession session = save.getSession();
        ExplorationResult locked = requireNotInCombat(session);
        if (locked != null) {
            return locked;
        }
        Room room = session.currentRoom();
        Exit exit = room.getExits().get(direction);

        if (exit == null || !exit.isKnown()) {
            return ExplorationResult.failure("There is no exit to the " + direction.lower() + ".");
        }
        if (exit.getDoor() == DoorState.ONE_WAY) {
            return ExplorationResult.failure("The way " + direction.lower()
                    + " won't budge — this passage only opens from the other side.");
        }
        if (!exit.getDoor().isPassable()) {
            String doorName = exit.getDoor().name().toLowerCase();
            String article = doorName.startsWith("u") ? "an" : "a";
            return ExplorationResult.failure("The way " + direction.lower() + " is blocked by " + article
                    + " " + doorName + " door. Try `open " + direction.lower() + "`.");
        }

        ExplorationResult result = new ExplorationResult();
        result.add("You go " + direction.lower() + ".");

        // Corridor trap: checked/sprung for the passage itself, while still logically "in" it.
        maybePassiveCorridorTrapSense(save, exit, result);
        maybeSpringCorridorTrap(save, exit, result);

        session.setCurrentRoomId(exit.getDestinationRoomId());
        session.currentRoom().setVisited(true);

        // A door that had to be actively opened doesn't stay open on its own -- it always swings shut
        // once the party has passed through, unless spiked. Rests at UNLOCKED (not CLOSED) if it used
        // to be locked, so it's never mistaken for still needing a key.
        if (exit.getDoor() == DoorState.OPEN && !exit.isSpiked()) {
            setBothDoors(session, exit, exit.isEverLocked() ? DoorState.UNLOCKED : DoorState.CLOSED);
            result.add("The door swings shut behind you.");
        }

        advanceTurn(save, result, false);

        maybePassiveRoomTrapSense(save, result);
        maybeSpringTrap(save, result);
        maybePassiveSecretDoorSense(save, result);

        result.add("");
        result.add(describeRoom(session));
        handleEncounter(save, result);
        return result;
    }

    /** Descends or ascends stairs in the current room, advancing one dungeon turn. */
    public ExplorationResult useStairs(SaveGame save, boolean down) {
        GameSession session = save.getSession();
        ExplorationResult locked = requireNotInCombat(session);
        if (locked != null) {
            return locked;
        }
        Room room = session.currentRoom();
        boolean available = down ? room.isStairsDown() : room.isStairsUp();
        if (!available) {
            return ExplorationResult.failure("There are no stairs leading " + (down ? "down" : "up") + " here.");
        }
        session.setCurrentLevel(room.getStairsDestinationLevel());
        session.setCurrentRoomId(room.getStairsDestinationRoomId());
        session.currentRoom().setVisited(true);

        ExplorationResult result = new ExplorationResult();
        result.add(down ? "You descend the stairs, deeper into the dark." : "You climb the stairs.");
        result.add("Now on dungeon level " + (session.getCurrentLevel() + 1) + ".");
        advanceTurn(save, result, false);

        // A room entered by stairs gets the same passive trap/secret-door sense and trap-springing that
        // entering by a door (move) does — otherwise the same room behaves differently by entry path.
        maybePassiveRoomTrapSense(save, result);
        maybeSpringTrap(save, result);
        maybePassiveSecretDoorSense(save, result);

        result.add("");
        result.add(describeRoom(session));
        handleEncounter(save, result);
        return result;
    }

    /** Searches the current room for secret doors, traps and treasure; advances one dungeon turn. */
    public ExplorationResult search(SaveGame save) {
        return search(save, false);
    }

    /** As {@link #search(SaveGame)}, but with {@code verbose} showing the raw XP/prime-requisite
        breakdown behind a treasure find (used by {@code /autodelve}'s verbose log detail). */
    public ExplorationResult search(SaveGame save, boolean verbose) {
        GameSession session = save.getSession();
        ExplorationResult locked = requireNotInCombat(session);
        if (locked != null) {
            return locked;
        }
        Room room = session.currentRoom();
        ExplorationResult result = new ExplorationResult();
        result.add("You search the room carefully...");

        int secretChance = bestSecretDoorChance(save);
        int found = 0;
        for (Exit exit : room.getExits().values()) {
            if (exit.isSecret() && !exit.isRevealed() && dice.d(6) <= secretChance) {
                exit.setRevealed(true);
                found++;
                result.add("You discover a secret door to the " + exit.getDirection().lower() + "!");
            }
        }

        int trapChance = bestTrapDetectionChance(save);
        if (room.isTrapped() && !room.isTrapDetected() && !room.isTrapSprung() && dice.d(6) <= trapChance) {
            room.setTrapDetected(true);
            found++;
            result.add("You detect a trap: " + room.getTrapDescription() + ".");
        }
        for (Exit exit : room.getExits().values()) {
            if (exit.isTrapped() && !exit.isTrapDetected() && !exit.isTrapSprung() && dice.d(6) <= trapChance) {
                exit.setTrapDetected(true);
                mirrorExitTrapState(session, exit);
                found++;
                result.add("You detect a trap in the passage to the " + exit.getDirection().lower() + ": "
                        + exit.getTrapDescription() + ".");
            }
            if (exit.isDoorTrapped() && !exit.isDoorTrapDetected() && !exit.isDoorTrapSprung() && dice.d(6) <= trapChance) {
                exit.setDoorTrapDetected(true);
                mirrorExitDoorTrapState(session, exit);
                found++;
                result.add("You detect a trap on the " + exit.getDirection().lower() + " door's lock: "
                        + exit.getDoorTrapDescription() + ".");
            }
        }

        if (room.isHasTreasure() && !room.isLooted()) {
            if (room.isTreasureTrapped() && !room.isTreasureTrapDisarmed()) {
                found++;
                // The party's best remove-traps hand (a Thief if it has one) attempts the disarm — and
                // springs it on themselves if they fumble it, not on the front rank.
                Character disarmer = bestDisarmer(save);
                if (tryDisarmTreasureTrap(disarmer)) {
                    room.setTreasureTrapDisarmed(true);
                    result.add("You carefully disarm " + room.getTreasureTrapDescription()
                            + " guarding the treasure.");
                    loot(save, room, result, verbose);
                } else {
                    applyTrapDamage(save, room.getTreasureTrapDamage(), room.getTreasureTrapDescription(),
                            disarmer, result);
                    // Treasure stays put (room.looted is never set) so a retry is possible on a later search.
                }
            } else {
                found++;
                loot(save, room, result, verbose);
            }
        }

        if (found == 0) {
            result.add("You find nothing of interest.");
        }
        room.setSearched(true);
        advanceTurn(save, result, false);
        handleEncounter(save, result);
        return result;
    }

    /** Adds the room's treasure to the character, with the existing XP-per-gold and potion chance. */
    /** Loots the room's treasure, split across the party the same way combat XP already is: every
        living PC takes a full share, each living retainer a half-share (matching
        {@code CombatService.victory()}'s convention) — so a PC's own gold and gp-based XP reflect only
        their cut, not the whole hoard. Searching itself is still a primary-PC-only action in this pass
        (see {@link #search}), but the split accounts for the whole party. */
    private void loot(SaveGame save, Room room, ExplorationResult result, boolean verbose) {
        room.setLooted(true);

        int gemsValue = room.getTreasureGemsValue();
        int jewelryValue = room.getTreasureJewelryValue();
        int totalGold = room.getTreasureGold() + gemsValue + jewelryValue;

        StringBuilder treasureFound = new StringBuilder("You find treasure: **").append(room.getTreasureGold()).append(" gp**");
        if (gemsValue > 0) {
            treasureFound.append(", gems worth **").append(gemsValue).append(" gp**");
        }
        if (jewelryValue > 0) {
            treasureFound.append(", jewelry worth **").append(jewelryValue).append(" gp**");
        }
        treasureFound.append("!");
        result.add(treasureFound.toString());

        // Deliberate wage abstraction (mirrors CombatService.victory()'s XP split): retainers count as
        // full shares in the denominator, and their cut of the gold is never credited to anyone — a
        // hireling pockets their share, it doesn't stay party wealth. Their XP is half the PC share.
        List<Character> livingPcs = save.livingCharacters();
        List<Retainer> survivors = save.livingRetainers();
        int shares = livingPcs.size() + survivors.size();
        int perShare = totalGold / shares;

        for (Character pc : livingPcs) {
            pc.setGold(pc.getGold() + perShare);
        }
        if (shares > 1 && perShare < totalGold) {
            result.add("Each share: **" + perShare + " gp** (split " + shares + " ways).");
        }
        // B/X awards 1 XP per gold piece recovered from the dungeon — the main route to advancement.
        // Each side is credited XP for its own share, not the full haul.
        if (perShare > 0) {
            for (Character pc : livingPcs) {
                result.getLines().addAll(Leveling.awardXp(pc, perShare, dice, verbose));
            }
        }
        for (Retainer retainer : survivors) {
            for (String line : Leveling.awardXp(retainer, perShare / 2, dice)) {
                if (line.contains("Level up")) {
                    result.add(line);
                }
            }
        }

        // A fraction of hoards include a potion of healing among the coin — credited to the primary PC's
        // inventory for now (no per-PC inventory-choice mechanic yet).
        if (dice.d(4) == 1) {
            Character primary = save.getCharacter();
            primary.setHealingPotions(primary.getHealingPotions() + 1);
            result.add("Among the coins is a **potion of healing**! (`quaff` to drink.)");
        }
    }

    /** Remove Traps: a Thief's improving level-based chance; everyone else a flat, low fallback. */
    private boolean tryDisarmTreasureTrap(Character character) {
        int chance = character.getCharacterClass() == CharacterClass.THIEF
                ? ThiefSkills.removeTraps(character.getLevel())
                : NON_THIEF_REMOVE_TRAPS_CHANCE;
        return dice.d(100) <= chance;
    }

    /** Attempts to open a door in the given direction. Forcing a stuck door costs a turn. */
    public ExplorationResult open(SaveGame save, Direction direction) {
        GameSession session = save.getSession();
        ExplorationResult locked = requireNotInCombat(session);
        if (locked != null) {
            return locked;
        }
        Room room = session.currentRoom();
        Exit exit = room.getExits().get(direction);

        if (exit == null || !exit.isKnown()) {
            return ExplorationResult.failure("There is no door to the " + direction.lower() + ".");
        }
        DoorState door = exit.getDoor();
        switch (door) {
            case NONE, OPEN, AJAR -> {
                return ExplorationResult.failure("The way " + direction.lower() + " is already open.");
            }
            case CLOSED, UNLOCKED -> {
                setBothDoors(session, exit, DoorState.OPEN);
                return ExplorationResult.of("You open the door to the " + direction.lower() + ".");
            }
            case STUCK -> {
                ExplorationResult result = new ExplorationResult();
                // The strongest living PC throws their shoulder at it — not always the first-rolled PC.
                Character forcer = strongestPc(save);
                maybeSpringDoorTrap(save, exit, forcer, result);
                int threshold = Math.max(1, Math.min(5, 2 + forcer.getAbilities().modifier(Ability.STR)));
                boolean forced = dice.d(6) <= threshold;
                result.add("You throw your shoulder against the stuck door...");
                if (forced) {
                    setBothDoors(session, exit, DoorState.OPEN);
                    result.add("It bursts open!");
                } else {
                    result.add("It holds fast.");
                }
                advanceTurn(save, result, false, !forced); // forcing a door takes time and makes noise if it fails
                return result;
            }
            case LOCKED -> {
                // Any living Thief PC can pick it (the highest-level one, best odds) — not only the
                // first-rolled PC. A party with no Thief anywhere still needs a key.
                Character picker = bestLockpicker(save);
                if (picker == null) {
                    return ExplorationResult.failure("The door to the " + direction.lower()
                            + " is locked. You need a key or a thief's touch.");
                }
                ExplorationResult result = new ExplorationResult();
                maybeSpringDoorTrap(save, exit, picker, result);
                boolean picked = dice.d(100) <= ThiefSkills.openLocks(picker.getLevel());
                if (picked) {
                    exit.setEverLocked(true);
                    mirrorEverLocked(session, exit);
                    setBothDoors(session, exit, DoorState.UNLOCKED);
                    result.add("The lock clicks — it's no longer locked, but still needs a push.");
                } else {
                    result.add("You fumble with the lock, but it holds.");
                }
                advanceTurn(save, result, false); // picking a lock takes time, success or not
                return result;
            }
            case ONE_WAY -> {
                return ExplorationResult.failure("This door only opens from the other side — force, "
                        + "picks, and spikes won't help.");
            }
            default -> throw new IllegalStateException("Unhandled door state: " + door);
        }
    }

    /** Jams a spike/wedge into a door, holding it {@code open} (so it never swings shut behind the
        party — see {@link #move}) or {@code closed} (jamming it into {@link DoorState#STUCK}, the
        classic "spike it shut to block pursuit" tactic — works from either side, on any present door
        regardless of its current state). Costs a turn either way. The caller (see {@code
        command.SpikeCommand}) is responsible for confirming a spike is available and consuming it from
        inventory only if this succeeds. */
    public ExplorationResult spike(SaveGame save, Direction direction, boolean holdOpen) {
        GameSession session = save.getSession();
        ExplorationResult locked = requireNotInCombat(session);
        if (locked != null) {
            return locked;
        }
        Room room = session.currentRoom();
        Exit exit = room.getExits().get(direction);
        if (exit == null || !exit.isKnown() || exit.getDoor() == DoorState.NONE) {
            return ExplorationResult.failure("There is no door to the " + direction.lower() + " to spike.");
        }
        if (exit.getDoor() == DoorState.ONE_WAY) {
            return ExplorationResult.failure("This door won't take a spike — it only opens from the other side.");
        }
        if (exit.isSpiked()) {
            return ExplorationResult.failure("That door already has a spike jammed in it.");
        }
        if (holdOpen && !exit.getDoor().isPassable()) {
            return ExplorationResult.failure("The door to the " + direction.lower()
                    + " isn't open yet — `open " + direction.lower() + "` it first.");
        }
        setBothDoors(session, exit, holdOpen ? DoorState.OPEN : DoorState.STUCK);
        exit.setSpiked(true);
        mirrorSpiked(session, exit);

        ExplorationResult result = new ExplorationResult();
        result.add(holdOpen
                ? "You jam a spike under the door to the " + direction.lower() + ", wedging it open."
                : "You jam a spike into the door to the " + direction.lower() + ", wedging it shut.");
        advanceTurn(save, result, false);
        return result;
    }

    /** Listens at a door for sounds beyond it: a 1-in-6 passive chance, one attempt per exit, and no
        turn cost (matches the house rule that this is always rolled while moving at exploration speed). */
    public ExplorationResult listen(SaveGame save, Direction direction) {
        GameSession session = save.getSession();
        ExplorationResult locked = requireNotInCombat(session);
        if (locked != null) {
            return locked;
        }
        Room room = session.currentRoom();
        Exit exit = room.getExits().get(direction);
        if (exit == null || !exit.isKnown()) {
            return ExplorationResult.failure("There is no door to the " + direction.lower() + ".");
        }
        if (exit.isListened()) {
            return ExplorationResult.failure("You've already listened at the " + direction.lower() + " door.");
        }
        exit.setListened(true);
        if (dice.d(6) > 1) {
            return ExplorationResult.of("You press an ear to the door but hear nothing.");
        }
        Room destination = session.currentLevel().room(exit.getDestinationRoomId());
        if (destination != null && destination.hasLiveMonster()) {
            return ExplorationResult.of("You hear something moving beyond the " + direction.lower() + " door!");
        }
        return ExplorationResult.of("You listen carefully but hear nothing stirring beyond the "
                + direction.lower() + " door.");
    }

    /** Rests for one turn, resetting the fatigue clock; still burns light and risks a wandering
        monster like any other turn. */
    public ExplorationResult rest(SaveGame save) {
        ExplorationResult locked = requireNotInCombat(save.getSession());
        if (locked != null) {
            return locked;
        }
        ExplorationResult result = new ExplorationResult();
        result.add("The party rests for a turn, catching their breath.");
        advanceTurn(save, result, true);
        handleEncounter(save, result);
        return result;
    }

    // --- internals ---------------------------------------------------------

    private void setBothDoors(GameSession session, Exit exit, DoorState state) {
        exit.setDoor(state);
        Room destination = session.currentLevel().room(exit.getDestinationRoomId());
        Exit back = destination.getExits().get(exit.getDirection().opposite());
        if (back != null) {
            back.setDoor(state);
        }
    }

    /** A corridor trap is one physical hazard shared by both directions' {@link Exit} objects, which
        aren't otherwise kept in sync (mirrors {@link #setBothDoors}) — detecting or springing it from
        either room must update both so it can't be "re-discovered" or re-sprung walking back through. */
    private void mirrorExitTrapState(GameSession session, Exit exit) {
        Room destination = session.currentLevel().room(exit.getDestinationRoomId());
        Exit back = destination == null ? null : destination.getExits().get(exit.getDirection().opposite());
        if (back != null) {
            back.setTrapDetected(exit.isTrapDetected());
            back.setTrapSprung(exit.isTrapSprung());
        }
    }

    /** As {@link #mirrorExitTrapState}, but for a trap on the door's lock/mechanism itself rather than
        the passage. */
    private void mirrorExitDoorTrapState(GameSession session, Exit exit) {
        Room destination = session.currentLevel().room(exit.getDestinationRoomId());
        Exit back = destination == null ? null : destination.getExits().get(exit.getDirection().opposite());
        if (back != null) {
            back.setDoorTrapDetected(exit.isDoorTrapDetected());
            back.setDoorTrapSprung(exit.isDoorTrapSprung());
        }
    }

    /** Mirrors {@link Exit#isEverLocked()} to the other side of the doorway — set defensively the
        moment a lock is actually picked (not just at dungeon-generation/module-authoring time), so
        swing-shut (see {@link #move}) rests the door at {@code UNLOCKED} rather than {@code CLOSED}
        regardless of how the door reached {@code LOCKED} in the first place. */
    private void mirrorEverLocked(GameSession session, Exit exit) {
        Room destination = session.currentLevel().room(exit.getDestinationRoomId());
        Exit back = destination == null ? null : destination.getExits().get(exit.getDirection().opposite());
        if (back != null) {
            back.setEverLocked(exit.isEverLocked());
        }
    }

    /** Mirrors {@link Exit#isSpiked()} to the other side of the doorway, matching {@link
        #setBothDoors}/{@link #mirrorExitTrapState}'s convention for keeping one physical door in sync
        across both rooms' independent {@link Exit} objects. */
    private void mirrorSpiked(GameSession session, Exit exit) {
        Room destination = session.currentLevel().room(exit.getDestinationRoomId());
        Exit back = destination == null ? null : destination.getExits().get(exit.getDirection().opposite());
        if (back != null) {
            back.setSpiked(exit.isSpiked());
        }
    }

    /** Advances one dungeon turn: burns light, ticks the rest clock, and runs the wandering-monster
        check. */
    private void advanceTurn(SaveGame save, ExplorationResult result, boolean isRest) {
        advanceTurn(save, result, isRest, false);
    }

    /** Advances one dungeon turn: burns light, ticks the rest clock, and runs the wandering-monster
        check. */
    private void advanceTurn(SaveGame save, ExplorationResult result, boolean isRest, boolean isLoud) {
        GameSession session = save.getSession();
        session.setDungeonTurn(session.getDungeonTurn() + 1);

        if (isRest) {
            session.setTurnsSinceRest(0);
        } else {
            session.setTurnsSinceRest(session.getTurnsSinceRest() + 1);
            if (session.getTurnsSinceRest() == 5) {
                result.add("⚠️ **Warning**: The party has traveled 5 turns without resting. You must REST next turn or become fatigued.");
            } else if (session.getTurnsSinceRest() == 6) {
                result.add("🔴 **The party is exhausted!** Skipping mandatory rest incurs a -1 penalty to hit and damage rolls until you rest.");
            }
        }

        lighting.reconcileBearer(save, result);
        lighting.tickFuel(save, result);
        mules.reconcileHandler(save, result);
        tickEffects(save, result);

        if (isLoud) {
            result.add("🔊 **Loud Noise!** The noise echoing through the halls might attract unwanted attention...");
            int threshold = 1;
            if (hasHeavyArmor(save)) {
                threshold += 1;
            }
            Character activeChar = save.getCharacter();
            if (activeChar != null && (activeChar.getCharacterClass() == CharacterClass.THIEF || activeChar.getCharacterClass() == CharacterClass.HALFLING)) {
                threshold = Math.max(1, threshold - 1);
            }
            int roll = dice.d(6);
            if (roll <= threshold) {
                triggerWanderingMonster(save, result);
            }
        }

        // Wandering monster check every other turn (1-in-6, plus an extra roll per additional
        // 8 party members beyond 9).
        int size = partySize(save);
        if (session.getDungeonTurn() % 2 == 0 && wanderingMonsterTriggered(size)) {
            triggerWanderingMonster(save, result);
        }
    }

    private boolean triggerWanderingMonster(SaveGame save, ExplorationResult result) {
        GameSession session = save.getSession();
        Room room = session.currentRoom();
        if (!room.hasLiveMonster()) {
            MonsterType type = pickWanderingMonster(session.getCurrentLevel() + 1);
            int count = Math.max(1, dice.d(Math.max(1, session.getCurrentLevel() + 2)));
            room.setContent(ContentType.MONSTER);
            room.setMonsterName(type.name());
            room.setMonsterCount(count);
            room.setCleared(false);
            room.setFreshEncounter(true);
            result.add("**Wandering monster!** "
                    + count + " " + type.name().toLowerCase() + (count > 1 ? "s" : "") + " appear!");
            return true;
        }
        return false;
    }

    private void tickEffects(SaveGame save, ExplorationResult result) {
        GameSession session = save.getSession();
        List<ActiveEffect> active = session.getActiveEffects();
        if (active == null || active.isEmpty()) {
            return;
        }

        Iterator<ActiveEffect> it = active.iterator();
        while (it.hasNext()) {
            ActiveEffect effect = it.next();
            effect.setTurnsRemaining(effect.getTurnsRemaining() - 1);

            String label = effect.getLabel();
            String ownerText = effect.getOwnerPcName() != null ? " on " + effect.getOwnerPcName() : "";

            if (effect.getTurnsRemaining() == 1) {
                result.add("⚠️ **Warning**: The effect of **" + label + "**" + ownerText + " is flickering and will soon end.");
            } else if (effect.getTurnsRemaining() <= 0) {
                result.add("✨ The effect of **" + label + "**" + ownerText + " has ended.");
                it.remove();
                if (label.equalsIgnoreCase("Light") || label.equalsIgnoreCase("Light (Cleric)")) {
                    recheckLightDarkness(save, result);
                }
            }
        }
    }

    private void recheckLightDarkness(SaveGame save, ExplorationResult result) {
        GameSession session = save.getSession();
        if (session.isSpellLightActive()) {
            return;
        }
        if (session.getActiveLight() != null && session.getLightBearer() != null) {
            return;
        }
        session.setInDarkness(true);
        result.add("🌑 **The magical light fades, and you are plunged into darkness!**");
    }

    private boolean hasHeavyArmor(SaveGame save) {
        for (Character pc : save.getCharacters()) {
            if (pc.isAlive() && (pc.getArmor() == dev.freitas.delve.game.engine.Armor.CHAIN_MAIL || pc.getArmor() == dev.freitas.delve.game.engine.Armor.PLATE_MAIL)) {
                return true;
            }
        }
        for (Retainer r : save.getRetainers()) {
            if (r.isAlive() && (r.getArmor() == dev.freitas.delve.game.engine.Armor.CHAIN_MAIL || r.getArmor() == dev.freitas.delve.game.engine.Armor.PLATE_MAIL)) {
                return true;
            }
        }
        return false;
    }

    private void maybeSpringTrap(SaveGame save, ExplorationResult result) {
        Room room = save.getSession().currentRoom();
        if (!room.isTrapped() || room.isTrapSprung() || room.isTrapDetected()) {
            return;
        }
        // B/X: a trap springs on a 1-2 in 6 when entered unawares.
        if (dice.d(6) <= 2) {
            room.setTrapSprung(true);
            applyTrapDamage(save, room.getTrapDamage(), room.getTrapDescription(), result);
        }
    }

    /** Corridor counterpart of {@link #maybeSpringTrap}: sprung while traversing a trapped exit. */
    private void maybeSpringCorridorTrap(SaveGame save, Exit exit, ExplorationResult result) {
        if (!exit.isTrapped() || exit.isTrapSprung() || exit.isTrapDetected()) {
            return;
        }
        if (dice.d(6) <= 2) {
            exit.setTrapSprung(true);
            mirrorExitTrapState(save.getSession(), exit);
            applyTrapDamage(save, exit.getTrapDamage(), exit.getTrapDescription(), result);
        }
    }

    /** A trap on the door's own lock/mechanism, sprung by forcing or picking it (not by walking
        through) — same 2-in-6-unless-detected formula as every other trap here. It springs on the
        one doing the forcing/picking, not the front rank. */
    private void maybeSpringDoorTrap(SaveGame save, Exit exit, Character actor, ExplorationResult result) {
        if (!exit.isDoorTrapped() || exit.isDoorTrapSprung() || exit.isDoorTrapDetected()) {
            return;
        }
        if (dice.d(6) <= 2) {
            exit.setDoorTrapSprung(true);
            mirrorExitDoorTrapState(save.getSession(), exit);
            applyTrapDamage(save, exit.getDoorTrapDamage(), exit.getDoorTrapDescription(), actor, result);
        }
    }

    /** Rolls room/corridor trap damage against whoever is exposed at the front of the marching order
        (see {@link #frontRankVictim}); door and treasure traps use the explicit-victim overload to hit
        the opener/searcher instead. */
    private void applyTrapDamage(SaveGame save, DamageRoll damage, String description, ExplorationResult result) {
        applyTrapDamage(save, damage, description, frontRankVictim(save), result);
    }

    /** Rolls trap damage against {@code victim} with a saving throw for half. The delve only ends when
        no living PC remains (matching {@code CombatService}'s whole-party defeat rule) — a retainer's
        death, or one PC of several falling, doesn't end it. */
    private void applyTrapDamage(
            SaveGame save, DamageRoll damage, String description, Combatant victim, ExplorationResult result) {
        GameSession session = save.getSession();
        int amount = damage.roll(dice);
        Advanceable adv = (Advanceable) victim; // only PCs/retainers are ever picked — see frontRankVictim
        int saveTarget = SavingThrows.forCharacter(adv.getCharacterClass(), adv.getLevel())
                .paralysisPetrify();
        boolean saved = dice.d20() >= saveTarget;
        if (saved) {
            amount = Math.max(1, amount / 2);
        }
        victim.setCurrentHp(victim.getCurrentHp() - amount);
        boolean solo = victim instanceof Character && save.getCharacters().size() == 1;
        result.add("**A trap springs — " + description + "!** " + (solo ? "You take " : victim.getName() + " takes ")
                + amount + " damage" + (saved ? " (saved for half)" : "") + ".");
        if (!victim.isAlive()) {
            if (victim instanceof Character && save.livingCharacters().isEmpty()) {
                session.setState(SessionState.IN_TOWN);
                result.add(solo
                        ? "**" + victim.getName() + " has died in the dungeon.**"
                        : "**Your party has been wiped out in the dungeon.**");
            } else {
                result.add("**" + victim.getName() + " has been slain by the trap.**");
            }
        }
    }

    /** The trap's victim: a random living front-rank PC or retainer ({@link Formation#engagedFront} —
        the person physically leading the way). The mule is never picked even when it marches up front:
        an animal has no {@link SavingThrows} table (and no {@link Advanceable} class/level to look one
        up by). Falls back to the primary PC if the front rank is somehow empty. */
    private Combatant frontRankVictim(SaveGame save) {
        int width = save.getSession().currentRoom().getCorridorWidth();
        List<Combatant> eligible = new ArrayList<>();
        for (Combatant c : Formation.engagedFront(save.fullOrder(), width)) {
            if (c instanceof Advanceable) {
                eligible.add(c);
            }
        }
        if (eligible.isEmpty()) {
            return save.getCharacter();
        }
        return eligible.get(dice.d(eligible.size()) - 1);
    }

    /** Total party size for the wandering-monster check: every living PC, every living retainer, and
        the party's mule if it has one (physically present and just as noisy). Package-private: tests
        exercise this directly. */
    int partySize(SaveGame save) {
        return save.livingCharacters().size() + save.livingRetainers().size() + save.livingMules().size();
    }

    /** Base 1-in-6 wandering-monster check, plus an extra 1-in-6 roll for every additional set of 8
        party members beyond 9 (any success triggers an encounter). */
    private boolean wanderingMonsterTriggered(int partySize) {
        if (dice.d(6) == 1) {
            return true;
        }
        int extraChecks = partySize > 9 ? 1 + (partySize - 10) / 8 : 0;
        for (int i = 0; i < extraChecks; i++) {
            if (dice.d(6) == 1) {
                return true;
            }
        }
        return false;
    }

    /** Dwarf → 2-in-6, everyone else 1-in-6 — shared by the active `/search` bonus and the passive sense. */
    private int trapDetectionChance(CharacterClass characterClass) {
        return characterClass == CharacterClass.DWARF ? 2 : 1;
    }

    /** Elf → 2-in-6, everyone else 1-in-6 — shared by the active `/search` bonus and the passive sense. */
    private int secretDoorChance(CharacterClass characterClass) {
        return characterClass == CharacterClass.ELF ? 2 : 1;
    }

    /** The party's best active-search trap chance across every living PC — the whole party searches
        together, so a Dwarf anywhere in a multi-PC party lends their 2-in-6, not just a Dwarf who
        happens to be the first-rolled PC. */
    private int bestTrapDetectionChance(SaveGame save) {
        int best = 1;
        for (Character pc : save.livingCharacters()) {
            best = Math.max(best, trapDetectionChance(pc.getCharacterClass()));
        }
        return best;
    }

    /** As {@link #bestTrapDetectionChance}, for secret doors (an Elf PC anywhere in the party). */
    private int bestSecretDoorChance(SaveGame save) {
        int best = 1;
        for (Character pc : save.livingCharacters()) {
            best = Math.max(best, secretDoorChance(pc.getCharacterClass()));
        }
        return best;
    }

    /** The living PC with the best Remove Traps chance — the highest-level Thief if the party has one,
        else the primary PC (every non-Thief shares the same flat fallback chance). */
    private Character bestDisarmer(SaveGame save) {
        Character best = save.getCharacter();
        int bestChance = -1;
        for (Character pc : save.livingCharacters()) {
            int chance = pc.getCharacterClass() == CharacterClass.THIEF
                    ? ThiefSkills.removeTraps(pc.getLevel())
                    : NON_THIEF_REMOVE_TRAPS_CHANCE;
            if (chance > bestChance) {
                bestChance = chance;
                best = pc;
            }
        }
        return best;
    }

    /** The living PC with the highest Strength — who throws their shoulder at a stuck door. Falls back
        to the primary PC when there are somehow no living PCs. */
    private Character strongestPc(SaveGame save) {
        Character best = save.getCharacter();
        int bestStr = Integer.MIN_VALUE;
        for (Character pc : save.livingCharacters()) {
            int str = pc.getAbilities().score(Ability.STR);
            if (str > bestStr) {
                bestStr = str;
                best = pc;
            }
        }
        return best;
    }

    /** The living PC with the highest Charisma — the party "face" whose reaction bonus applies when the
        whole party meets a monster. Falls back to the primary PC. */
    private Character bestCharismaPc(SaveGame save) {
        Character best = save.getCharacter();
        int bestCha = Integer.MIN_VALUE;
        for (Character pc : save.livingCharacters()) {
            int cha = pc.getAbilities().score(Ability.CHA);
            if (cha > bestCha) {
                bestCha = cha;
                best = pc;
            }
        }
        return best;
    }

    /** The living Thief PC best able to pick a lock (highest level, best {@code openLocks} odds), or
        {@code null} if the party has no living Thief — locks stay a Thief-only skill, but any Thief in
        a multi-PC party can try, not just the first-rolled PC. */
    private Character bestLockpicker(SaveGame save) {
        Character best = null;
        for (Character pc : save.livingCharacters()) {
            if (pc.getCharacterClass() == CharacterClass.THIEF && (best == null || pc.getLevel() > best.getLevel())) {
                best = pc;
            }
        }
        return best;
    }

    private boolean anyLivingPartyMemberIsClass(SaveGame save, CharacterClass characterClass) {
        for (Character pc : save.livingCharacters()) {
            if (pc.getCharacterClass() == characterClass) {
                return true;
            }
        }
        for (Retainer r : save.livingRetainers()) {
            if (r.getCharacterClass() == characterClass) {
                return true;
            }
        }
        return false;
    }

    /** Passive trap sense: tries the pole first (if poling and the front rank is engaged), then a
        Dwarf's racial bonus — either can succeed, and only one roll happens so a poling Dwarf doesn't
        double-roll for no reason. */
    private boolean rollPassiveTrapSense(SaveGame save) {
        GameSession session = save.getSession();
        if (session.isPolingFrontRank()) {
            int width = session.currentRoom().getCorridorWidth();
            if (!Formation.engagedFront(save.fullOrder(), width).isEmpty()
                    && dice.d(6) <= PASSIVE_TRAP_SENSE_CHANCE) {
                return true;
            }
        }
        if (anyLivingPartyMemberIsClass(save, CharacterClass.DWARF)) {
            return dice.d(6) <= trapDetectionChance(CharacterClass.DWARF);
        }
        return false;
    }

    private void maybePassiveRoomTrapSense(SaveGame save, ExplorationResult result) {
        Room room = save.getSession().currentRoom();
        if (!room.isTrapped() || room.isTrapDetected() || room.isTrapSprung()) {
            return;
        }
        if (rollPassiveTrapSense(save)) {
            room.setTrapDetected(true);
            result.add("You sense a trap here: " + room.getTrapDescription() + ".");
        }
    }

    private void maybePassiveCorridorTrapSense(SaveGame save, Exit exit, ExplorationResult result) {
        if (!exit.isTrapped() || exit.isTrapDetected() || exit.isTrapSprung()) {
            return;
        }
        if (rollPassiveTrapSense(save)) {
            exit.setTrapDetected(true);
            mirrorExitTrapState(save.getSession(), exit);
            result.add("You sense a trap ahead: " + exit.getTrapDescription() + ".");
        }
    }

    /** Elf racial passive: notices a secret door on room entry, no `/search` needed. */
    private void maybePassiveSecretDoorSense(SaveGame save, ExplorationResult result) {
        if (!anyLivingPartyMemberIsClass(save, CharacterClass.ELF)) {
            return;
        }
        Room room = save.getSession().currentRoom();
        int chance = secretDoorChance(CharacterClass.ELF);
        for (Exit exit : room.getExits().values()) {
            if (exit.isSecret() && !exit.isRevealed() && dice.d(6) <= chance) {
                exit.setRevealed(true);
                result.add("You notice a secret door to the " + exit.getDirection().lower() + "!");
            }
        }
    }

    /** When a live monster shares the room, rolls reaction (unless the room scripts a fixed disposition)
        and starts combat if it is hostile. */
    private void handleEncounter(SaveGame save, ExplorationResult result) {
        GameSession session = save.getSession();
        if (session.getState() == SessionState.IN_COMBAT) {
            return;
        }
        Room room = session.currentRoom();
        if (!room.hasLiveMonster()) {
            return;
        }
        MonsterType type = Bestiary.byName(room.getMonsterName());
        result.add("");
        MonsterDisposition scripted = room.getScriptedDisposition();
        ReactionTier reaction = scripted == null ? combat.reaction(bestCharismaPc(save), type) : null;
        boolean hostile = scripted != null ? scripted == MonsterDisposition.HOSTILE : reaction.isHostile();
        if (hostile) {
            result.getLines().addAll(combat.startCombat(save).getLines());
        } else {
            String plural = room.getMonsterName().toLowerCase() + (room.getMonsterCount() > 1 ? "s" : "");
            String flavor;
            if (scripted == MonsterDisposition.FRIENDLY || reaction == ReactionTier.FRIENDLY) {
                flavor = "seem friendly and let you pass";
            } else if (reaction == ReactionTier.INDIFFERENT) {
                flavor = "seem indifferent and let you pass";
            } else if (reaction == ReactionTier.UNCERTAIN) {
                flavor = "eye you uncertainly, unsure what to make of you";
            } else {
                flavor = "watch you warily but do not attack yet";
            }
            result.add("The " + plural + " " + flavor + ". (`attack` to fight, or move on.)");
        }
    }

    private MonsterType pickWanderingMonster(int depth) {
        List<MonsterType> eligible = new ArrayList<>();
        for (MonsterType type : Bestiary.all()) {
            int effectiveHd = type.hitDiceCount() + (type.hitDiceBonus() > 0 ? 1 : 0);
            if (effectiveHd <= depth + 1) {
                eligible.add(type);
            }
        }
        if (eligible.isEmpty()) {
            eligible = Bestiary.all();
        }
        return eligible.get(dice.d(eligible.size()) - 1);
    }

    private String describeRoom(GameSession session) {
        Room room = session.currentRoom();
        StringBuilder sb = new StringBuilder();
        sb.append("**Level ").append(session.getCurrentLevel() + 1)
                .append(", turn ").append(session.getDungeonTurn()).append("**\n");

        if (session.isInDarkness()) {
            sb.append("It is pitch black. You can barely feel your way around.\n");
        }
        if (room.getName() != null && !room.getName().isBlank()) {
            sb.append("__").append(room.getName()).append("__\n");
        }
        sb.append("You are in ").append(room.getDescription()).append(".");
        if (room.getReadAloud() != null && !room.getReadAloud().isBlank()) {
            sb.append("\n> ").append(room.getReadAloud().replace("\n", "\n> "));
        }

        if (room.hasLiveMonster()) {
            sb.append("\n⚔️ **").append(room.getMonsterCount()).append(" ")
                    .append(room.getMonsterName().toLowerCase())
                    .append(room.getMonsterCount() > 1 ? "s" : "").append("** lurk here, hostile!");
        } else if (room.getContent() == ContentType.SPECIAL && room.getSpecialText() != null) {
            sb.append("\n").append(room.getSpecialText());
        }
        if (room.isTrapDetected() && !room.isTrapSprung()) {
            sb.append("\n⚠️ You know there is a trap here: ").append(room.getTrapDescription()).append(".");
        }
        if (room.isStairsDown()) {
            sb.append("\nA stairway descends deeper (`move down`).");
        }
        if (room.isStairsUp()) {
            sb.append("\nStairs lead back up (`move up`).");
        }
        if (room.getCorridorWidth() < 3) {
            sb.append("\n_This passage is only wide enough for ").append(room.getCorridorWidth())
                    .append(" abreast._");
        }

        sb.append("\n").append(describeExits(room));
        return sb.toString();
    }

    private String describeExits(Room room) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Direction, Exit> entry : room.getExits().entrySet()) {
            Exit exit = entry.getValue();
            if (!exit.isKnown()) {
                continue;
            }
            String door = switch (exit.getDoor()) {
                case NONE -> "open";
                case OPEN -> "open door";
                case AJAR -> "door ajar";
                case CLOSED -> "closed door";
                case UNLOCKED -> "unlocked door";
                case STUCK -> "stuck door";
                case LOCKED -> "locked door";
                case ONE_WAY -> "one-way door";
            };
            String trapWarning = exit.isDoorTrapDetected() && !exit.isDoorTrapSprung() ? " ⚠️trapped lock" : "";
            parts.add(entry.getKey().lower() + " (" + door + trapWarning + ")");
        }
        if (parts.isEmpty()) {
            return "There are no obvious exits.";
        }
        return "Exits: " + String.join(", ", parts) + ".";
    }
}
