package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.GameClock;
import dev.freitas.delve.game.engine.MuleRules;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.ContentType;
import dev.freitas.delve.game.model.Direction;
import dev.freitas.delve.game.model.DoorState;
import dev.freitas.delve.game.model.Dungeon;
import dev.freitas.delve.game.model.DungeonLevel;
import dev.freitas.delve.game.model.Exit;
import dev.freitas.delve.game.model.Mule;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.Room;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.model.SessionState;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.MuleService;
import dev.freitas.delve.game.session.SpellService;
import dev.freitas.delve.game.session.TownService;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The party's pack mule: a real OSE-statted combatant (AC 7, HD 2, THAC0 18, kick 1d4) that takes a
    marching-order slot and can be attacked and killed, plus buy/load/unload/handler, upkeep, and its
    effect on the party's group movement rate — gygax75-rules' "a single mule may be brought into a
    dungeon by a party." */
class MuleTest {

    private final Dice dice = new Dice(new Random(11));
    private final CharacterFactory characterFactory = new CharacterFactory(dice);
    private final RetainerFactory retainerFactory = new RetainerFactory(dice);
    private final MuleFactory muleFactory = new MuleFactory(dice);
    private final MuleService muleService = new MuleService();

    // --- MuleRules -----------------------------------------------------------

    @Test
    void movementRateStepsDownAtTheLightCapacityThreshold() {
        assertThat(MuleRules.movementRate(0)).isEqualTo(120);
        assertThat(MuleRules.movementRate(MuleRules.CAPACITY_LIGHT_CNS)).isEqualTo(120); // exactly at cap: still light
        assertThat(MuleRules.movementRate(MuleRules.CAPACITY_LIGHT_CNS + 1)).isEqualTo(60);
        assertThat(MuleRules.movementRate(MuleRules.CAPACITY_MAX_CNS)).isEqualTo(60);
        assertThat(MuleRules.encounterRate(0)).isEqualTo(40);
        assertThat(MuleRules.encounterRate(MuleRules.CAPACITY_MAX_CNS)).isEqualTo(20);
    }

    @Test
    void capacityRemainingNeverGoesNegative() {
        assertThat(MuleRules.capacityRemaining(0)).isEqualTo(MuleRules.CAPACITY_MAX_CNS);
        assertThat(MuleRules.capacityRemaining(MuleRules.CAPACITY_MAX_CNS)).isZero();
        assertThat(MuleRules.capacityRemaining(MuleRules.CAPACITY_MAX_CNS + 500)).isZero();
    }

    // --- Mule: OSE combatant stats -------------------------------------------

    @Test
    void muleFactoryRollsTheOseStatBlock() {
        Mule mule = muleFactory.create("Jenny");

        assertThat(mule.armorClass()).isEqualTo(7);
        assertThat(mule.thac0()).isEqualTo(18);
        assertThat(mule.getMainWeapon()).isEqualTo("Kick");
        assertThat(mule.getMainWeaponDamage().toString()).isNotBlank();
        // HD 2 (2d8): between 2 and 16, never the un-rolled zero a bare `new Mule()` would have.
        assertThat(mule.getMaxHp()).isBetween(2, 16);
        assertThat(mule.getCurrentHp()).isEqualTo(mule.getMaxHp());
        assertThat(mule.isAlive()).isTrue();
        assertThat(mule.getCharacterClass()).isNull(); // no class -- an animal
    }

    // --- SaveGame: marching order / resolve -----------------------------------

    @Test
    void findMuleIsCaseInsensitive() {
        SaveGame save = heroSave();
        Mule mule = muleFactory.create("Jenny");
        save.getMules().add(mule);

        assertThat(save.findMule("jenny")).isEqualTo(mule);
        assertThat(save.findMule("Nobody")).isNull();
        assertThat(save.getMule()).isEqualTo(mule);
    }

    @Test
    void resolveAndFullOrderIncludeTheMule() {
        SaveGame save = heroSave();
        Mule mule = muleFactory.create("Jenny");
        save.getMules().add(mule);

        assertThat(save.resolve("Jenny")).isSameAs(mule);
        assertThat(save.tokenFor(mule)).isEqualTo("Jenny");
        assertThat(save.fullOrder()).contains(mule); // takes a marching-order slot alongside the PC
    }

    // --- MuleService: handler pick/assign/reconcile -----------------------------

    @Test
    void picksAFreeHandedRetainerAsHandlerOverAShieldedPc() {
        SaveGame save = heroSave(); // sword + shield, 0 hands free
        Retainer thief = retainerFactory.create("Nessa", CharacterClass.THIEF, 1, 9); // 1 hand free
        save.getRetainers().add(thief);

        assertThat(muleService.pickEligibleHandler(save)).isEqualTo("Nessa");
    }

    @Test
    void assignHandlerRejectsACandidateWithNoFreeHand() {
        SaveGame save = heroSave();
        Mule mule = muleFactory.create("Mule");
        save.getMules().add(mule);
        Retainer otherFighter = retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 9); // 0 free
        save.getRetainers().add(otherFighter);

