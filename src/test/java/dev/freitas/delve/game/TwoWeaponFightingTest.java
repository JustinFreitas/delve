package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.SpellService;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Two-weapon fighting grants +1 to attack (no extra damage) — {@code CombatService.meleeAttack}. */
class TwoWeaponFightingTest {

    @Test
    void dualWieldingDealsMoreTotalDamageThanSingleWielding() {
        int dualTotal = 0;
        int singleTotal = 0;
        int trials = 150;
        for (int seed = 0; seed < trials; seed++) {
            dualTotal += heroDamageInOneRound(seed, true);
            singleTotal += heroDamageInOneRound(seed, false);
        }
        assertThat(dualTotal).isGreaterThan(singleTotal);
    }

    /** One fresh 1-on-1 fight (Hero vs. a Bugbear, so it's unlikely to die from a single hit), returning
        how much damage the hero's single attack this round dealt. */
    private int heroDamageInOneRound(long seed, boolean dualWielding) {
        Dice dice = new Dice(new Random(seed));
        CombatService combat = new CombatService(dice, new SpellService(dice));
        Character hero = new CharacterFactory(dice)
                .create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
        hero.setMaxHp(200);
        hero.setCurrentHp(200);
        if (dualWielding) {
            hero.setOffHandWeapon("Dagger");
        }
        SaveGame save = combatSave(hero, Bestiary.BUGBEAR, 1);

        combat.startCombat(save);
        int hpBefore = save.getSession().getCombat().getMonsters().get(0).getCurrentHp();
        combat.attackRound(save, null);
        if (save.getSession().getState() == SessionState.IN_COMBAT) {
            return hpBefore - save.getSession().getCombat().getMonsters().get(0).getCurrentHp();
        }
        return hpBefore; // the monster died this round -> its full remaining hp was dealt
    }

    private SaveGame combatSave(Character hero, dev.freitas.delve.game.model.MonsterType type, int count) {
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
