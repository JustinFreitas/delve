package dev.freitas.delve.game.session;

import dev.freitas.delve.game.Bestiary;
import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Formation;
import dev.freitas.delve.game.engine.Leveling;
import dev.freitas.delve.game.engine.LightSource;
import dev.freitas.delve.game.engine.SavingThrows;
import dev.freitas.delve.game.engine.ThiefSkills;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.ContentType;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.GameSession;
import dev.freitas.delve.game.model.MonsterDisposition;
import dev.freitas.delve.game.model.MonsterType;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The exploration rules: starting a delve, looking, moving, searching, opening doors, and descending
 * stairs — together with the B/X bookkeeping each action triggers (10-minute dungeon turns, torch
 * burn, and a wandering-monster check every other turn). Combat resolution itself arrives in the next
 * milestone; for now an encounter is surfaced and left in the room.
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

    public ExplorationService(Dice dice, DungeonGenerator generator, CombatService combat, LightingService lighting) {
        this.dice = dice;
        this.generator = generator;
        this.combat = combat;
        this.lighting = lighting;
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

    /** Moves through a cardinal exit, advancing one dungeon turn. */
    public ExplorationResult move(SaveGame save, Direction direction) {
        GameSession session = save.getSession();
        if (session.getState() == SessionState.IN_COMBAT) {
            return ExplorationResult.failure("You are locked in combat — `attack` or `flee` first.");
        }
        Room room = session.currentRoom();
        Exit exit = room.getExits().get(direction);

        if (exit == null || !exit.isKnown()) {
            return ExplorationResult.failure("There is no exit to the " + direction.lower() + ".");
        }
        if (!exit.getDoor().isPassable()) {
            return ExplorationResult.failure("The way " + direction.lower() + " is blocked by a "
                    + exit.getDoor().name().toLowerCase() + " door. Try `open " + direction.lower() + "`.");
        }

        ExplorationResult result = new ExplorationResult();
        result.add("You go " + direction.lower() + ".");

        // Corridor trap: checked/sprung for the passage itself, while still logically "in" it.
        maybePassiveCorridorTrapSense(save, exit, result);
        maybeSpringCorridorTrap(save, exit, result);

        session.setCurrentRoomId(exit.getDestinationRoomId());
        session.currentRoom().setVisited(true);
        advanceTurn(save, result);

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
        advanceTurn(save, result);
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
        Character character = save.getCharacter();
        Room room = session.currentRoom();
        ExplorationResult result = new ExplorationResult();
        result.add("You search the room carefully...");

        int secretChance = secretDoorChance(character.getCharacterClass());
        int found = 0;
        for (Exit exit : room.getExits().values()) {
            if (exit.isSecret() && !exit.isRevealed() && dice.d(6) <= secretChance) {
                exit.setRevealed(true);
                found++;
                result.add("You discover a secret door to the " + exit.getDirection().lower() + "!");
            }
        }

        int trapChance = trapDetectionChance(character.getCharacterClass());
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
        }

        if (room.isHasTreasure() && !room.isLooted()) {
            if (room.isTreasureTrapped() && !room.isTreasureTrapDisarmed()) {
                found++;
                if (tryDisarmTreasureTrap(character)) {
                    room.setTreasureTrapDisarmed(true);
                    result.add("You carefully disarm " + room.getTreasureTrapDescription()
                            + " guarding the treasure.");
                    loot(save, room, result, verbose);
                } else {
                    applyTrapDamage(save, room.getTreasureTrapDamage(), room.getTreasureTrapDescription(), result);
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
        advanceTurn(save, result);
        handleEncounter(save, result);
        return result;
    }

    /** Adds the room's treasure to the character, with the existing XP-per-gold and potion chance. */
    /** Loots the room's treasure, split across the party the same way combat XP already is: the PC
        takes a full share, each living retainer a half-share (matching {@code CombatService.victory()}'s
        convention) — so the PC's own gold and gp-based XP reflect only their cut, not the whole hoard. */
    private void loot(SaveGame save, Room room, ExplorationResult result, boolean verbose) {
        Character character = save.getCharacter();
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

        List<Retainer> survivors = save.livingRetainers();
        int shares = 1 + survivors.size();
        int pcShare = totalGold / shares;

        character.setGold(character.getGold() + pcShare);
        if (!survivors.isEmpty() && pcShare < totalGold) {
            result.add("Your share: **" + pcShare + " gp** (the rest goes to your retainers' cut).");
        }
        // B/X awards 1 XP per gold piece recovered from the dungeon — the main route to advancement.
        // Each side is credited XP for its own share, not the full haul.
        if (pcShare > 0) {
            result.getLines().addAll(Leveling.awardXp(character, pcShare, dice, verbose));
        }
        for (Retainer retainer : survivors) {
            for (String line : Leveling.awardXp(retainer, pcShare / 2, dice)) {
                if (line.contains("Level up")) {
                    result.add(line);
                }
            }
        }

        // A fraction of hoards include a potion of healing among the coin.
        if (dice.d(4) == 1) {
            character.setHealingPotions(character.getHealingPotions() + 1);
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
        Character character = save.getCharacter();
        Room room = session.currentRoom();
        Exit exit = room.getExits().get(direction);

        if (exit == null || !exit.isKnown()) {
            return ExplorationResult.failure("There is no door to the " + direction.lower() + ".");
        }
        DoorState door = exit.getDoor();
        switch (door) {
            case NONE, OPEN -> {
                return ExplorationResult.failure("The way " + direction.lower() + " is already open.");
            }
            case CLOSED -> {
                setBothDoors(session, exit, DoorState.OPEN);
                return ExplorationResult.of("You open the door to the " + direction.lower() + ".");
            }
            case STUCK -> {
                ExplorationResult result = new ExplorationResult();
                int threshold = Math.max(1, Math.min(5, 2 + character.getAbilities().modifier(Ability.STR)));
                boolean forced = dice.d(6) <= threshold;
                result.add("You throw your shoulder against the stuck door...");
                if (forced) {
                    setBothDoors(session, exit, DoorState.OPEN);
                    result.add("It bursts open!");
                } else {
                    result.add("It holds fast.");
                }
                advanceTurn(save, result); // forcing a door takes time and makes noise
                return result;
            }
            default -> {
                return ExplorationResult.failure(
                        "The door to the " + direction.lower() + " is locked. You need a key or a thief's tools.");
            }
        }
    }

    /** Listens at a door for sounds beyond it: a 1-in-6 passive chance, one attempt per exit, and no
        turn cost (matches the house rule that this is always rolled while moving at exploration speed). */
    public ExplorationResult listen(SaveGame save, Direction direction) {
        GameSession session = save.getSession();
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
        ExplorationResult result = new ExplorationResult();
        result.add("The party rests for a turn, catching their breath.");
        advanceTurn(save, result);
        save.getSession().setTurnsSinceRest(0);
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

    /** Advances one dungeon turn: burns light, ticks the rest clock, and runs the wandering-monster
        check. */
    private void advanceTurn(SaveGame save, ExplorationResult result) {
        GameSession session = save.getSession();
        session.setDungeonTurn(session.getDungeonTurn() + 1);
        session.setTurnsSinceRest(session.getTurnsSinceRest() + 1);

        lighting.reconcileBearer(save, result);
        lighting.tickFuel(save, result);

        // Wandering monster check every other turn (1-in-6, plus an extra roll per additional
        // 8 party members beyond 9).
        int partySize = 1 + save.livingRetainers().size();
        if (session.getDungeonTurn() % 2 == 0 && wanderingMonsterTriggered(partySize)) {
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
            }
        }
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

    /** Rolls trap damage against the character with a saving throw for half; shared by room, corridor
        and treasure traps. */
    private void applyTrapDamage(SaveGame save, DamageRoll damage, String description, ExplorationResult result) {
        GameSession session = save.getSession();
        Character character = save.getCharacter();
        int amount = damage.roll(dice);
        int saveTarget = SavingThrows.forCharacter(character.getCharacterClass(), character.getLevel())
                .paralysisPetrify();
        boolean saved = dice.d20() >= saveTarget;
        if (saved) {
            amount = Math.max(1, amount / 2);
        }
        character.setCurrentHp(character.getCurrentHp() - amount);
        result.add("**A trap springs — " + description + "!** You take " + amount
                + " damage" + (saved ? " (saved for half)" : "") + ".");
        if (!character.isAlive()) {
            session.setState(SessionState.IN_TOWN);
            result.add("**" + character.getName() + " has died in the dungeon.**");
        }
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

    private boolean anyLivingPartyMemberIsClass(SaveGame save, CharacterClass characterClass) {
        Character character = save.getCharacter();
        if (character.isAlive() && character.getCharacterClass() == characterClass) {
            return true;
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
        boolean hostile = scripted != null
                ? scripted == MonsterDisposition.HOSTILE
                : combat.isHostileReaction(save.getCharacter(), type);
        if (hostile) {
            result.getLines().addAll(combat.startCombat(save).getLines());
        } else {
            String plural = room.getMonsterName().toLowerCase() + (room.getMonsterCount() > 1 ? "s" : "");
            if (scripted == MonsterDisposition.FRIENDLY) {
                result.add("The " + plural + " seem friendly and let you pass. (`attack` to fight anyway, or move on.)");
            } else {
                result.add("The " + plural + " watch you warily but do not attack yet. (`attack` to fight, or move on.)");
            }
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
                case CLOSED -> "closed door";
                case STUCK -> "stuck door";
                case LOCKED -> "locked door";
            };
            parts.add(entry.getKey().lower() + " (" + door + ")");
        }
        if (parts.isEmpty()) {
            return "There are no obvious exits.";
        }
        return "Exits: " + String.join(", ", parts) + ".";
    }
}
