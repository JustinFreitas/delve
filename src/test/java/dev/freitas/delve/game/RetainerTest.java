package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.RetainerRules;
import dev.freitas.delve.game.model.Character;
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
import dev.freitas.delve.game.session.SpellService;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RetainerTest {

    private final Dice dice = new Dice(new Random(21));
    private final CombatService combat = new CombatService(dice, new SpellService(dice));
    private final CharacterFactory characterFactory = new CharacterFactory(dice);
    private final RetainerFactory retainerFactory = new RetainerFactory(dice);

    @Test
    void charismaCapsRetainersAndSetsLoyalty() {
        assertThat(RetainerRules.maxRetainers(3)).isEqualTo(1);
        assertThat(RetainerRules.maxRetainers(9)).isEqualTo(4);
        assertThat(RetainerRules.maxRetainers(13)).isEqualTo(5);
        assertThat(RetainerRules.maxRetainers(18)).isEqualTo(7);

        assertThat(RetainerRules.baseLoyalty(9)).isEqualTo(7); // average
        assertThat(RetainerRules.baseLoyalty(18)).isEqualTo(11); // 7 + 4
        assertThat(RetainerRules.baseLoyalty(3)).isEqualTo(5); // 7 - 2
        assertThat(RetainerRules.loyaltyDescriptor(11)).isEqualTo("loyal");
    }

    @Test
    void retainerFactoryProducesAViableHench() {
        Retainer r = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 8);
        assertThat(r.getMaxHp()).isGreaterThanOrEqualTo(1);
        assertThat(r.getCurrentHp()).isEqualTo(r.getMaxHp());
        assertThat(r.armorClass()).isLessThanOrEqualTo(7); // chain + shield
        assertThat(r.getMainWeaponDamage().toString()).isEqualTo("1d8");
        assertThat(r.isAlive()).isTrue();
    }

    @Test
    void ownerIsUnsetUntilExplicitlyAssigned() {
        // RetainerFactory deliberately has no Character/SaveGame dependency -- ownership is set by the
        // caller (e.g. HireCommand.hireOne), not baked into construction.
        Retainer r = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 8);
        assertThat(r.getOwner()).isNull();

        r.setOwner("Anna");
        assertThat(r.getOwner()).isEqualTo("Anna");
    }

    @Test
    void equipmentForMatchesWhatCreateActuallyEquips() {
        for (CharacterClass cls : CharacterClass.values()) {
            Retainer r = retainerFactory.create("Test", cls, 1, 9);
            RetainerFactory.Equipment eq = RetainerFactory.equipmentFor(cls);
            assertThat(r.getArmor()).as(cls + " armor").isEqualTo(eq.armor());
            assertThat(r.isShield()).as(cls + " shield").isEqualTo(eq.shield());
            assertThat(r.getMainWeapon()).as(cls + " weapon").isEqualTo(eq.weapon());
            assertThat(r.getMainWeaponDamage()).as(cls + " damage").isEqualTo(eq.weaponDamage());
            assertThat(r.getSecondaryWeapon()).as(cls + " secondary weapon").isEqualTo(eq.secondaryWeapon());
            assertThat(r.getSecondaryWeaponDamage()).as(cls + " secondary damage").isEqualTo(eq.secondaryWeaponDamage());
        }
    }

    @Test
    void everyClassExceptMagicUserAndKnightGetsAMissileBackupWeapon() {
        for (CharacterClass cls : CharacterClass.values()) {
            RetainerFactory.Equipment eq = RetainerFactory.equipmentFor(cls);
            if (cls == CharacterClass.MAGIC_USER || cls == CharacterClass.KNIGHT) {
                assertThat(eq.secondaryWeapon()).as(cls + " has no missile backup").isNull();
            } else {
                assertThat(eq.secondaryWeapon()).as(cls + " missile backup").isNotNull();
            }
        }
    }

    @Test
    void retainersFightAndShareXp() {
        SaveGame save = combatSave(Bestiary.ORC, 2);
        Character pc = save.getCharacter();
        Retainer hench = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 9);
        hench.setMaxHp(40);
        hench.setCurrentHp(40);
        save.getRetainers().add(hench);

        combat.startCombat(save);
        for (int round = 0; round < 300 && save.getSession().getState() == SessionState.IN_COMBAT; round++) {
            combat.attackRound(save, null);
        }

        assertThat(save.getSession().getState()).isEqualTo(SessionState.EXPLORING);
        assertThat(pc.getXp()).isGreaterThan(0);
        assertThat(hench.getXp()).isGreaterThan(0); // retainer earns a half-share
        assertThat(hench.getXp()).isLessThan(pc.getXp());
    }

    @Test
    void disloyalRetainersDesertWhenThePartyFlees() {
        // Desertion is only ever at risk after a genuinely bad fight, and only for a retainer who'd
        // already broken (fled=true, e.g. from being bloodied earlier this delve) — simulate both here.
        // Stirge (encounter rate 10) is slower than the test hero's Chain Mail (encounter rate 20), so
        // fleeing is deterministic here — these tests are about desertion mechanics, not evasion.
        SaveGame save = combatSave(Bestiary.STIRGE, 1);
        save.getCharacter().setCurrentHp(save.getCharacter().getMaxHp() / 5); // bloodied: catastrophic gate
        Retainer coward = retainerFactory.create("Mott", CharacterClass.THIEF, 1, 0); // loyalty 0 -> always deserts
        coward.setMaxHp(30);
        coward.setCurrentHp(30);
        coward.setFled(true); // already broke earlier this delve
        save.getRetainers().add(coward);

        combat.startCombat(save);
        combat.flee(save);

        assertThat(save.getRetainers()).isEmpty(); // deserted in the rout
    }

    @Test
    void loyalRetainersStayThroughARout() {
        // Stirge (encounter rate 10) is slower than the test hero's Chain Mail (encounter rate 20), so
        // fleeing is deterministic here — these tests are about desertion mechanics, not evasion.
        SaveGame save = combatSave(Bestiary.STIRGE, 1);
        save.getCharacter().setCurrentHp(save.getCharacter().getMaxHp() / 5); // bloodied: catastrophic gate
        Retainer stalwart = retainerFactory.create("Gwen", CharacterClass.FIGHTER, 1, 12); // never fails morale
        stalwart.setMaxHp(40);
        stalwart.setCurrentHp(40);
        stalwart.setFled(true); // already broke earlier this delve, but loyalty holds
        save.getRetainers().add(stalwart);

        combat.startCombat(save);
        combat.flee(save);

        assertThat(save.getRetainers()).containsExactly(stalwart);
    }

    @Test
    void untouchedRetainersJustRetreatWithoutRiskingDesertion() {
        // The bug this guards against: fleeing a barely-started fight (nobody hurt, nobody already
        // broken) must never roll desertion at all, no matter how disloyal a retainer is.
        // Stirge (encounter rate 10) is slower than the test hero's Chain Mail (encounter rate 20), so
        // fleeing is deterministic here — these tests are about desertion mechanics, not evasion.
        SaveGame save = combatSave(Bestiary.STIRGE, 1);
        Retainer coward = retainerFactory.create("Mott", CharacterClass.THIEF, 1, 0); // loyalty 0
        coward.setMaxHp(30);
        coward.setCurrentHp(30);
        save.getRetainers().add(coward);

        combat.startCombat(save);
        combat.flee(save);

        assertThat(save.getRetainers()).containsExactly(coward);
    }

    private SaveGame combatSave(MonsterType type, int count) {
        Character hero = characterFactory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(16, 9, 9, 13, 13, 12));
        hero.setMaxHp(60);
        hero.setCurrentHp(60);

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