        String failure = muleService.assignHandler(save, mule, "Bryn");

        assertThat(failure).contains("hands are already full");
        assertThat(mule.getHandler()).isNull();
    }

    @Test
    void assignHandlerSucceedsForAFreeHandedCandidate() {
        SaveGame save = heroSave();
        Mule mule = muleFactory.create("Mule");
        save.getMules().add(mule);
        Retainer thief = retainerFactory.create("Nessa", CharacterClass.THIEF, 1, 9);
        save.getRetainers().add(thief);

        String failure = muleService.assignHandler(save, mule, "Nessa");

        assertThat(failure).isNull();
        assertThat(mule.getHandler()).isEqualTo("Nessa");
    }

    @Test
    void reconcileHandlerPicksAReplacementWhenTheHandlerLeavesTheParty() {
        SaveGame save = heroSave();
        save.getCharacter().setShield(false); // now the PC has a free hand
        Mule mule = muleFactory.create("Mule");
        mule.setHandler("Nessa");
        save.getMules().add(mule);
        // Nessa isn't actually in the party -- simulates a dismissed/deserted former handler.

        ExplorationResult result = new ExplorationResult();
        muleService.reconcileHandler(save, result);

        assertThat(mule.getHandler()).isEqualTo(SaveGame.PLAYER_SLOT);
        assertThat(result.text()).contains("takes up");
    }

    @Test
    void reconcileHandlerReportsWhenNobodyIsEligible() {
        SaveGame save = heroSave(); // sword + shield, 0 free
        Mule mule = muleFactory.create("Mule");
        mule.setHandler("Nessa"); // not in the party
        save.getMules().add(mule);

        ExplorationResult result = new ExplorationResult();
        muleService.reconcileHandler(save, result);

        assertThat(mule.getHandler()).isNull();
        assertThat(result.text()).contains("No one has a free hand");
    }

    // --- MuleService: load/unload ------------------------------------------

    @Test
    void loadMovesGoldFromPayerOntoTheMuleUpToItsCapacity() {
        Character pc = characterFactory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
        pc.setGold(5000);
        Mule mule = muleFactory.create("Mule");

        int moved = muleService.load(mule, pc, 5000);

        assertThat(moved).isEqualTo(MuleRules.CAPACITY_MAX_CNS); // clamped to the hard cap
        assertThat(mule.getCarriedGold()).isEqualTo(MuleRules.CAPACITY_MAX_CNS);
        assertThat(pc.getGold()).isEqualTo(5000 - MuleRules.CAPACITY_MAX_CNS);
    }

    @Test
    void loadNeverMovesMoreGoldThanThePayerHas() {
        Character pc = characterFactory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
        pc.setGold(50);
        Mule mule = muleFactory.create("Mule");

        int moved = muleService.load(mule, pc, 500);

        assertThat(moved).isEqualTo(50);
        assertThat(pc.getGold()).isZero();
        assertThat(mule.getCarriedGold()).isEqualTo(50);
    }

    @Test
    void unloadMovesGoldFromTheMuleBackToThePayee() {
        Character pc = characterFactory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
        pc.setGold(0);
        Mule mule = muleFactory.create("Mule");
        mule.setCarriedGold(1000);

        int moved = muleService.unload(mule, pc, 400);

        assertThat(moved).isEqualTo(400);
        assertThat(mule.getCarriedGold()).isEqualTo(600);
        assertThat(pc.getGold()).isEqualTo(400);
    }

    // --- TownService: upkeep -------------------------------------------------

    @Test
    void muleUpkeepIsChargedToItsOwnerOnARealTownVisit() {
        SpellService spells = new SpellService(dice);
        TownService town = new TownService(spells, dice, new GameClock());
        SaveGame save = heroSave();
        Character pc = save.getCharacter();
        pc.setGold(100);
        Mule mule = muleFactory.create("Mule");
        mule.setOwner(pc.getName());
        save.getMules().add(mule);
        save.setLastTownVisitMillis(System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000);

        ExplorationResult result = town.returnToTown(save);

        assertThat(pc.getGold()).isEqualTo(100 - MuleRules.UPKEEP_GP_PER_WEEK);
        assertThat(result.text()).contains("stabling Mule");
    }

    @Test
    void unpaidMuleUpkeepReportsAShortfallButNeverDesertsIt() {
        SpellService spells = new SpellService(dice);
        TownService town = new TownService(spells, dice, new GameClock());
        SaveGame save = heroSave();
        Character pc = save.getCharacter();
        pc.setGold(0);
        Mule mule = muleFactory.create("Mule");
        mule.setOwner(pc.getName());
        save.getMules().add(mule);
        save.setLastTownVisitMillis(System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000);

        ExplorationResult result = town.returnToTown(save);

        assertThat(save.getMules()).containsExactly(mule); // no desertion equivalent for a mule
        assertThat(result.text()).contains("short of the full fee");
    }

    // --- CombatService: overloaded mule drags down the group's flee speed -----

    @Test
    void anOverloadedMuleCanCostThePartyItsSpeedAdvantageOnFlee() {
        // Unarmored PC alone: encounter rate 40, always faster than a Skeleton's 20 -- always escapes
        // (mirrors EvasionTest.fasterPartyAlwaysEscapes). Loading the mule past its light threshold
        // drops its own encounter rate to 20, tying the party's group rate with the Skeleton's, so the
        // party is no longer guaranteed to outrun it.
        int escapedUnburdened = 0;
        int escapedWithOverloadedMule = 0;
        int trials = 60;
        for (int seed = 0; seed < trials; seed++) {
            escapedUnburdened += fleeOutcome(seed, false) ? 1 : 0;
            escapedWithOverloadedMule += fleeOutcome(seed, true) ? 1 : 0;
        }
        assertThat(escapedUnburdened).isEqualTo(trials); // always faster, no mule dragging it down
        assertThat(escapedWithOverloadedMule).isLessThan(trials); // no longer guaranteed
    }

    // A precisely-weighted bare PC (10 cns: a dagger only, no armor/shield/gold/inventory) rather than
    // CharacterFactory's full starting kit + random gold -- that kit alone can reach ~800+ cns (shield
    // + sword + torches + gear + up to 180gp), which would make the "always faster than a Skeleton"
    // assumption below nondeterministic across seeds instead of a guaranteed 120'/turn.
    private boolean fleeOutcome(long seed, boolean overloadedMule) {
        Dice localDice = new Dice(new Random(seed));
        CombatService combat = new CombatService(localDice, new SpellService(localDice));
        Character hero = new Character();
        hero.setName("Hero");
        hero.setCharacterClass(CharacterClass.FIGHTER);
        hero.setAbilities(new AbilityScores(9, 9, 9, 9, 9, 9));
        hero.setArmor(Armor.NONE);
        hero.setShield(false);
        hero.setMainWeapon("Dagger");
        hero.setTorches(0);
        hero.setMaxHp(200);
        hero.setCurrentHp(200);
        SaveGame save = new SaveGame();
        save.setCharacter(hero);
        if (overloadedMule) {
            Mule mule = new MuleFactory(localDice).create("Mule");
            mule.setMaxHp(50); // sturdy enough it can't die to the Skeleton's parting blow and confound the test
            mule.setCurrentHp(50);
            mule.setCarriedGold(MuleRules.CAPACITY_LIGHT_CNS + 1); // just over the light threshold
            save.getMules().add(mule);
        }

        setUpDungeonCombat(save, Bestiary.SKELETON, 1);

        combat.startCombat(save);
        combat.flee(save);
        return save.getSession().getState() == SessionState.EXPLORING;
    }

    // --- CombatService: the mule is a real target, and drain no longer no-ops it ---

    @Test
    void aFrailMuleCanBeKilledByAMonster() {
        // A Wight's DRAIN effect used to be a total no-op against a non-Advanceable target (the fix
        // makes it fall back to ordinary damage instead) -- with only 1 hp, any connecting hit at all
        // kills the mule outright, so across enough trials this must eventually happen if the fix
        // holds. Pre-fix, this loop would run to completion with zero deaths, every time.
        boolean everDied = false;
        for (int seed = 0; seed < 50 && !everDied; seed++) {
            Dice localDice = new Dice(new Random(seed));
            CombatService combat = new CombatService(localDice, new SpellService(localDice));
            Character hero = new CharacterFactory(localDice)
                    .create("Hero", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 9));
            hero.setMaxHp(200);
            hero.setCurrentHp(200);
            SaveGame save = new SaveGame();
            save.setCharacter(hero);
            Mule mule = new MuleFactory(localDice).create("Mule");
            mule.setMaxHp(1);
            mule.setCurrentHp(1);
            mule.setCarriedGold(500);
            save.getMules().add(mule);
            // Single-file corridor, mule explicitly placed front -- the only engaged/targetable member,
            // so a Wight's attack (if it hits at all) must land on the mule, not the PC.
            save.setMarchingOrder(List.of("Mule"));
            setUpDungeonCombat(save, Bestiary.WIGHT, 1);
            save.getSession().currentRoom().setCorridorWidth(1);

            combat.startCombat(save);
            for (int round = 0; round < 8 && save.getSession().getState() == SessionState.IN_COMBAT; round++) {
                combat.attackRound(save, null);
            }
            if (save.getMules().isEmpty()) {
                everDied = true;
            }
        }
        assertThat(everDied).isTrue();
    }

    @Test
    void livingMulesExcludesADeadOne() {
        SaveGame save = heroSave();
        Mule alive = muleFactory.create("Alive");
        Mule dead = muleFactory.create("Dead");
        dead.setCurrentHp(0);
        save.getMules().add(alive);
        save.getMules().add(dead);

        assertThat(save.livingMules()).containsExactly(alive);
    }

    private void setUpDungeonCombat(SaveGame save, dev.freitas.delve.game.model.MonsterType type, int count) {
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
    }

    private SaveGame heroSave() {
        Character hero = characterFactory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(13, 9, 9, 12, 12, 12));
        SaveGame save = new SaveGame();
        save.setCharacter(hero);
        return save;
    }
}
