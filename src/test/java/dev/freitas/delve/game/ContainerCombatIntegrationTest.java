package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.ContainerType;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Container;
import dev.freitas.delve.game.model.ContentType;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.DungeonLevel;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.MonsterType;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.SpellService;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** End-to-end: a held sack dropping when a two-handed weapon leaves no free hand for it, restored to
    the party on victory, and lost for good on a successful flee -- {@link CombatService} wired to
    {@code ContainerService} via {@link CombatService#startCombat}/{@code victory}/{@code flee}. */
class ContainerCombatIntegrationTest {

    @Test
    void heldSackDropsAtCombatStartAndReturnsOnVictory() {
        Dice dice = new Dice(new Random(13)); // same seed CombatTest relies on for a reliable win
        CombatService combat = new CombatService(dice, new SpellService(dice));
        CharacterFactory factory = new CharacterFactory(dice);
        Character hero = factory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(16, 9, 9, 13, 13, 12));
        hero.setMaxHp(60);
        hero.setCurrentHp(60);
        hero.setMainWeapon("Halberd"); // two-handed: 0 hands free even without a shield
        hero.setShield(false);
        Container heldSack = new Container(ContainerType.SMALL_SACK, true, hero.getName());
        heldSack.getItems().add("Gems");
        hero.getContainers().add(heldSack);

        SaveGame save = combatSave(hero, Bestiary.GIANT_RAT, 1);
        combat.startCombat(save);

        assertThat(hero.getContainers()).doesNotContain(heldSack);
        assertThat(save.getSession().getCombat().getDroppedContainers()).contains(heldSack);

        for (int round = 0; round < 300 && save.getSession().getState() == SessionState.IN_COMBAT; round++) {
            combat.attackRound(save, null);
        }

        assertThat(save.getSession().getState()).isEqualTo(SessionState.EXPLORING);
        assertThat(hero.getContainers()).contains(heldSack); // won -- picked back up
    }

    @Test
    void heldSackIsLostForGoodWhenThePartyFleesSuccessfully() {
        Dice dice = new Dice(new Random(0));
        CombatService combat = new CombatService(dice, new SpellService(dice));
        Character hero = new Character();
        hero.setName("Hero");
        hero.setCharacterClass(CharacterClass.FIGHTER);
        hero.setAbilities(new AbilityScores(9, 9, 9, 9, 9, 9));
        hero.setArmor(Armor.NONE);
        hero.setShield(false);
        hero.setMainWeapon("Halberd"); // two-handed: 0 hands free
        hero.setTorches(0);
        hero.setGold(0);
        hero.setMaxHp(200);
        hero.setCurrentHp(200);
        Container heldSack = new Container(ContainerType.SMALL_SACK, true, hero.getName());
        heldSack.getItems().add("Gems");
        hero.getContainers().add(heldSack);
        // Total carried weight (halberd 150 + empty sack 1) stays under 400cn -- movement rate 120,
        // encounter rate 40 -- always faster than a Skeleton (moveRate 60 -> encounter rate 20), so
        // evasion is guaranteed (mirrors EvasionTest#fasterPartyAlwaysEscapes).
        SaveGame save = combatSave(hero, Bestiary.SKELETON, 1);

        combat.startCombat(save);
        assertThat(hero.getContainers()).isEmpty(); // dropped -- 0 free hands

        ExplorationResult result = combat.flee(save);

        assertThat(save.getSession().getState()).isEqualTo(SessionState.EXPLORING);
        assertThat(hero.getContainers()).isEmpty(); // not returned -- gone for good
        assertThat(result.text()).contains("Gems").contains("small sack");
    }

    private SaveGame combatSave(Character hero, MonsterType type, int count) {
        SaveGame save = new SaveGame();
        save.setCharacter(hero);

        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room here = new Room(0);
        here.setDescription("a fighting pit");
        here.setContent(ContentType.MONSTER);
        here.setMonsterName(type.name());
        here.setMonsterCount(count);
        Room next = new Room(1);
        next.setDescription("an antechamber");
        here.getExits().put(Direction.EAST, new Exit(Direction.EAST, 1, DoorState.NONE, false));
        next.getExits().put(Direction.WEST, new Exit(Direction.WEST, 0, DoorState.NONE, false));
        level.addRoom(here);
        level.addRoom(next);
        level.setEntranceRoomId(0);
        dungeon.addLevel(level);

        save.getSession().setDungeon(dungeon);
        save.getSession().setCurrentLevel(0);
        save.getSession().setCurrentRoomId(0);
        save.getSession().setState(SessionState.EXPLORING);
        return save;
    }
}
