package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.CombatEncounter;
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
import dev.freitas.delve.game.session.SpellService;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SurpriseAndRangeTest {

    private final Dice dice = new Dice(new Random(26));
    private final CombatService combat = new CombatService(dice, new SpellService(dice));
    private final CharacterFactory factory = new CharacterFactory(dice);

    @Test
    void wanderingEncounterRollsTheClassicTwoDSixTimesTenDistance() {
        SaveGame save = combatSave(Bestiary.ORC, 1, 60);
        save.getSession().currentRoom().setFreshEncounter(true);
        combat.startCombat(save);

        int distance = save.getSession().getCombat().getDistanceFeet();
        assertThat(distance).isBetween(20, 120);
        assertThat(distance % 10).isEqualTo(0);
        assertThat(save.getSession().currentRoom().isFreshEncounter()).isFalse(); // consumed
    }

    @Test
    void roomBasedEncounterDefaultsToThirtyFeetWithoutAMissileWeapon() {
        SaveGame save = combatSave(Bestiary.ORC, 1, 60); // Fighter's sword isn't a missile weapon
        combat.startCombat(save);
        assertThat(save.getSession().getCombat().getDistanceFeet()).isEqualTo(30);
    }

    @Test
    void roomBasedEncounterUsesTheMissileWeaponsShortRangeCeiling() {
        SaveGame save = combatSave(Bestiary.ORC, 1, 60);
        save.getCharacter().setMainWeapon("Short bow");
        combat.startCombat(save);
        assertThat(save.getSession().getCombat().getDistanceFeet()).isEqualTo(50);
    }

    @Test
    void distanceClosesByTheMonstersMoveRateEachRound() {
        SaveGame save = combatSave(Bestiary.ORC, 1, 60); // Orc moveRate 60 -> closes 20/round
        combat.startCombat(save);
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setPartySurprised(false);
        encounter.setMonstersSurprised(false);
        int start = encounter.getDistanceFeet(); // 30 (room-based, no missile weapon)

        combat.attackRound(save, null);
        assertThat(encounter.getDistanceFeet()).isEqualTo(Math.max(0, start - 20));
    }

    @Test
    void surprisedPartySkipsOnlyTheFirstRoundsAction() {
        SaveGame save = combatSave(Bestiary.GIANT_RAT, 1, 60);
        combat.startCombat(save);
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setDistanceFeet(0);
        encounter.setPartySurprised(true);
        int hpBefore = encounter.aliveMonsters().get(0).getCurrentHp();

        combat.attackRound(save, null); // round 1: surprised, no party action
        assertThat(encounter.aliveMonsters()).isNotEmpty();
        assertThat(encounter.aliveMonsters().get(0).getCurrentHp()).isEqualTo(hpBefore);
        assertThat(encounter.isPartySurprised()).isFalse(); // cleared after round 1
    }

    @Test
    void surprisedMonstersSkipOnlyTheFirstRoundsAttack() {
        SaveGame save = combatSave(Bestiary.ORC, 3, 60);
        Character hero = save.getCharacter();
        combat.startCombat(save);
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setDistanceFeet(0);
        encounter.setPartySurprised(false);
        encounter.setMonstersSurprised(true);
        int hpBefore = hero.getCurrentHp();

        combat.attackRound(save, null); // round 1: monsters surprised, no attack on the party
        assertThat(hero.getCurrentHp()).isEqualTo(hpBefore);
        assertThat(encounter.isMonstersSurprised()).isFalse();
    }

    private SaveGame combatSave(MonsterType type, int count, int heroHp) {
        Character hero = factory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(16, 9, 9, 13, 13, 12));
        hero.setMaxHp(heroHp);
        hero.setCurrentHp(heroHp);

        SaveGame save = new SaveGame();
        save.setCharacter(hero);

        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room here = new Room(0);
        here.setContent(ContentType.MONSTER);
        here.setMonsterName(type.name());
        here.setMonsterCount(count);
        Room next = new Room(1);
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
