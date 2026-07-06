package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.DungeonLevel;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.GameSession;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.LightingService;
import dev.freitas.delve.game.session.MuleService;
import dev.freitas.delve.game.session.ExplorationService;
import dev.freitas.delve.game.session.SpellService;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PassiveSenseTest {

    @Test
    void dwarfAutoDetectsARoomTrapWithoutSearching() {
        Dice dice = new Dice(new Random(38));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, CharacterClass.DWARF);
        Room target = save.getSession().currentLevel().room(1);
        target.setTrapped(true);
        target.setTrapDescription("a concealed pit");
        target.setTrapDamage(new DamageRoll(1, 6));
        int hpBefore = save.getCharacter().getCurrentHp();

        for (int i = 0; i < 300 && !target.isTrapDetected(); i++) {
            target.setVisited(false);
            save.getSession().setCurrentRoomId(0);
            service.move(save, Direction.EAST);
        }
        assertThat(target.isTrapDetected()).isTrue();
        assertThat(save.getCharacter().getCurrentHp()).isEqualTo(hpBefore);
    }

    @Test
    void nonDwarfNeverGetsThePassiveTrapRoll() {
        Dice dice = new Dice(new Random(39));
        ExplorationService service = newService(dice);
        int detectedWithoutSearching = 0;
        for (int trial = 0; trial < 100; trial++) {
            SaveGame save = twoRoomSave(dice, CharacterClass.FIGHTER);
            Room target = save.getSession().currentLevel().room(1);
            target.setTrapped(true);
            target.setTrapDescription("a concealed pit");
            target.setTrapDamage(new DamageRoll(1, 6));
            service.move(save, Direction.EAST); // no /search — only a passive sense could detect it
            if (target.isTrapDetected() && !target.isTrapSprung()) {
                detectedWithoutSearching++;
            }
        }
        assertThat(detectedWithoutSearching).isZero();
    }

    @Test
    void elfAutoRevealsASecretDoorOnRoomEntryWithoutSearching() {
        Dice dice = new Dice(new Random(40));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, CharacterClass.ELF);
        Room target = save.getSession().currentLevel().room(1);
        Exit secret = new Exit(Direction.NORTH, 0, DoorState.CLOSED, true);
        target.getExits().put(Direction.NORTH, secret);

        for (int i = 0; i < 300 && !secret.isRevealed(); i++) {
            target.setVisited(false);
            save.getSession().setCurrentRoomId(0);
            service.move(save, Direction.EAST);
        }
        assertThat(secret.isRevealed()).isTrue();
    }

    @Test
    void dwarfPassiveSenseNeverAppliesToTreasureTraps() {
        Dice dice = new Dice(new Random(44));
        ExplorationService service = newService(dice);
        SaveGame save = twoRoomSave(dice, CharacterClass.DWARF);
        Room target = save.getSession().currentLevel().room(1);
        target.setHasTreasure(true);
        target.setTreasureGold(50);
        target.setTreasureTrapped(true);
        target.setTreasureTrapDescription("a poisoned needle");
        target.setTreasureTrapDamage(new DamageRoll(1, 4));

        for (int i = 0; i < 50; i++) {
            target.setVisited(false);
            save.getSession().setCurrentRoomId(0);
            service.move(save, Direction.EAST);
        }
        // Merely walking in/out (no /search) never touches the treasure trap at all.
        assertThat(target.isTreasureTrapDisarmed()).isFalse();
        assertThat(target.isLooted()).isFalse();
    }

    private ExplorationService newService(Dice dice) {
        return new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
    }

    private SaveGame twoRoomSave(Dice dice, CharacterClass characterClass) {
        Character c = new CharacterFactory(dice)
                .create("Tester", characterClass, new AbilityScores(13, 9, 9, 12, 14, 9));
        c.setTorches(9);
        SaveGame save = new SaveGame();
        save.setCharacter(c);
        GameSession session = save.getSession();

        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room a = new Room(0);
        a.setDescription("a test chamber");
        Room b = new Room(1);
        b.setDescription("another test chamber");
        a.getExits().put(Direction.EAST, new Exit(Direction.EAST, 1, DoorState.NONE, false));
        b.getExits().put(Direction.WEST, new Exit(Direction.WEST, 0, DoorState.NONE, false));
        level.addRoom(a);
        level.addRoom(b);
        level.setEntranceRoomId(0);
        dungeon.addLevel(level);

        session.setDungeon(dungeon);
        session.setCurrentLevel(0);
        session.setCurrentRoomId(0);
        session.setState(SessionState.EXPLORING);
        session.setLightTurnsRemaining(6);
        session.setActiveLight(dev.freitas.delve.game.engine.LightSource.TORCH);
        session.setLightBearer(SaveGame.PLAYER_SLOT);
        return save;
    }
}
