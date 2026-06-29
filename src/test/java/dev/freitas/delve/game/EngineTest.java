package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.Advancement;
import dev.freitas.delve.game.engine.AttackResolver;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.CombatTables;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.SavingThrows;
import dev.freitas.delve.game.model.Monster;
import java.util.Random;
import org.junit.jupiter.api.Test;

class EngineTest {

    private final Dice seededDice = new Dice(new Random(7));

    // --- Attack resolution -------------------------------------------------

    @Test
    void targetNumberIsThac0MinusAc() {
        assertThat(AttackResolver.targetNumber(19, 9)).isEqualTo(10);
        assertThat(AttackResolver.targetNumber(19, 0)).isEqualTo(19);
        assertThat(AttackResolver.targetNumber(17, -2)).isEqualTo(19);
    }

    @Test
    void hitWhenModifiedRollMeetsTarget() {
        // THAC0 19 vs AC 9 needs a 10.
        assertThat(AttackResolver.resolve(10, 0, 19, 9).hit()).isTrue();
        assertThat(AttackResolver.resolve(9, 0, 19, 9).hit()).isFalse();
        // A +2 modifier turns a 9 into a hit.
        assertThat(AttackResolver.resolve(9, 2, 19, 9).hit()).isTrue();
    }

    @Test
    void naturalTwentyAlwaysHitsAndNaturalOneAlwaysMisses() {
        // Impossible target, but a natural 20 still hits.
        var crit = AttackResolver.resolve(20, -20, 19, -10);
        assertThat(crit.hit()).isTrue();
        assertThat(crit.critical()).isTrue();
        // Trivial target, but a natural 1 still misses.
        var fumble = AttackResolver.resolve(1, 20, 19, 9);
        assertThat(fumble.hit()).isFalse();
        assertThat(fumble.fumble()).isTrue();
    }

    // --- THAC0 tables ------------------------------------------------------

    @Test
    void classThac0FollowsBxBands() {
        assertThat(CombatTables.classThac0(CharacterClass.FIGHTER, 1)).isEqualTo(19);
        assertThat(CombatTables.classThac0(CharacterClass.FIGHTER, 4)).isEqualTo(17);
        assertThat(CombatTables.classThac0(CharacterClass.FIGHTER, 7)).isEqualTo(14);
        assertThat(CombatTables.classThac0(CharacterClass.CLERIC, 4)).isEqualTo(19);
        assertThat(CombatTables.classThac0(CharacterClass.CLERIC, 5)).isEqualTo(17);
        assertThat(CombatTables.classThac0(CharacterClass.MAGIC_USER, 5)).isEqualTo(19);
        assertThat(CombatTables.classThac0(CharacterClass.MAGIC_USER, 6)).isEqualTo(17);
        // Demihumans attack as fighters.
        assertThat(CombatTables.classThac0(CharacterClass.DWARF, 4)).isEqualTo(17);
    }

    @Test
    void monsterThac0StepsByHitDiceWithPlusBump() {
        assertThat(CombatTables.monsterThac0(1, 0)).isEqualTo(19);
        assertThat(CombatTables.monsterThac0(2, 0)).isEqualTo(18);
        assertThat(CombatTables.monsterThac0(1, 1)).isEqualTo(18); // 1+1 attacks as 2 HD
        assertThat(CombatTables.monsterThac0(3, 1)).isEqualTo(16); // 3+1 attacks as 4 HD
        assertThat(CombatTables.monsterThac0(1, -1)).isEqualTo(19); // 1-1 still attacks as 1 HD
    }

    // --- Advancement -------------------------------------------------------

    @Test
    void advancementTablesAndLookups() {
        assertThat(Advancement.maxLevel(CharacterClass.FIGHTER)).isEqualTo(14);
        assertThat(Advancement.maxLevel(CharacterClass.HALFLING)).isEqualTo(8);
        assertThat(Advancement.maxLevel(CharacterClass.ELF)).isEqualTo(10);
        assertThat(Advancement.maxLevel(CharacterClass.DWARF)).isEqualTo(12);

        assertThat(Advancement.xpForLevel(CharacterClass.FIGHTER, 2)).isEqualTo(2000);
        assertThat(Advancement.xpForLevel(CharacterClass.FIGHTER, 9)).isEqualTo(240000);

        assertThat(Advancement.levelForXp(CharacterClass.FIGHTER, 0)).isEqualTo(1);
        assertThat(Advancement.levelForXp(CharacterClass.FIGHTER, 1999)).isEqualTo(1);
        assertThat(Advancement.levelForXp(CharacterClass.FIGHTER, 2000)).isEqualTo(2);
        assertThat(Advancement.levelForXp(CharacterClass.FIGHTER, 2500)).isEqualTo(2);
        // Beyond the table, capped at class max.
        assertThat(Advancement.levelForXp(CharacterClass.HALFLING, 99_999_999)).isEqualTo(8);
    }

