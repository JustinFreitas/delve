package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Encumbrance;
import dev.freitas.delve.game.engine.Spell;
import dev.freitas.delve.game.engine.SpellTables;
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
import dev.freitas.delve.game.session.TownService;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SpellTownTest {

    private final Dice dice = new Dice(new Random(31));
    private final SpellService spells = new SpellService(dice);
    private final CombatService combat = new CombatService(dice, spells);
    private final TownService town = new TownService(spells, dice);
    private final CharacterFactory factory = new CharacterFactory(dice);
    private final RetainerFactory retainerFactory = new RetainerFactory(dice);

    // --- Spell tables ------------------------------------------------------

    @Test
    void spellSlotsFollowBxProgression() {
        assertThat(SpellTables.slotsAt(CharacterClass.MAGIC_USER, 1, 1)).isEqualTo(1);
        assertThat(SpellTables.slotsAt(CharacterClass.MAGIC_USER, 3, 2)).isEqualTo(1);
        assertThat(SpellTables.slotsAt(CharacterClass.CLERIC, 1, 1)).isEqualTo(0); // no spells at level 1
        assertThat(SpellTables.slotsAt(CharacterClass.CLERIC, 2, 1)).isEqualTo(1);
        assertThat(SpellTables.isCaster(CharacterClass.FIGHTER)).isFalse();
        assertThat(SpellTables.isCaster(CharacterClass.ELF)).isTrue();
    }

    @Test
    void magicUserPreparesAndCastsMagicMissileInCombat() {
        SaveGame save = combatSave(CharacterClass.MAGIC_USER, Bestiary.GIANT_RAT, 1);
        Character mu = save.getCharacter();
        // Ensure Magic Missile is in the book and prepared.
        mu.getSpellbook().add("Magic Missile");
        spells.autoPrepare(mu);
        assertThat(mu.getMemorizedSpells()).isNotEmpty();

        // If Magic Missile got prepared, casting it should consume the slot and damage the foe.
        if (spells.isMemorized(mu, Spell.MAGIC_MISSILE)) {
            combat.startCombat(save);
            // Isolate spellcasting from the (separately tested) surprise mechanic, which can otherwise
            // skip the player's round-1 action depending on the seed.
            save.getSession().getCombat().setPartySurprised(false);
            int before = save.getSession().getCombat().aliveMonsters().get(0).getCurrentHp();
            combat.castRound(save, Spell.MAGIC_MISSILE, 1);
            boolean damagedOrDead = save.getSession().getCombat() == null
                    || save.getSession().getCombat().getMonsters().get(0).getCurrentHp() < before;
            assertThat(damagedOrDead).isTrue();
            assertThat(spells.isMemorized(mu, Spell.MAGIC_MISSILE)).isFalse(); // slot spent
        }
    }

    @Test
    void clericHealsOutOfCombat() {
        Character cleric = factory.create("Brother", CharacterClass.CLERIC, new AbilityScores(10, 9, 13, 10, 12, 10));
        cleric.setLevel(2); // clerics get a spell slot at level 2
        cleric.setMaxHp(12);
        cleric.setCurrentHp(4);
        cleric.getMemorizedSpells().add(Spell.CURE_LIGHT_WOUNDS.name());

        SaveGame save = new SaveGame();
        save.setCharacter(cleric);
        var result = spells.castOutOfCombat(save, Spell.CURE_LIGHT_WOUNDS);

        assertThat(result.isSuccess()).isTrue();
        assertThat(cleric.getCurrentHp()).isGreaterThan(4);
        assertThat(spells.isMemorized(cleric, Spell.CURE_LIGHT_WOUNDS)).isFalse();
    }

    @Test
    void damageSpellsCannotBeCastOutOfCombat() {
        Character mu = factory.create("Mage", CharacterClass.MAGIC_USER, new AbilityScores(9, 16, 9, 12, 12, 9));
        mu.getMemorizedSpells().add(Spell.MAGIC_MISSILE.name());
        SaveGame save = new SaveGame();
        save.setCharacter(mu);
        var result = spells.castOutOfCombat(save, Spell.MAGIC_MISSILE);
        assertThat(result.isSuccess()).isFalse();
        assertThat(spells.isMemorized(mu, Spell.MAGIC_MISSILE)).isTrue(); // not consumed on failure
    }

    // --- Encumbrance -------------------------------------------------------

    @Test
    void encumbranceTracksArmorAndLoad() {
        assertThat(Encumbrance.movementRate(Armor.NONE, false)).isEqualTo(120);
        assertThat(Encumbrance.movementRate(Armor.PLATE_MAIL, false)).isEqualTo(30);
        assertThat(Encumbrance.movementRate(Armor.LEATHER, true)).isEqualTo(60); // heavy load slows a step
        assertThat(Encumbrance.encounterRate(Armor.NONE, false)).isEqualTo(40);
        assertThat(Encumbrance.heavyLoad(1000)).isTrue();
        assertThat(Encumbrance.heavyLoad(100)).isFalse();
    }

    // --- Town --------------------------------------------------------------

    @Test
    void townRestHealsThePartyAndRefreshesSpells() {
        SaveGame save = new SaveGame();
        Character mu = factory.create("Mage", CharacterClass.MAGIC_USER, new AbilityScores(9, 16, 9, 12, 12, 12));
        mu.setMaxHp(6);
        mu.setCurrentHp(1);
        mu.setGold(500);
        save.setCharacter(mu);

        Retainer hench = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 9);
        hench.setMaxHp(6); // small, known gap so the default week of rest is guaranteed to cap it
        hench.setCurrentHp(1);
        save.getRetainers().add(hench);
        // Put the party in a dungeon to prove /town abandons it.
        save.getSession().setDungeon(simpleDungeon());
        save.getSession().setState(SessionState.EXPLORING);

        int goldBefore = mu.getGold();
        town.returnToTown(save); // defaults to a full week's rest

        assertThat(save.getSession().getState()).isEqualTo(SessionState.IN_TOWN);
        assertThat(save.getSession().getDungeon()).isNull();
        // A week of rest (min 1 hp/day) guarantees at least +7 hp — enough to cap both at max from a
        // 5-hp gap, unlike a single short stay (see restDaysPartiallyHealAShortStay below).
        assertThat(mu.getCurrentHp()).isEqualTo(mu.getMaxHp());
        assertThat(hench.getCurrentHp()).isEqualTo(hench.getMaxHp());
        assertThat(mu.getGold()).isEqualTo(goldBefore - 10); // one retainer's upkeep
        assertThat(mu.getMemorizedSpells()).isNotEmpty(); // spells re-prepared
    }

    @Test
    void restDaysPartiallyHealAShortStay() {
        // A single day's rest (1d3 hp) should almost never fully close a large HP gap.
        int trials = 200;
        int fullyHealed = 0;
        for (int seed = 0; seed < trials; seed++) {
            Dice localDice = new Dice(new Random(seed));
            TownService localTown = new TownService(new SpellService(localDice), localDice);
            SaveGame save = new SaveGame();
            Character hero = new CharacterFactory(localDice)
                    .create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
            hero.setMaxHp(50);
            hero.setCurrentHp(1);
            save.setCharacter(hero);

            localTown.returnToTown(save, 1);
            if (hero.getCurrentHp() >= hero.getMaxHp()) {
                fullyHealed++;
            }
        }
        assertThat(fullyHealed).isZero(); // 1d3 can never close a 49-hp gap in one day
    }

    @Test
    void manyRestDaysEventuallyCapAtMaxHp() {
        SaveGame save = new SaveGame();
        Character hero = factory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
        hero.setMaxHp(6);
        hero.setCurrentHp(1);
        save.setCharacter(hero);

        town.returnToTown(save, 10); // way more than the 5-hp gap needs even at 1 hp/day

        assertThat(hero.getCurrentHp()).isEqualTo(hero.getMaxHp());
    }

    @Test
    void fledRetainersRiskPermanentLossOnReturnToTown() {
        // Statistical: a fled retainer has only a 3-in-6 chance of making it back; across many seeds
        // both outcomes (survives, lost for good) should occur.
        int survived = 0;
        int lost = 0;
        for (int seed = 0; seed < 100; seed++) {
            Dice localDice = new Dice(new Random(seed));
            TownService localTown = new TownService(new SpellService(localDice), localDice);
            SaveGame save = new SaveGame();
            Character hero = new CharacterFactory(localDice)
                    .create("Hero", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
            save.setCharacter(hero);
            Retainer coward = new RetainerFactory(localDice).create("Mott", CharacterClass.THIEF, 1, 7);
            coward.setFled(true);
            save.getRetainers().add(coward);

            localTown.returnToTown(save, 1);
            if (save.getRetainers().contains(coward)) {
                assertThat(coward.isFled()).isFalse(); // survivors rejoin, no longer sitting out
                survived++;
            } else {
                lost++;
            }
        }
        assertThat(survived).isGreaterThan(0);
        assertThat(lost).isGreaterThan(0);
    }

    @Test
    void unpaidRetainersLoseLoyalty() {
        SaveGame save = new SaveGame();
        Character pc = factory.create("Pauper", CharacterClass.FIGHTER, new AbilityScores(12, 9, 9, 12, 12, 9));
        pc.setGold(0); // can't pay upkeep
        save.setCharacter(pc);
        Retainer hench = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 8);
        save.getRetainers().add(hench);

        town.returnToTown(save);
        // Either loyalty dropped, or (if already low) they quit.
        boolean penalized = save.getRetainers().isEmpty() || hench.getLoyalty() == 7;
        assertThat(penalized).isTrue();
    }

    // --- Set-pieces --------------------------------------------------------

    @Test
    void setPiecesAreLoadedFromContent() {
        var setPieces = dev.freitas.delve.game.dungeon.SetPieceLoader.load();
        assertThat(setPieces).isNotEmpty();
        assertThat(setPieces).anyMatch(sp -> sp.name().equals("The Sealed Crypt"));
    }

    // --- helpers -----------------------------------------------------------

    private Dungeon simpleDungeon() {
        Dungeon dungeon = new Dungeon();
        DungeonLevel level = new DungeonLevel(1);
        Room room = new Room(0);
        level.addRoom(room);
        level.setEntranceRoomId(0);
        dungeon.addLevel(level);
        return dungeon;
    }

    private SaveGame combatSave(CharacterClass cls, MonsterType type, int count) {
        Character hero = factory.create("Hero", cls, new AbilityScores(12, 13, 12, 13, 12, 12));
        hero.setMaxHp(20);
        hero.setCurrentHp(20);
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
