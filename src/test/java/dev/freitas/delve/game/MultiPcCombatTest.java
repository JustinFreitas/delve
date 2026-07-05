package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.command.PartySummary;
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
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.SpellService;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MultiPcCombatTest {

    private final Dice dice = new Dice(new Random(31));
    private final CombatService combat = new CombatService(dice, new SpellService(dice));
    private final CharacterFactory factory = new CharacterFactory(dice);
    private final RetainerFactory retainerFactory = new RetainerFactory(dice);

    @Test
    void addingASecondPcIsAdditiveNotReplacing() {
        SaveGame save = new SaveGame();
        Character first = hero("Anna", 60);
        save.setCharacter(first);

        boolean added = save.addCharacter(hero("Bram", 50));

        assertThat(added).isTrue();
        assertThat(save.getCharacters()).extracting(Character::getName).containsExactly("Anna", "Bram");
        assertThat(save.getCharacter()).isSameAs(first); // primary-PC accessor still points at the first PC
    }

    @Test
    void addCharacterRejectsPastTheEightPcCap() {
        SaveGame save = new SaveGame();
        for (int i = 0; i < SaveGame.MAX_CHARACTERS; i++) {
            assertThat(save.addCharacter(hero("PC" + i, 10))).isTrue();
        }
        assertThat(save.addCharacter(hero("Overflow", 10))).isFalse();
        assertThat(save.getCharacters()).hasSize(SaveGame.MAX_CHARACTERS);
    }

    @Test
    void partySummaryListsEveryPcByNameWhenMultiPc() {
        SaveGame save = combatSave(Bestiary.ORC, 3);

        String text = PartySummary.text(save);

        assertThat(text).contains("Anna");
        assertThat(text).contains("Bram");
        assertThat(text).doesNotContain("(you)"); // only shown when solo
    }

    @Test
    void partySummaryShowsTheSumOfEveryPcsOwnRetainerCap() {
        // Each PC's Charisma cap is enforced independently (SaveGame#atRetainerCap) -- every PC can fill
        // their own cap regardless of what anyone else has hired -- so the party's real reachable
        // ceiling is the SUM of every PC's own cap, not just the highest single one (a plain max would
        // under-report how many retainers the party could legitimately end up with).
        SaveGame save = new SaveGame();
        Character lowCha = factory.create("Anna", CharacterClass.FIGHTER, new AbilityScores(16, 9, 9, 13, 13, 3));
        save.setCharacter(lowCha);
        Character highCha = factory.create("Bram", CharacterClass.CLERIC, new AbilityScores(9, 9, 9, 9, 9, 18));
        save.addCharacter(highCha);

        String text = PartySummary.text(save);

        int expectedSum = RetainerRules.maxRetainers(3) + RetainerRules.maxRetainers(18);
        assertThat(text).contains("0/" + expectedSum + " (Charisma cap)");
    }

    @Test
    void partySummaryAggregateCapCanActuallyHoldWhatThePartyLegitimatelyHires() {
        // Regression: under the old max-of-caps aggregate, two CHA-18 PCs each independently filling
        // their own 7-slot cap (14 retainers total, legitimate under the per-owner cap enforced
        // elsewhere) would have shown a nonsensical "14/7". The sum-based cap must actually be able to
        // contain everyone the party could legitimately hire.
        SaveGame save = new SaveGame();
        Character anna = factory.create("Anna", CharacterClass.FIGHTER, new AbilityScores(9, 9, 9, 9, 9, 18)); // cap 7
        save.setCharacter(anna);
        Character bram = factory.create("Bram", CharacterClass.CLERIC, new AbilityScores(9, 9, 9, 9, 9, 18)); // cap 7
        save.addCharacter(bram);
        for (int i = 0; i < 7; i++) {
            Retainer r = retainerFactory.create("AnnaHench" + i, CharacterClass.FIGHTER, 1, 9);
            r.setOwner("Anna");
            save.getRetainers().add(r);
        }
        for (int i = 0; i < 7; i++) {
            Retainer r = retainerFactory.create("BramHench" + i, CharacterClass.CLERIC, 1, 9);
            r.setOwner("Bram");
            save.getRetainers().add(r);
        }

        String text = PartySummary.text(save);

        assertThat(save.getRetainers()).hasSize(14);
        assertThat(text).contains("14/14 (Charisma cap)"); // not the old, impossible-looking "14/7"
    }

    @Test
    void partySummaryShowsEachPcsOwnOpenRetainerSlots() {
        SaveGame save = new SaveGame();
        Character lowCha = factory.create("Anna", CharacterClass.FIGHTER, new AbilityScores(16, 9, 9, 13, 13, 8)); // cap 3
        save.setCharacter(lowCha);
        Character highCha = factory.create("Bram", CharacterClass.CLERIC, new AbilityScores(9, 9, 9, 9, 9, 18)); // cap 7
        save.addCharacter(highCha);
        // Anna owns 2 retainers, Bram owns 1 -- each PC's open-slot count must be against their OWN
        // retainers, not the whole party's total (that old bug would say Bram has 7-3=4 open, not 6).
        Retainer annaHench1 = retainerFactory.create("AnnaHench1", CharacterClass.FIGHTER, 1, 9);
        annaHench1.setOwner("Anna");
        Retainer annaHench2 = retainerFactory.create("AnnaHench2", CharacterClass.FIGHTER, 1, 9);
        annaHench2.setOwner("Anna");
        Retainer bramHench = retainerFactory.create("BramHench", CharacterClass.CLERIC, 1, 9);
        bramHench.setOwner("Bram");
        save.getRetainers().add(annaHench1);
        save.getRetainers().add(annaHench2);
        save.getRetainers().add(bramHench);

        String text = PartySummary.text(save);

        // Anna's cap 3 minus her own 2 -> 1 open; Bram's cap 7 minus his own 1 -> 6 open.
        assertThat(text).contains("1 open slot");
        assertThat(text).contains("6 open slots");
    }

    @Test
    void partySummaryGroupsRetainersUnderTheirOwningPc() {
        SaveGame save = new SaveGame();
        save.setCharacter(hero("Anna", 60));
        save.addCharacter(hero("Bram", 60));
        Retainer annaHench = retainerFactory.create("AnnaHench", CharacterClass.FIGHTER, 1, 9);
        annaHench.setOwner("Anna");
        Retainer bramHench = retainerFactory.create("BramHench", CharacterClass.CLERIC, 1, 9);
        bramHench.setOwner("Bram");
        // Insertion order deliberately not owner order, to prove grouping isn't just insertion-order luck.
        save.getRetainers().add(bramHench);
        save.getRetainers().add(annaHench);

        String text = PartySummary.text(save);

        // Grouped by owner: Anna's row, then AnnaHench, then Bram's row -- not the old flat "every PC,
        // then every retainer" layout, which would put both retainers after both PC rows.
        assertThat(text.indexOf("AnnaHench")).isLessThan(text.indexOf("Bram"));
    }

    @Test
    void namingAPcInAttackGivesThemTheExplicitTargetWhileOthersAutoAttack() {
        SaveGame save = combatSave(Bestiary.ORC, 3);
        combat.startCombat(save);

        ExplorationResult result = combat.attackRound(save, "Bram", 1);

        // Both PCs act this round: Bram (named) explicitly, Anna automatically -- neither is "You" since
        // the party is no longer solo.
        assertThat(result.text()).contains("Bram");
        assertThat(result.text()).contains("Anna");
    }

    @Test
    void combatDoesNotEndInDefeatWhileAnyPcStillStands() {
        SaveGame save = combatSave(Bestiary.ORC, 3);
        Character anna = save.getCharacters().get(0);
        Character bram = save.getCharacters().get(1);
        combat.startCombat(save);
        anna.setCurrentHp(0); // Anna falls, but Bram is still standing

        combat.attackRound(save, "Bram", null);

        // The old single-PC defeat check (`!save.getCharacter().isAlive()`) would have wrongly ended the
        // delve here, since Anna -- the primary/first-rolled PC -- is dead. Defeat must require the whole
        // party (every living PC) to be down, not just the first one.
        assertThat(save.getSession().getState()).isNotEqualTo(SessionState.IN_TOWN);
        assertThat(bram.isAlive()).isTrue();
    }

    @Test
    void combatEndsInDefeatOnlyOnceEveryLivingPcIsDown() {
        SaveGame save = combatSave(Bestiary.ORC, 3);
        Character anna = save.getCharacters().get(0);
        Character bram = save.getCharacters().get(1);
        combat.startCombat(save);
        anna.setCurrentHp(0);
        bram.setCurrentHp(0);

        combat.attackRound(save, "Bram", null);

        assertThat(save.getSession().getState()).isEqualTo(SessionState.IN_TOWN);
        assertThat(save.getSession().getCombat()).isNull();
    }

    @Test
    void xpShareArithmeticCoversEveryLivingPcAndRetainer() {
        SaveGame save = combatSave(Bestiary.ORC, 3);
        Retainer hench = retainerFactory.create("Hench", CharacterClass.FIGHTER, 1, 9);
        hench.setMaxHp(40);
        hench.setCurrentHp(40);
        save.getRetainers().add(hench);
        Character anna = save.getCharacters().get(0);
        Character bram = save.getCharacters().get(1);

        combat.startCombat(save);
        for (int round = 0; round < 300 && save.getSession().getState() == SessionState.IN_COMBAT; round++) {
            combat.attackRound(save, null);
        }

        assertThat(save.getSession().getState()).isEqualTo(SessionState.EXPLORING);
        assertThat(anna.getXp()).isGreaterThan(0);
        assertThat(bram.getXp()).isGreaterThan(0);
        assertThat(hench.getXp()).isGreaterThan(0); // retainer earns a half-share
        assertThat(hench.getXp()).isLessThan(anna.getXp());
    }

    // --- helpers -----------------------------------------------------------

    private Character hero(String name, int hp) {
        Character c = factory.create(name, CharacterClass.FIGHTER, new AbilityScores(16, 9, 9, 13, 13, 12));
        c.setMaxHp(hp);
        c.setCurrentHp(hp);
        return c;
    }

    private SaveGame combatSave(MonsterType type, int count) {
        SaveGame save = new SaveGame();
        save.setCharacter(hero("Anna", 60));
        save.addCharacter(hero("Bram", 60));

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
