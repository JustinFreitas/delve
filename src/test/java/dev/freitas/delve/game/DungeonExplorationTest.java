package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.ContentType;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.DungeonLevel;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.GameSession;
import dev.freitas.delve.game.model.MonsterDisposition;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.SpellService;
import dev.freitas.delve.game.session.LightingService;
import dev.freitas.delve.game.session.MuleService;
import dev.freitas.delve.game.session.ExplorationService;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DungeonExplorationTest {

    // --- Generator ---------------------------------------------------------

    @Test
    void everyRoomIsReachableFromTheEntrance() {
        for (long seed = 1; seed <= 25; seed++) {
            DungeonGenerator generator = new DungeonGenerator(new Dice(new Random(seed)));
            Dungeon dungeon = generator.generate(3, 10);
            for (DungeonLevel level : dungeon.getLevels()) {
                assertThat(reachable(level)).as("seed %d depth %d", seed, level.getDepth())
                        .hasSize(level.getRooms().size());
            }
        }
    }

    @Test
    void treasureIsSometimesTrappedButNotAlwaysOrNever() {
        int trapped = 0;
        int untrapped = 0;
        for (long seed = 1; seed <= 60; seed++) {
            DungeonGenerator generator = new DungeonGenerator(new Dice(new Random(seed)));
            Dungeon dungeon = generator.generate(3, 10);
            for (DungeonLevel level : dungeon.getLevels()) {
                for (Room room : level.getRooms().values()) {
                    if (!room.isHasTreasure()) {
                        continue;
                    }
                    if (room.isTreasureTrapped()) {
                        trapped++;
                    } else {
                        untrapped++;
                    }
                }
            }
        }
        assertThat(trapped).isGreaterThan(0); // some treasure is trapped
        assertThat(untrapped).isGreaterThan(0); // most is not
    }

    @Test
    void stairsLinkAdjacentLevels() {
        DungeonGenerator generator = new DungeonGenerator(new Dice(new Random(99)));
        Dungeon dungeon = generator.generate(3, 10);
        assertThat(dungeon.levelCount()).isEqualTo(3);

        DungeonLevel level0 = dungeon.level(0);
        Room down = level0.getRooms().values().stream()
                .filter(Room::isStairsDown)
                .findFirst()
                .orElseThrow();
        assertThat(down.getStairsDestinationLevel()).isEqualTo(1);

        Room up = dungeon.level(1).room(down.getStairsDestinationRoomId());
        assertThat(up.isStairsUp()).isTrue();
        assertThat(up.getStairsDestinationRoomId()).isEqualTo(down.getId());
    }

    /** BFS over known exits (door state is openable, so it doesn't affect reachability). */
    private Set<Integer> reachable(DungeonLevel level) {
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(level.getEntranceRoomId());
        seen.add(level.getEntranceRoomId());
        while (!queue.isEmpty()) {
            Room room = level.room(queue.poll());
            for (Exit exit : room.getExits().values()) {
                if (exit.isKnown() && seen.add(exit.getDestinationRoomId())) {
                    queue.add(exit.getDestinationRoomId());
                }
            }
        }
        return seen;
    }

    // --- Exploration service ----------------------------------------------

    @Test
    void enterStartsTheDelveAndLightsATorch() {
        Dice dice = new Dice(new Random(3));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
        SaveGame save = newSaveWithCharacter(6);

        var result = service.enter(save);

        assertThat(save.getSession().getState()).isEqualTo(SessionState.EXPLORING);
        assertThat(save.getSession().isInDungeon()).isTrue();
        assertThat(save.getCharacter().getTorches()).isEqualTo(5); // one lit on entry
        assertThat(save.getSession().currentRoom().isVisited()).isTrue();
        assertThat(result.text()).isNotBlank();
    }

    @Test
    void movingAdvancesDungeonTurnsAndBurnsLight() {
        Dice dice = new Dice(new Random(11));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
        SaveGame save = twoRoomSave(dice, 0); // no spare torches; current torch has 6 turns

        // Alternate east/west between the two rooms. Starting in room 0, even steps go east, odd west,
        // so each move is always valid. Neutralize any wandering monster that appears so it does not
        // start combat and block the next move — we are only measuring turns and light here.
        for (int i = 0; i < 6; i++) {
            if (i == 5) {
                // After five moves the torch still has one turn of light left.
                assertThat(save.getSession().getDungeonTurn()).isEqualTo(5);
                assertThat(save.getSession().isInDarkness()).isFalse();
            }
            service.move(save, (i % 2 == 0) ? Direction.EAST : Direction.WEST);
            save.getSession().setState(SessionState.EXPLORING);
            save.getSession().setCombat(null);
            save.getSession().currentRoom().setCleared(true);
        }

        // The sixth turn exhausts the torch with no spare -> darkness.
        assertThat(save.getSession().getDungeonTurn()).isEqualTo(6);
        assertThat(save.getSession().isInDarkness()).isTrue();
    }

    @Test
    void searchGathersTreasureAndCanRevealSecretDoors() {
        Dice dice = new Dice(new Random(5));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
        SaveGame save = twoRoomSave(dice, 3);
        Room room = save.getSession().currentRoom();
        room.setHasTreasure(true);
        room.setTreasureGold(120);
        // Add a hidden secret door.
        Exit secret = new Exit(Direction.NORTH, 1, DoorState.CLOSED, true);
        room.getExits().put(Direction.NORTH, secret);

        int goldBefore = save.getCharacter().getGold();
        service.search(save);
        assertThat(save.getCharacter().getGold()).isEqualTo(goldBefore + 120);
        assertThat(room.isLooted()).isTrue();

        // Keep searching until the 1-in-6 reveals the secret door (proves the mechanic fires).
        for (int i = 0; i < 100 && !secret.isRevealed(); i++) {
            service.search(save);
        }
        assertThat(secret.isRevealed()).isTrue();
    }

    @Test
    void treasureSplitsAcrossLivingRetainersAndReducesThePcsShare() {
        Dice dice = new Dice(new Random(6));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
        SaveGame save = twoRoomSave(dice, 3);
        save.getRetainers().add(new RetainerFactory(dice).create("Bryn", CharacterClass.FIGHTER, 1, 9));
        Room room = save.getSession().currentRoom();
        room.setHasTreasure(true);
        room.setTreasureGold(120); // 1 PC + 1 retainer -> 2 shares of 60 each

        int goldBefore = save.getCharacter().getGold();
        int xpBefore = save.getCharacter().getXp();
        int retainerXpBefore = save.getRetainers().get(0).getXp();
        service.search(save);

        assertThat(save.getCharacter().getGold()).isEqualTo(goldBefore + 60); // PC's share only, not the full 120
        // XP follows the PC's own share (60), not the full 120 gp — allow for the class's prime-requisite
        // XP bonus/penalty (Leveling.awardXp), so assert the range rather than an exact number.
        assertThat(save.getCharacter().getXp()).isBetween(xpBefore + 50, xpBefore + 70);
        assertThat(save.getRetainers().get(0).getXp()).isGreaterThan(retainerXpBefore); // retainer earns XP too
    }

    @Test
    void delveCountIncrementsEachTimeADelveBegins() {
        Dice dice = new Dice(new Random(10));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
        SaveGame save = newSaveWithCharacter(6);
        assertThat(save.getCharacter().getDelveCount()).isZero();

        service.enter(save);
        assertThat(save.getCharacter().getDelveCount()).isEqualTo(1);

        service.enter(save); // re-entering (e.g. after town) counts as another delve
        assertThat(save.getCharacter().getDelveCount()).isEqualTo(2);
    }

    @Test
    void treasureChanceAndValueAreBumpedAboveTheOldBaseline() {
        // Old baseline: 3/2/1-in-6 chance by content type, value 2d6*10*depth (avg 70 at depth 1).
        // New: 4/3/2-in-6 chance, value 2d6*13*depth (avg ~91 at depth 1) — confirm both moved up.
        int roomsWithTreasure = 0;
        int totalRooms = 0;
        long goldSum = 0;
        int depth1TreasureRooms = 0;
        for (long seed = 1; seed <= 40; seed++) {
            DungeonGenerator generator = new DungeonGenerator(new Dice(new Random(seed)));
            Dungeon dungeon = generator.generate(1, 10);
            for (Room room : dungeon.level(0).getRooms().values()) {
                totalRooms++;
                if (room.isHasTreasure()) {
                    roomsWithTreasure++;
                    depth1TreasureRooms++;
                    goldSum += room.getTreasureGold();
                }
            }
        }
        double treasureRate = (double) roomsWithTreasure / totalRooms;
        double avgGold = (double) goldSum / depth1TreasureRooms;
        assertThat(treasureRate).isGreaterThan(0.25); // old baseline landed noticeably lower than this
        assertThat(avgGold).isGreaterThan(70.0); // old baseline's depth-1 average
    }

    @Test
    void trapsSpringSometimesAndCanBeAvoidedWhenDetected() {
        Dice dice = new Dice(new Random(8));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());

        int sprang = 0;
        for (int trial = 0; trial < 200; trial++) {
            SaveGame save = twoRoomSave(dice, 9);
            Room target = save.getSession().currentLevel().room(1);
            target.setTrapped(true);
            target.setTrapDescription("a concealed pit");
            target.setTrapDamage(new DamageRoll(1, 6));
            target.setVisited(false);
            int hpBefore = save.getCharacter().getCurrentHp();

            service.move(save, Direction.EAST);
            if (save.getCharacter().getCurrentHp() < hpBefore) {
                sprang++;
            }
        }
        assertThat(sprang).isGreaterThan(0); // traps do trigger
        assertThat(sprang).isLessThan(200); // ...but not every time

        // A detected trap is stepped around: no damage.
        SaveGame safe = twoRoomSave(dice, 9);
        Room target = safe.getSession().currentLevel().room(1);
        target.setTrapped(true);
        target.setTrapDetected(true);
        target.setTrapDamage(new DamageRoll(1, 6));
        target.setVisited(false);
        int hpBefore = safe.getCharacter().getCurrentHp();
        safe.getSession().setCurrentRoomId(0);
        for (int i = 0; i < 20; i++) {
            safe.getSession().currentLevel().room(1).setVisited(false);
            safe.getSession().setCurrentRoomId(0);
            service.move(safe, Direction.EAST);
        }
        assertThat(safe.getCharacter().getCurrentHp()).isEqualTo(hpBefore);
    }

    @Test
    void listeningIsOneAttemptPerDoorAndCostsNoTurn() {
        Dice dice = new Dice(new Random(21));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
        SaveGame save = twoRoomSave(dice, 3);
        Exit exit = save.getSession().currentRoom().getExits().get(Direction.EAST);
        assertThat(exit.isListened()).isFalse();

        service.listen(save, Direction.EAST);
        assertThat(exit.isListened()).isTrue();
        assertThat(save.getSession().getDungeonTurn()).isZero(); // listening doesn't cost a turn

        var second = service.listen(save, Direction.EAST);
        assertThat(second.text()).contains("already listened");
    }

    @Test
    void listeningSometimesDetectsAMonsterBeyondADoor() {
        Dice dice = new Dice(new Random(22));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
        int heard = 0;
        for (int trial = 0; trial < 200; trial++) {
            SaveGame save = twoRoomSave(dice, 3);
            Room destination = save.getSession().currentLevel().room(1);
            destination.setContent(ContentType.MONSTER);
            destination.setMonsterName("Goblin");
            destination.setMonsterCount(1);
            var result = service.listen(save, Direction.EAST);
            if (result.text().contains("hear something moving")) {
                heard++;
            }
        }
        assertThat(heard).isGreaterThan(0); // sometimes detected...
        assertThat(heard).isLessThan(200); // ...but not always
    }

    @Test
    void restingResetsTheFatigueClock() {
        Dice dice = new Dice(new Random(23));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
        SaveGame save = twoRoomSave(dice, 9);

        for (int i = 0; i < 6; i++) {
            service.move(save, (i % 2 == 0) ? Direction.EAST : Direction.WEST);
            save.getSession().setState(SessionState.EXPLORING);
            save.getSession().setCombat(null);
            save.getSession().currentRoom().setCleared(true);
        }
        assertThat(save.getSession().getTurnsSinceRest()).isEqualTo(6);
        assertThat(save.getSession().isFatigued()).isTrue();

        service.rest(save);
        save.getSession().setState(SessionState.EXPLORING);
        save.getSession().setCombat(null);
        assertThat(save.getSession().getTurnsSinceRest()).isZero();
        assertThat(save.getSession().isFatigued()).isFalse();
    }

    @Test
    void largerPartiesFaceMoreFrequentWanderingMonsterChecks() {
        int soloTriggers = 0;
        int largePartyTriggers = 0;
        for (long seed = 1; seed <= 15; seed++) {
            soloTriggers += countWanderingTriggers(seed, 0);
            largePartyTriggers += countWanderingTriggers(seed, 9); // party size 10 -> +1 extra roll
        }
        assertThat(largePartyTriggers).isGreaterThan(soloTriggers);
    }

    /** Shuttles between the two test rooms, counting how many moves trigger a wandering monster. */
    private int countWanderingTriggers(long seed, int extraRetainers) {
        Dice dice = new Dice(new Random(seed));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
        SaveGame save = twoRoomSave(dice, 999); // plenty of spare torches so light never runs out
        for (int i = 1; i <= extraRetainers; i++) {
            save.getRetainers().add(new RetainerFactory(dice).create("R" + i, CharacterClass.FIGHTER, 1, 9));
        }
        int triggers = 0;
        for (int i = 0; i < 60; i++) {
            var result = service.move(save, (i % 2 == 0) ? Direction.EAST : Direction.WEST);
            if (result.text().contains("Wandering monster!")) {
                triggers++;
            }
            save.getSession().setState(SessionState.EXPLORING);
            save.getSession().setCombat(null);
            save.getSession().currentRoom().setCleared(true);
        }
        return triggers;
    }

    // --- Reaction override --------------------------------------------------

    @Test
    void scriptedHostileDispositionStartsCombat() {
        Dice dice = new Dice(new Random(41));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
        SaveGame save = twoRoomSave(dice, 9);
        Room target = save.getSession().currentLevel().room(1);
        target.setContent(ContentType.MONSTER);
        target.setMonsterName("Goblin");
        target.setMonsterCount(1);
        target.setScriptedDisposition(MonsterDisposition.HOSTILE);

        service.move(save, Direction.EAST);
        assertThat(save.getSession().getState()).isEqualTo(SessionState.IN_COMBAT);
    }

    @Test
    void scriptedFriendlyDispositionOverridesEvenTheUndeadAlwaysHostileRule() {
        Dice dice = new Dice(new Random(42));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
        for (int trial = 0; trial < 30; trial++) {
            SaveGame save = twoRoomSave(dice, 9);
            Room target = save.getSession().currentLevel().room(1);
            target.setContent(ContentType.MONSTER);
            target.setMonsterName("Skeleton"); // normally always hostile as undead
            target.setMonsterCount(1);
            target.setScriptedDisposition(MonsterDisposition.FRIENDLY);

            service.move(save, Direction.EAST);
            assertThat(save.getSession().getState()).isNotEqualTo(SessionState.IN_COMBAT);
        }
    }

    @Test
    void unscriptedRoomStillRollsReactionAsBefore() {
        Dice dice = new Dice(new Random(43));
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), new CombatService(dice, new SpellService(dice)), new LightingService(), new MuleService());
        SaveGame save = twoRoomSave(dice, 9);
        Room target = save.getSession().currentLevel().room(1);
        target.setContent(ContentType.MONSTER);
        target.setMonsterName("Skeleton"); // undead: always hostile via the ordinary (unscripted) path
        target.setMonsterCount(1);
        assertThat(target.getScriptedDisposition()).isNull();

        service.move(save, Direction.EAST);
        assertThat(save.getSession().getState()).isEqualTo(SessionState.IN_COMBAT);
    }

    // --- helpers -----------------------------------------------------------

    private SaveGame newSaveWithCharacter(int torches) {
        Character c = new CharacterFactory(new Dice(new Random(1)))
                .create("Tester", CharacterClass.FIGHTER, new AbilityScores(13, 9, 9, 12, 14, 9));
        c.setTorches(torches);
        c.setShield(false); // a solo Fighter needs a free hand to carry their own torch
        SaveGame save = new SaveGame();
        save.setCharacter(c);
        return save;
    }

    /** A deterministic 2-room level (rooms 0 and 1) joined by open passages both ways. */
    private SaveGame twoRoomSave(Dice dice, int torches) {
        SaveGame save = newSaveWithCharacter(torches);
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
