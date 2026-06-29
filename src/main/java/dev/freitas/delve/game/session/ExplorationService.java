package dev.freitas.delve.game.session;

import dev.freitas.delve.game.Bestiary;
import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.SavingThrows;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.ContentType;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.GameSession;
import dev.freitas.delve.game.model.MonsterType;
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
    private static final int TORCH_TURNS = 6;

    private final Dice dice;
    private final DungeonGenerator generator;
    private final CombatService combat;

    public ExplorationService(Dice dice, DungeonGenerator generator, CombatService combat) {
        this.dice = dice;
        this.generator = generator;
        this.combat = combat;
    }

    /** Begins a fresh dungeon run for the character, lighting the first torch. */
    public ExplorationResult enter(SaveGame save) {
        Character character = save.getCharacter();
        GameSession session = save.getSession();

        Dungeon dungeon = generator.generate(LEVELS, ROOMS_PER_LEVEL);
        session.setDungeon(dungeon);
        session.setCurrentLevel(0);
        session.setCurrentRoomId(dungeon.level(0).getEntranceRoomId());
        session.setDungeonTurn(0);
        session.setInDarkness(false);
        session.setState(SessionState.EXPLORING);

        ExplorationResult result = new ExplorationResult();
        result.add("You light a torch and descend into the dungeon...");
        if (character.getTorches() > 0) {
            character.setTorches(character.getTorches() - 1);
            session.setTorchTurnsRemaining(TORCH_TURNS);
        } else {
            session.setInDarkness(true);
            result.add("You have no torches — you grope forward in darkness!");
        }
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

        session.setCurrentRoomId(exit.getDestinationRoomId());
        session.currentRoom().setVisited(true);

        ExplorationResult result = new ExplorationResult();
        result.add("You go " + direction.lower() + ".");
        advanceTurn(save, result);
        maybeSpringTrap(save, result);
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
        GameSession session = save.getSession();
        Character character = save.getCharacter();
        Room room = session.currentRoom();
        ExplorationResult result = new ExplorationResult();
        result.add("You search the room carefully...");

        // Elves find secret doors more readily (2-in-6 vs 1-in-6).
        int secretChance = switch (character.getCharacterClass()) {
            case ELF -> 2;
            default -> 1;
        };
        int found = 0;
        for (Exit exit : room.getExits().values()) {
            if (exit.isSecret() && !exit.isRevealed() && dice.d(6) <= secretChance) {
                exit.setRevealed(true);
                found++;
                result.add("You discover a secret door to the " + exit.getDirection().lower() + "!");
            }
        }

        // Dwarves are keen on stonework traps (2-in-6).
        int trapChance = switch (character.getCharacterClass()) {
            case DWARF -> 2;
            default -> 1;
        };
        if (room.isTrapped() && !room.isTrapDetected() && !room.isTrapSprung() && dice.d(6) <= trapChance) {
            room.setTrapDetected(true);
            found++;
            result.add("You detect a trap: " + room.getTrapDescription() + ".");
        }

        if (room.isHasTreasure() && !room.isLooted()) {
            room.setLooted(true);
            character.setGold(character.getGold() + room.getTreasureGold());
            found++;
            result.add("You find treasure: **" + room.getTreasureGold() + " gp**!");
        }

        if (found == 0) {
            result.add("You find nothing of interest.");
        }
        room.setSearched(true);
        advanceTurn(save, result);
        handleEncounter(save, result);
        return result;
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

    // --- internals ---------------------------------------------------------

    private void setBothDoors(GameSession session, Exit exit, DoorState state) {
        exit.setDoor(state);
        Room destination = session.currentLevel().room(exit.getDestinationRoomId());
        Exit back = destination.getExits().get(exit.getDirection().opposite());
        if (back != null) {
            back.setDoor(state);
        }
    }

    /** Advances one dungeon turn: burns light and runs the wandering-monster check. */
    private void advanceTurn(SaveGame save, ExplorationResult result) {
        GameSession session = save.getSession();
        Character character = save.getCharacter();
        session.setDungeonTurn(session.getDungeonTurn() + 1);

        if (!session.isInDarkness()) {
            session.setTorchTurnsRemaining(session.getTorchTurnsRemaining() - 1);
            if (session.getTorchTurnsRemaining() <= 0) {
                if (character.getTorches() > 0) {
                    character.setTorches(character.getTorches() - 1);
                    session.setTorchTurnsRemaining(TORCH_TURNS);
                    result.add("_Your torch gutters out; you light another (" + character.getTorches() + " left)._");
                } else {
                    session.setInDarkness(true);
                    result.add("**Your last torch burns out — you are plunged into darkness!**");
                }
            }
        }

        // Wandering monster check every other turn (1-in-6).
        if (session.getDungeonTurn() % 2 == 0 && dice.d(6) == 1) {
            Room room = session.currentRoom();
            if (!room.hasLiveMonster()) {
                MonsterType type = pickWanderingMonster(session.getCurrentLevel() + 1);
                int count = Math.max(1, dice.d(Math.max(1, session.getCurrentLevel() + 2)));
                room.setContent(ContentType.MONSTER);
                room.setMonsterName(type.name());
                room.setMonsterCount(count);
                room.setCleared(false);
                result.add("**Wandering monster!** "
                        + count + " " + type.name().toLowerCase() + (count > 1 ? "s" : "") + " appear!");
            }
        }
    }

    private void maybeSpringTrap(SaveGame save, ExplorationResult result) {
        GameSession session = save.getSession();
        Character character = save.getCharacter();
        Room room = session.currentRoom();
        if (!room.isTrapped() || room.isTrapSprung() || room.isTrapDetected()) {
            return;
        }
        // B/X: a trap springs on a 1-2 in 6 when entered unawares.
        if (dice.d(6) <= 2) {
            room.setTrapSprung(true);
            int damage = room.getTrapDamage().roll(dice);
            int save_ = SavingThrows.forCharacter(character.getCharacterClass(), character.getLevel())
                    .paralysisPetrify();
            boolean saved = dice.d20() >= save_;
            if (saved) {
                damage = Math.max(1, damage / 2);
            }
            character.setCurrentHp(character.getCurrentHp() - damage);
            result.add("**A trap springs — " + room.getTrapDescription() + "!** You take " + damage
                    + " damage" + (saved ? " (saved for half)" : "") + ".");
            if (!character.isAlive()) {
                session.setState(SessionState.IN_TOWN);
                result.add("**" + character.getName() + " has died in the dungeon.**");
            }
        }
    }

    /** When a live monster shares the room, rolls reaction and starts combat if it is hostile. */
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
        if (combat.isHostileReaction(save.getCharacter(), type)) {
            result.getLines().addAll(combat.startCombat(save).getLines());
        } else {
            String plural = room.getMonsterName().toLowerCase() + (room.getMonsterCount() > 1 ? "s" : "");
            result.add("The " + plural + " watch you warily but do not attack yet. (`attack` to fight, or move on.)");
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
        sb.append("You are in ").append(room.getDescription()).append(".");

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
