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
import dev.freitas.delve.game.model.Monster;
import dev.freitas.delve.game.model.MonsterType;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.SpellService;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MoraleTriggerTest {

    private final Dice dice = new Dice(new Random(27));
    private final CombatService combat = new CombatService(dice, new SpellService(dice));
    private final CharacterFactory factory = new CharacterFactory(dice);

    @Test
    void moraleChecksOnlyAtFirstCasualtyAndHalfLossThresholds() {
        SaveGame save = combatSave(Bestiary.GOBLIN, 6, 300); // tanky hero, no retainers
        combat.startCombat(save);
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setDistanceFeet(0);
        encounter.setPartySurprised(false);

        assertThat(encounter.isFirstCasualtyChecked()).isFalse();
        assertThat(encounter.isHalfLossChecked()).isFalse();

        encounter.getMonsters().get(0).takeDamage(999); // guarantee at least one casualty before this round
        combat.attackRound(save, null);
        assertThat(encounter.isFirstCasualtyChecked()).isTrue();

        if (!encounter.isMoraleBroken()) {
            // Push comfortably past half losses regardless of what the round's own attacks also did.
            List<Monster> alive = encounter.aliveMonsters();
            for (int i = 0; i < Math.min(2, alive.size()); i++) {
                alive.get(i).takeDamage(999);
            }
            combat.attackRound(save, null);
            assertThat(encounter.isHalfLossChecked()).isTrue();
        }
    }

    @Test
    void smallGroupCoincidingThresholdsBothGetMarked() {
        // In a group of 2 the first casualty is also the half-loss point. Both checks are marked (each
        // gets its own roll — see coincidentTriggersEachGetTheirOwnMoraleRoll). Suppressed round so the
        // party can't over-kill the second monster down to zero alive (which would skip morale entirely).
        SaveGame save = suppressedMeleeRound(Bestiary.SKELETON, 2);

        killMonsters(save, 1); // 1 of 2 dead: first-casualty and half-loss at once
        combat.attackRound(save, null);

        CombatEncounter encounter = save.getSession().getCombat();
        assertThat(encounter.isFirstCasualtyChecked()).isTrue();
        assertThat(encounter.isHalfLossChecked()).isTrue();
    }

    @Test
    void aBadlyBloodiedPartyMakesMonstersBreakLessOften() {
        // Same monster losses (half dead) either way; only the party's own HP differs. A healthy party
        // should make the modifier favor the monsters breaking; a badly bloodied party should make them
        // hold on longer.
        int brokenWhenPartyHealthy = 0;
        int brokenWhenPartyBloodied = 0;
        int trials = 200;
        for (int seed = 0; seed < trials; seed++) {
            brokenWhenPartyHealthy += moraleBrokeWithPartyAt(seed, 300) ? 1 : 0;
            brokenWhenPartyBloodied += moraleBrokeWithPartyAt(seed, 20) ? 1 : 0;
        }
        assertThat(brokenWhenPartyBloodied).isLessThan(brokenWhenPartyHealthy);
    }

    private boolean moraleBrokeWithPartyAt(long seed, int currentHp) {
        Dice localDice = new Dice(new Random(seed));
        CombatService localCombat = new CombatService(localDice, new SpellService(localDice));
        SaveGame save = combatSave(Bestiary.GOBLIN, 2, 300);
        save.getCharacter().setCurrentHp(currentHp);

        localCombat.startCombat(save);
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setDistanceFeet(0);
        encounter.setPartySurprised(false);
        encounter.getMonsters().get(0).takeDamage(999); // 1 of 2 dead: triggers first+half-loss at once

        localCombat.attackRound(save, null);
        return encounter.isMoraleBroken();
    }

    @Test
    void halfLossCheckDoesNotFireEarlyOnOddGroups() {
        // Group of 5: "half or fewer remain" means 2 alive (3 dead), not 3 alive. Skeletons (morale 12)
        // with a badly bloodied party never break, so this isolates the threshold, not the roll.
        SaveGame save = suppressedMeleeRound(Bestiary.SKELETON, 5);

        killMonsters(save, 2); // 3 alive of 5 — under half yet
        combat.attackRound(save, null);
        CombatEncounter encounter = save.getSession().getCombat();
        assertThat(encounter.isFirstCasualtyChecked()).isTrue();
        assertThat(encounter.isHalfLossChecked()).as("2 of 5 dead is not yet half losses").isFalse();
    }

    @Test
    void halfLossCheckFiresOnceHalfAreActuallyDead() {
        SaveGame save = suppressedMeleeRound(Bestiary.SKELETON, 5);

        killMonsters(save, 3); // 2 alive of 5 — half or fewer remain
        combat.attackRound(save, null);
        CombatEncounter encounter = save.getSession().getCombat();
        assertThat(encounter.isHalfLossChecked()).isTrue();
    }

    @Test
    void coincidentTriggersEachGetTheirOwnMoraleRoll() {
        // When one round crosses both the first-casualty AND the half-loss threshold at once (3 of 6 die
        // in a single round), each triggered check gets its own 2d6 roll — not one shared roll. Skeletons
        // + a bloodied party never break, so both rolls happen and are countable.
        CountingDice counter = new CountingDice(new Random(1));
        CombatService localCombat = new CombatService(counter, new SpellService(counter));
        SaveGame save = suppressedMeleeRound(Bestiary.SKELETON, 6);

        killMonsters(save, 3); // 3 alive of 6: first-casualty and half-loss both newly due this round
        localCombat.attackRound(save, null);

        CombatEncounter encounter = save.getSession().getCombat();
        assertThat(encounter.isFirstCasualtyChecked()).isTrue();
        assertThat(encounter.isHalfLossChecked()).isTrue();
        assertThat(counter.morale2d6Calls).as("one roll per triggered check, not one shared roll").isEqualTo(2);
    }

    /** A melee round where neither side acts (both surprised) and the party is badly bloodied, so a
        skeleton group's morale check never breaks — isolating the morale-threshold/roll logic. */
    private SaveGame suppressedMeleeRound(MonsterType type, int count) {
        SaveGame save = combatSave(type, count, 300);
        combat.startCombat(save);
        CombatEncounter encounter = save.getSession().getCombat();
        encounter.setDistanceFeet(0);
        encounter.setPartySurprised(true);   // party sits this round out — no monster kills from the party
        encounter.setMonstersSurprised(true); // monsters sit out — party HP stays put
        save.getCharacter().setCurrentHp(10); // bloodied: keeps the situational modifier from easing breaks
        return save;
    }

    private void killMonsters(SaveGame save, int n) {
        List<Monster> monsters = save.getSession().getCombat().getMonsters();
        for (int i = 0; i < n; i++) {
            monsters.get(i).takeDamage(999);
        }
    }

    private static final class CountingDice extends Dice {
        int morale2d6Calls = 0;

        CountingDice(Random rng) {
            super(rng);
        }

        @Override
        public int roll2d6() {
            morale2d6Calls++;
            return super.roll2d6();
        }
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
