package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.Bestiary;
import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.dungeon.ModuleLoader;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.ContentType;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.DungeonLevel;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.ExplorationService;
import dev.freitas.delve.game.session.SpellService;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleTest {

    @Test
    void sampleModuleListsAndLoads() {
        assertThat(ModuleLoader.listModules()).contains("sample");
        assertThat(ModuleLoader.exists("sample")).isTrue();
        assertThat(ModuleLoader.load("does-not-exist")).isNull();
    }

    @Test
    void loadedModuleMapsToAFaithfulReachableDungeon() {
        ModuleLoader.LoadedModule loaded = ModuleLoader.load("sample");
        assertThat(loaded).isNotNull();
        assertThat(loaded.title()).isEqualTo("The Sunken Antechamber");

        Dungeon dungeon = loaded.dungeon();
        assertThat(dungeon.levelCount()).isEqualTo(1);
        DungeonLevel level = dungeon.level(0);
        assertThat(level.getEntranceRoomId()).isEqualTo(1);
        assertThat(level.getRooms()).hasSize(4);

        // Read-aloud and keyed name carried through.
        Room entrance = level.room(1);
        assertThat(entrance.getName()).contains("Collapsed Antechamber");
        assertThat(entrance.getReadAloud()).contains("Fallen masonry");

        // Monster resolved against the Bestiary so combat can look it up.
        Room guardPost = level.room(2);
        assertThat(guardPost.hasLiveMonster()).isTrue();
        assertThat(Bestiary.byName(guardPost.getMonsterName())).isEqualTo(Bestiary.GOBLIN);
        assertThat(guardPost.isHasTreasure()).isTrue();
        assertThat(guardPost.getTreasureGold()).isEqualTo(80);

        // Trap and special content mapped.
        assertThat(level.room(3).isTrapped()).isTrue();
        assertThat(level.room(4).getContent()).isEqualTo(ContentType.SPECIAL);
        assertThat(level.room(4).getTreasureGold()).isEqualTo(200);

        // Room 2 keyed an exit north->4, but room 4 keyed none back: the loader adds the return path.
        Exit backToTwo = level.room(4).getExits().get(Direction.SOUTH);
        assertThat(backToTwo).isNotNull();
        assertThat(backToTwo.getDestinationRoomId()).isEqualTo(2);

        // Every room reachable from the entrance over known exits.
        assertThat(reachable(level)).hasSize(4);
    }

    @Test
    void enterModuleInstallsTheDungeonAndDescribesTheEntrance() {
        Dice dice = new Dice(new Random(7));
        SpellService spells = new SpellService(dice);
        CombatService combat = new CombatService(dice, spells);
        ExplorationService service = new ExplorationService(dice, new DungeonGenerator(dice), combat);

        SaveGame save = new SaveGame();
        Character hero = new CharacterFactory(dice)
                .create("Tester", CharacterClass.FIGHTER, new AbilityScores(13, 9, 9, 12, 13, 9));
        save.setCharacter(hero);

        ModuleLoader.LoadedModule loaded = ModuleLoader.load("sample");
        var result = service.enterModule(save, loaded.dungeon(), loaded.title());

        assertThat(save.getSession().getState()).isEqualTo(SessionState.EXPLORING);
        assertThat(save.getSession().getCurrentRoomId()).isEqualTo(1);
        assertThat(result.text()).contains("The Sunken Antechamber");
        assertThat(result.text()).contains("Collapsed Antechamber");
    }

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
}
