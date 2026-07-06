package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.model.Character;
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

/** Evasion on {@code /flee}: the fleeing side auto-escapes if faster than the pursuer, otherwise a
    2-in-6 pursuit check stands in for the rulebook's obstacle/dropped-loot/line-of-sight rolls. */
class EvasionTest {

    @Test
    void fasterPartyAlwaysEscapes() {
        // Weight 10 cns (bare, dagger only -> movement rate 120, encounter rate 40) vs. a Skeleton
        // (moveRate 60 -> encounter rate 20): always faster.
        for (int seed = 0; seed < 40; seed++) {
            SaveGame save = fleeAttempt(seed, 10, Bestiary.SKELETON);
            assertThat(save.getSession().getState()).isEqualTo(SessionState.EXPLORING);
        }
    }

    @Test
    void slowerPartyOnlySometimesEscapes() {
        // Weight 510 cns (plate mail alone -> movement rate 90, encounter rate 30) vs. a Giant Rat
        // (moveRate 120 -> encounter rate 40): never faster, so only the flat 2-in-6 pursuit-check
        // escape applies.
        int escaped = 0;
        int caught = 0;
        int trials = 100;
        for (int seed = 0; seed < trials; seed++) {
            SaveGame save = fleeAttempt(seed, 510, Bestiary.GIANT_RAT);
            if (save.getSession().getState() == SessionState.EXPLORING) {
                escaped++;
            } else {
                caught++;
                assertThat(save.getSession().getState()).isEqualTo(SessionState.IN_COMBAT);
            }
        }
        assertThat(escaped).isGreaterThan(0);
        assertThat(caught).isGreaterThan(0);
        assertThat(escaped).isLessThan(trials / 2); // well below the "faster" case's near-100%
    }

    /** Builds a bare, precisely-weighted PC (armor NONE, no shield/inventory/gold, a single dagger as
        mainWeapon) then bumps gold up to land exactly on the requested total {@code carriedWeightCns()}
        -- isolating the evasion math from {@code CharacterFactory}'s starting-kit weight/random gold,
        which would otherwise make the exact encounter-rate band this test depends on nondeterministic. */
    private SaveGame fleeAttempt(long seed, int carriedWeightCns, MonsterType monster) {
        Dice dice = new Dice(new Random(seed));
        CombatService combat = new CombatService(dice, new SpellService(dice));
        Character hero = new Character();
        hero.setName("Hero");
        hero.setCharacterClass(CharacterClass.FIGHTER);
        hero.setAbilities(new AbilityScores(9, 9, 9, 9, 9, 9));
        hero.setArmor(carriedWeightCns >= 500 ? Armor.PLATE_MAIL : Armor.NONE);
        hero.setShield(false);
        hero.setMainWeapon("Dagger"); // 10 cns
        hero.setTorches(0);
        hero.setGold(carriedWeightCns - hero.carriedWeightCns());
        hero.setMaxHp(200);
        hero.setCurrentHp(200);
        SaveGame save = combatSave(hero, monster, 1);

        combat.startCombat(save);
        combat.flee(save);
        return save;
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
