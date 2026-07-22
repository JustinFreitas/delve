package dev.freitas.delve.game.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.CharacterFactory;
import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.ContentType;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.DungeonLevel;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.Mule;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ExplorationServiceTest {

    private final Dice dice = new Dice(new Random(1));
    private final ExplorationService service = new ExplorationService(
            dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
    private final CharacterFactory factory = new CharacterFactory(dice);

    @Test
    void partySizeCountsEveryLivingPcAndRetainerNotJustOne() {
        SaveGame save = new SaveGame();
        save.setCharacter(factory.create("First", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9)));
        save.addCharacter(factory.create("Second", CharacterClass.CLERIC, new AbilityScores(9, 9, 9, 9, 9, 9)));
        Character deadPc = factory.create("Dead", CharacterClass.THIEF, new AbilityScores(9, 9, 9, 9, 9, 9));
        deadPc.setCurrentHp(0);
        save.addCharacter(deadPc); // dead -- must not count

        Retainer aliveRetainer = new Retainer();
        aliveRetainer.setName("Alive");
        aliveRetainer.setCharacterClass(CharacterClass.FIGHTER);
        aliveRetainer.setMaxHp(10);
        aliveRetainer.setCurrentHp(10);
        save.getRetainers().add(aliveRetainer);
        Retainer deadRetainer = new Retainer();
        deadRetainer.setName("Fallen");
        deadRetainer.setCharacterClass(CharacterClass.FIGHTER);
        deadRetainer.setMaxHp(10);
        deadRetainer.setCurrentHp(0);
        save.getRetainers().add(deadRetainer); // dead -- must not count

        // The old (buggy) formula `1 + livingRetainers().size()` would have said 2 here (assuming only
        // one PC); the correct count is 2 living PCs + 1 living retainer.
        assertThat(service.partySize(save)).isEqualTo(3);
    }

    @Test
    void partySizeCountsALivingMuleButNotADeadOne() {
        SaveGame save = new SaveGame();
        save.setCharacter(factory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9)));
        assertThat(service.partySize(save)).isEqualTo(1);

        Mule mule = new Mule();
        mule.setName("Mule");
        mule.setMaxHp(9);
        mule.setCurrentHp(9);
        save.getMules().add(mule);

        assertThat(service.partySize(save)).isEqualTo(2);

        Mule deadMule = new Mule();
        deadMule.setName("Fallen");
        deadMule.setMaxHp(9);
        deadMule.setCurrentHp(0);
        save.getMules().add(deadMule); // dead -- must not count

        assertThat(service.partySize(save)).isEqualTo(2);
    }

    @Test
    void aLockedDoorIsRefusedOnlyWhenNoThiefIsAnywhereInTheParty() {
        // Fighter primary + Cleric second: no thief anywhere -> refused, door untouched.
        SaveGame noThief = lockedRoomParty(CharacterClass.FIGHTER, CharacterClass.CLERIC);
        ExplorationResult refused = service.open(noThief, Direction.EAST);
        assertThat(String.join(" ", refused.getLines())).contains("thief");
        assertThat(doorEast(noThief)).isEqualTo(DoorState.LOCKED);

        // Fighter primary + Thief *second* PC: the party attempts the pick (using the thief), rather than
        // being refused just because the first-rolled PC isn't a thief. This is the multi-PC parity fix.
        SaveGame withThief = lockedRoomParty(CharacterClass.FIGHTER, CharacterClass.THIEF);
        ExplorationResult attempted = service.open(withThief, Direction.EAST);
        assertThat(String.join(" ", attempted.getLines())).doesNotContain("need a key");
    }

    /** Two PCs (the given classes) standing in a room whose east door is LOCKED. */
    private SaveGame lockedRoomParty(CharacterClass primary, CharacterClass second) {
        SaveGame save = new SaveGame();
        save.setCharacter(factory.create("Bram", primary, new AbilityScores(12, 9, 9, 9, 9, 9)));
        Character secondPc = factory.create("Sly", second, new AbilityScores(9, 9, 9, 9, 9, 9));
        secondPc.setLevel(10); // a capable thief, when it is one
        save.addCharacter(secondPc);

        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room here = new Room(0);
        here.setContent(ContentType.EMPTY);
        Room beyond = new Room(1);
        here.getExits().put(Direction.EAST, new Exit(Direction.EAST, 1, DoorState.LOCKED, false));
        beyond.getExits().put(Direction.WEST, new Exit(Direction.WEST, 0, DoorState.LOCKED, false));
        level.addRoom(here);
        level.addRoom(beyond);
        level.setEntranceRoomId(0);
        dungeon.addLevel(level);

        save.getSession().setDungeon(dungeon);
        save.getSession().setCurrentLevel(0);
        save.getSession().setCurrentRoomId(0);
        save.getSession().setState(SessionState.EXPLORING);
        return save;
    }

    private DoorState doorEast(SaveGame save) {
        return save.getSession().currentRoom().getExits().get(Direction.EAST).getDoor();
    }
}