    @Test
    void primeRequisiteXpBonusApplied() {
        assertThat(Advancement.adjustedAward(1000, 0)).isEqualTo(1000);
        assertThat(Advancement.adjustedAward(1000, 10)).isEqualTo(1100);
        assertThat(Advancement.adjustedAward(1000, 5)).isEqualTo(1050);
        assertThat(Advancement.adjustedAward(1000, -20)).isEqualTo(800);
    }

    // --- Saving throws -----------------------------------------------------

    @Test
    void savingThrowsAreBandedByLevel() {
        assertThat(SavingThrows.forCharacter(CharacterClass.CLERIC, 1).deathPoison()).isEqualTo(11);
        assertThat(SavingThrows.forCharacter(CharacterClass.CLERIC, 4).deathPoison()).isEqualTo(11);
        assertThat(SavingThrows.forCharacter(CharacterClass.CLERIC, 5).deathPoison()).isEqualTo(9);
        assertThat(SavingThrows.forCharacter(CharacterClass.FIGHTER, 1).spells()).isEqualTo(16);
        // Dwarves and halflings share the sturdy demihuman table.
        assertThat(SavingThrows.forCharacter(CharacterClass.DWARF, 1).deathPoison()).isEqualTo(8);
        assertThat(SavingThrows.forCharacter(CharacterClass.HALFLING, 1).deathPoison()).isEqualTo(8);
        // Above the top band, the best row is reused.
        assertThat(SavingThrows.forCharacter(CharacterClass.FIGHTER, 99).deathPoison()).isEqualTo(4);
    }

    // --- Monsters ----------------------------------------------------------

    @Test
    void monsterHitPointsAndThac0() {
        assertThat(Bestiary.SKELETON.thac0()).isEqualTo(19);
        assertThat(Bestiary.ZOMBIE.thac0()).isEqualTo(18);
        assertThat(Bestiary.BUGBEAR.thac0()).isEqualTo(16);
        assertThat(Bestiary.HOBGOBLIN.hitDiceLabel()).isEqualTo("1+1");
        assertThat(Bestiary.SKELETON.hitDiceLabel()).isEqualTo("1");

        for (int i = 0; i < 50; i++) {
            Monster z = Monster.roll(Bestiary.ZOMBIE, seededDice);
            assertThat(z.getMaxHp()).isBetween(2, 16); // 2d8
            assertThat(z.getCurrentHp()).isEqualTo(z.getMaxHp());
        }

        Monster orc = Monster.roll(Bestiary.ORC, seededDice);
        int hp = orc.getMaxHp();
        orc.takeDamage(hp - 1);
        assertThat(orc.isAlive()).isTrue();
        orc.takeDamage(5);
        assertThat(orc.isAlive()).isFalse();
        assertThat(orc.getCurrentHp()).isZero();
    }

    @Test
    void bestiaryLookup() {
        assertThat(Bestiary.byName("orc")).isEqualTo(Bestiary.ORC);
        assertThat(Bestiary.byName("Goblin")).isEqualTo(Bestiary.GOBLIN);
        assertThat(Bestiary.byName("dragon")).isNull();
        assertThat(Bestiary.all()).hasSize(9);
    }

    // --- Damage ------------------------------------------------------------

    @Test
    void damageRollFormatsAndFloorsAtOne() {
        assertThat(new DamageRoll(1, 6).toString()).isEqualTo("1d6");
        assertThat(new DamageRoll(2, 4, 1).toString()).isEqualTo("2d4+1");
        assertThat(new DamageRoll(1, 6, -1).toString()).isEqualTo("1d6-1");
        assertThat(new DamageRoll(1, 6).average()).isEqualTo(3.5);

        // A large negative modifier still deals at least 1 on a hit.
        DamageRoll feeble = new DamageRoll(1, 4, -10);
        for (int i = 0; i < 50; i++) {
            assertThat(feeble.roll(seededDice)).isEqualTo(1);
        }
    }
}
