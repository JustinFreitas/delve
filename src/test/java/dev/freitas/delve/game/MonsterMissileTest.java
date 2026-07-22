package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.MissileRangeTable;
import dev.freitas.delve.game.model.AttackEffect;
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
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.SpellService;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Monster missile fire: an armed monster (bow/sling) shoots the party during the approach under the
    same B/X range-band to-hit math the party's own missile fire uses; melee-only monsters just close. */
class MonsterMissileTest {

    private final Dice dice = new Dice(new Random(26));
    private final CombatService combat = new CombatService(dice, new SpellService(dice));
    private final CharacterFactory factory = new CharacterFactory(dice);

    @Test
    void armedMonstersExposeARangedAttackAndMeleeOnlyOnesDoNot() {
        assertThat(Bestiary.ORC.ranged()).isNotNull();
        assertThat(Bestiary.ORC.ranged().damage()).isEqualTo(new DamageRoll(1, 6)); // short bow
        assertThat(Bestiary.ORC.ranged().range().shortFeet()).isEqualTo(50);
        assertThat(Bestiary.KOBOLD.ranged()).isNotNull(); // sling
        assertThat(Bestiary.GOBLIN.ranged()).isNotNull();
        assertThat(Bestiary.HOBGOBLIN.ranged()).isNotNull();

        assertThat(Bestiary.GIANT_RAT.ranged()).isNull(); // natural melee attacker
        assertThat(Bestiary.SKELETON.ranged()).isNull();
        assertThat(Bestiary.STIRGE.ranged()).isNull();
    }

    @Test
    void withRangedCopiesTheTypePreservingEveryOtherField() {
        MonsterType plain = new MonsterType("Test", 2, 1, 5, new DamageRoll(1, 8), 9, 20, "1d6", 90, AttackEffect.NORMAL);
        assertThat(plain.ranged()).isNull();

        MonsterType armed = plain.withRanged(new dev.freitas.delve.game.engine.RangedAttack(
                new DamageRoll(1, 4), new MissileRangeTable(40, 80, 160)));
        assertThat(armed.ranged()).isNotNull();
        assertThat(armed.name()).isEqualTo("Test");
        assertThat(armed.hitDiceCount()).isEqualTo(2);
        assertThat(armed.armorClass()).isEqualTo(5);
        assertThat(armed.attack()).isEqualTo(new DamageRoll(1, 8)); // melee attack untouched
        assertThat(plain.ranged()).isNull(); // original unchanged (record copy)
    }

    @Test
    void aRangedMonsterFiresDuringTheApproach() {
        SaveGame save = combatSave(Bestiary.ORC, 1, 60); // Orc short bow; moveRate 60 -> closes 20/round
        combat.startCombat(save);
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setPartySurprised(false);
        encounter.setMonstersSurprised(false);
        encounter.setDistanceFeet(45); // within Short range (<=50); after closing 20 it's still 25 (> 0)

        ExplorationResult result = combat.attackRound(save, null);

        assertThat(encounter.getDistanceFeet()).isEqualTo(25); // still not in melee
        assertThat(String.join(" ", result.getLines())).contains("shoots"); // the orc loosed an arrow
    }

    @Test
    void aMeleeOnlyMonsterCannotAttackDuringTheApproach() {
        SaveGame save = combatSave(Bestiary.GIANT_RAT, 1, 60); // no ranged; moveRate 120 -> closes 40/round
        combat.startCombat(save);
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setPartySurprised(false);
        encounter.setMonstersSurprised(false);
        encounter.setDistanceFeet(45); // after closing 40 it's still 5 (> 0, not yet melee)
        int hpBefore = save.getCharacter().getCurrentHp();

        ExplorationResult result = combat.attackRound(save, null);

        assertThat(encounter.getDistanceFeet()).isEqualTo(5);
        assertThat(String.join(" ", result.getLines())).doesNotContain("shoots");
        assertThat(save.getCharacter().getCurrentHp()).isEqualTo(hpBefore); // the rat can't reach yet
    }

    @Test
    void aRangedMonsterBeyondLongRangeHoldsFire() {
        SaveGame save = combatSave(Bestiary.ORC, 1, 60); // short bow Long range 150
        combat.startCombat(save);
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setPartySurprised(false);
        encounter.setMonstersSurprised(false);
        encounter.setDistanceFeet(200); // after closing 20 it's 180, still beyond Long range
        int hpBefore = save.getCharacter().getCurrentHp();

        ExplorationResult result = combat.attackRound(save, null);

        assertThat(encounter.getDistanceFeet()).isEqualTo(180);
        assertThat(String.join(" ", result.getLines())).doesNotContain("shoots");
        assertThat(save.getCharacter().getCurrentHp()).isEqualTo(hpBefore);
    }

    @Test
    void missileFireWhittlesThePartyBeforeMeleeIsJoined() {
        SaveGame save = combatSave(Bestiary.ORC, 8, 400); // a firing line of orcs, plenty of shots
        save.getCharacter().setArmor(Armor.NONE); // AC 9: easy to hit, so the volley reliably connects
        combat.startCombat(save);
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setPartySurprised(false);
        encounter.setMonstersSurprised(false);
        encounter.setDistanceFeet(45); // two approach rounds (45 -> 25 -> 5) of firing before contact
        int hpBefore = save.getCharacter().getCurrentHp();

        combat.attackRound(save, null); // 45 -> 25, still ranged
        combat.attackRound(save, null); // 25 -> 5, still ranged
        assertThat(encounter.getDistanceFeet()).isGreaterThan(0); // melee not yet joined
        assertThat(save.getCharacter().getCurrentHp()).isLessThan(hpBefore); // yet the party has taken fire
    }

    private SaveGame combatSave(MonsterType type, int count, int heroHp) {
        Character hero = factory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(16, 9, 9, 9, 9, 12));
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
