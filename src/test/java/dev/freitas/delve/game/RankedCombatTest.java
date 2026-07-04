package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Formation;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.CombatEncounter;
import dev.freitas.delve.game.model.ContentType;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.DungeonLevel;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.MonsterType;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.SpellService;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RankedCombatTest {

    private final Dice dice = new Dice(new Random(25));
    private final CombatService combat = new CombatService(dice, new SpellService(dice));
    private final CharacterFactory characterFactory = new CharacterFactory(dice);
    private final RetainerFactory retainerFactory = new RetainerFactory(dice);

    @Test
    void singleFileFormationProtectsEveryoneButTheFront() {
        SaveGame save = rankedSave(Bestiary.ORC, 2, 1, 300);
        Retainer second = tankyRetainer("Bryn");
        Retainer third = tankyRetainer("Cora");
        save.getRetainers().add(second);
        save.getRetainers().add(third);
        save.setMarchingOrder(List.of(SaveGame.PLAYER_SLOT, "Bryn", "Cora"));

        startMeleeFight(save);
        for (int round = 0; round < 20 && save.getSession().getState() == SessionState.IN_COMBAT; round++) {
            combat.attackRound(save, null);
        }

        assertThat(second.getCurrentHp()).isEqualTo(20);
        assertThat(third.getCurrentHp()).isEqualTo(20);
    }

    @Test
    void columnExposesTheNextOccupantOnceItsFrontFalls() {
        SaveGame save = rankedSave(Bestiary.ORC, 1, 1, 50);
        Retainer front = tankyRetainer("Aldo");
        save.getRetainers().add(front);
        save.setMarchingOrder(List.of("Aldo", SaveGame.PLAYER_SLOT));

        startMeleeFight(save);
        for (int round = 0; round < 10 && save.getSession().getState() == SessionState.IN_COMBAT; round++) {
            combat.attackRound(save, null);
        }
        assertThat(save.getCharacter().getCurrentHp()).isEqualTo(50); // never targetable while Aldo fronts the column

        front.setCurrentHp(0);
        List<Combatant> fullOrder = save.fullOrder();
        assertThat(Formation.isEngaged(fullOrder, 1, save.getCharacter())).isTrue();
    }

    @Test
    void reachWeaponRetainerCanMeleeFromRankTwoPastALivingFrontRanker() {
        SaveGame save = rankedSave(Bestiary.ORC, 1, 1, 200);
        Retainer spearman = tankyRetainer("Doran");
        spearman.setMainWeapon("Spear");
        save.getRetainers().add(spearman);
        save.setMarchingOrder(List.of(SaveGame.PLAYER_SLOT, "Doran"));

        startMeleeFight(save);
        ExplorationResult result = combat.attackRound(save, null);

        assertThat(result.text()).contains("Doran");
        assertThat(result.text()).doesNotContain("can't reach the fight");
    }

    @Test
    void plainMeleeRetainerCannotActFromRankTwoWhileFrontIsAlive() {
        SaveGame save = rankedSave(Bestiary.ORC, 1, 1, 200);
        Retainer swordsman = tankyRetainer("Esna"); // default "Weapon" -> classifies as MELEE
        save.getRetainers().add(swordsman);
        save.setMarchingOrder(List.of(SaveGame.PLAYER_SLOT, "Esna"));

        startMeleeFight(save);
        ExplorationResult result = combat.attackRound(save, null);

        assertThat(result.text()).contains("Esna can't reach the fight from the second rank.");
    }

    @Test
    void missileRetainerFiresFromRankTwoPreMeleeAndNeverMeleesVoluntarily() {
        SaveGame save = rankedSave(Bestiary.ORC, 1, 1, 200);
        Retainer archer = tankyRetainer("Fendrel");
        archer.setMainWeapon("Short bow");
        save.getRetainers().add(archer);
        save.setMarchingOrder(List.of(SaveGame.PLAYER_SLOT, "Fendrel"));

        combat.startCombat(save);
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setPartySurprised(false);
        encounter.setMonstersSurprised(false);
        encounter.setDistanceFeet(50); // within a short bow's Short ceiling

        ExplorationResult rangedResult = combat.attackRound(save, null);
        assertThat(rangedResult.text()).contains("Fendrel fire");

        encounter.setDistanceFeet(0); // force melee range
        ExplorationResult meleeResult = combat.attackRound(save, null);
        assertThat(meleeResult.text()).contains("Fendrel can't reach the fight");
    }

    @Test
    void meleeRetainerWithASecondaryMissileWeaponFiresItFromRankTwoPreMelee() {
        SaveGame save = rankedSave(Bestiary.ORC, 1, 1, 200);
        Retainer fighter = tankyRetainer("Gwen"); // default Fighter kit: Sword main, Sling secondary
        save.getRetainers().add(fighter);
        save.setMarchingOrder(List.of(SaveGame.PLAYER_SLOT, "Gwen"));

        combat.startCombat(save);
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setPartySurprised(false);
        encounter.setMonstersSurprised(false);
        encounter.setDistanceFeet(50); // within the sling's Short ceiling

        ExplorationResult rangedResult = combat.attackRound(save, null);
        assertThat(rangedResult.text()).contains("Gwen fire");

        encounter.setDistanceFeet(0); // force melee range
        ExplorationResult meleeResult = combat.attackRound(save, null);
        assertThat(meleeResult.text()).contains("Gwen can't reach the fight");
    }

    // --- helpers -------------------------------------------------------------

    private void startMeleeFight(SaveGame save) {
        combat.startCombat(save);
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setPartySurprised(false);
        encounter.setMonstersSurprised(false);
        encounter.setDistanceFeet(0); // isolate rank/engagement behavior from surprise/range mechanics
    }

    private Retainer tankyRetainer(String name) {
        Retainer r = retainerFactory.create(name, CharacterClass.FIGHTER, 1, 12);
        r.setMaxHp(20);
        r.setCurrentHp(20);
        return r;
    }

    private SaveGame rankedSave(MonsterType type, int count, int corridorWidth, int heroHp) {
        Character hero = characterFactory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(16, 9, 9, 13, 13, 12));
        hero.setMaxHp(heroHp);
        hero.setCurrentHp(heroHp);

        SaveGame save = new SaveGame();
        save.setCharacter(hero);

        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room here = new Room(0);
        here.setCorridorWidth(corridorWidth);
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
