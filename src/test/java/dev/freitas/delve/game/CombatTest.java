package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Leveling;
import dev.freitas.delve.game.model.Character;
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

class CombatTest {

    private final Dice dice = new Dice(new Random(13));
    private final CombatService combat = new CombatService(dice, new SpellService(dice));
    private final CharacterFactory factory = new CharacterFactory(dice);

    @Test
    void undeadAreAlwaysHostile() {
        Character c = factory.create("Cleric", CharacterClass.CLERIC, new AbilityScores(10, 10, 13, 10, 12, 18));
        // Even with maximum charisma, skeletons and zombies attack.
        assertThat(combat.isHostileReaction(c, Bestiary.SKELETON)).isTrue();
        assertThat(combat.isHostileReaction(c, Bestiary.ZOMBIE)).isTrue();
    }

    @Test
    void startCombatRollsMonstersAndEntersCombat() {
        SaveGame save = combatSave(Bestiary.ORC, 3, 30);
        combat.startCombat(save);
        assertThat(save.getSession().getState()).isEqualTo(SessionState.IN_COMBAT);
        assertThat(save.getSession().getCombat().getMonsters()).hasSize(3);
        assertThat(save.getSession().getCombat().getInitialCount()).isEqualTo(3);
    }

    @Test
    void aTankyHeroDefeatsAWeakFoeAndGainsXp() {
        SaveGame save = combatSave(Bestiary.GIANT_RAT, 1, 60);
        Character hero = save.getCharacter();
        combat.startCombat(save);

        for (int round = 0; round < 300 && save.getSession().getState() == SessionState.IN_COMBAT; round++) {
            combat.attackRound(save, null);
        }

        assertThat(save.getSession().getState()).isEqualTo(SessionState.EXPLORING);
        assertThat(save.getSession().currentRoom().isCleared()).isTrue();
        assertThat(save.getSession().getCombat()).isNull();
        assertThat(hero.getXp()).isGreaterThan(0); // giant rat is worth 5 XP
    }

    @Test
    void anOutnumberedOneHitPointHeroDies() {
        SaveGame save = combatSave(Bestiary.ORC, 3, 60);
        Character hero = save.getCharacter();
        hero.setMaxHp(1);
        hero.setCurrentHp(1);
        combat.startCombat(save);

        for (int round = 0; round < 500 && save.getSession().getState() == SessionState.IN_COMBAT; round++) {
            combat.attackRound(save, null);
        }

        assertThat(hero.isAlive()).isFalse();
        assertThat(save.getSession().getState()).isEqualTo(SessionState.IN_TOWN);
        assertThat(save.getSession().getCombat()).isNull();
    }

    @Test
    void fleeingLeavesCombatForAnAdjacentRoom() {
        SaveGame save = combatSave(Bestiary.GIANT_RAT, 1, 60);
        combat.startCombat(save);
        assertThat(save.getSession().getCurrentRoomId()).isEqualTo(0);

        combat.flee(save);

        assertThat(save.getSession().getState()).isEqualTo(SessionState.EXPLORING);
        assertThat(save.getSession().getCombat()).isNull();
        assertThat(save.getSession().getCurrentRoomId()).isEqualTo(1); // the only exit
    }

    @Test
    void fatiguePenaltyReducesHeroDamageOutput() {
        // Paired trials: a fatigued hero (turnsSinceRest >= 6) should deal less total damage in one
        // round of attacks than a fresh hero, since -1 applies to both the to-hit roll and damage.
        int fatiguedTotal = 0;
        int freshTotal = 0;
        for (long seed = 1; seed <= 200; seed++) {
            fatiguedTotal += heroDamageInOneRound(seed, true);
            freshTotal += heroDamageInOneRound(seed, false);
        }
        assertThat(fatiguedTotal).isLessThan(freshTotal);
    }

    /** One fresh 1-on-1 fight (Hero vs. a Bugbear, so it's unlikely to die from a single hit), returning
        how much damage the hero's single attack this round dealt. */
    private int heroDamageInOneRound(long seed, boolean fatigued) {
        Dice localDice = new Dice(new Random(seed));
        CombatService localCombat = new CombatService(localDice, new SpellService(localDice));
        Character hero = new CharacterFactory(localDice)
                .create("Hero", CharacterClass.FIGHTER, new AbilityScores(16, 9, 9, 13, 13, 12));
        hero.setMaxHp(200);
        hero.setCurrentHp(200);
        SaveGame save = combatSave(Bestiary.BUGBEAR, 1, 200);
        save.setCharacter(hero);
        if (fatigued) {
            save.getSession().setTurnsSinceRest(6);
        }

        localCombat.startCombat(save);
        int hpBefore = save.getSession().getCombat().getMonsters().get(0).getCurrentHp();
        localCombat.attackRound(save, null);
        if (save.getSession().getState() == SessionState.IN_COMBAT) {
            return hpBefore - save.getSession().getCombat().getMonsters().get(0).getCurrentHp();
        }
        return hpBefore; // the monster died this round -> its full remaining hp was dealt
    }

    @Test
    void awardingXpLevelsTheCharacterUp() {
        Character fighter = factory.create("Conan", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
        int hpBefore = fighter.getMaxHp();
        Leveling.awardXp(fighter, 2000, dice); // fighter reaches level 2 at 2000 XP
        assertThat(fighter.getLevel()).isEqualTo(2);
        assertThat(fighter.getMaxHp()).isGreaterThan(hpBefore);

        Leveling.awardXp(fighter, 1_000_000, dice); // vaults several levels at once
        assertThat(fighter.getLevel()).isGreaterThan(5);
        assertThat(fighter.getLevel()).isLessThanOrEqualTo(14);
    }

    // --- helpers -----------------------------------------------------------

    private SaveGame combatSave(MonsterType type, int count, int heroHp) {
        Character hero = factory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(16, 9, 9, 13, 13, 12));
        hero.setMaxHp(heroHp);
        hero.setCurrentHp(heroHp);

        SaveGame save = new SaveGame();
        save.setCharacter(hero);

        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room here = new Room(0);
        here.setDescription("a fighting pit");
        here.setContent(dev.freitas.delve.game.model.ContentType.MONSTER);
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
