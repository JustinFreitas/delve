package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Formation;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MarchingOrderTest {

    private static final AbilityScores NEUTRAL_ABILITIES = new AbilityScores(9, 9, 9, 9, 9, 9);

    private final Dice dice = new Dice(new Random(37));
    private final CharacterFactory characterFactory = new CharacterFactory(dice);
    private final RetainerFactory retainerFactory = new RetainerFactory(dice);

    @Test
    void emptyMarchingOrderSortsTheWholePartyByToughness() {
        SaveGame save = partySave();
        Retainer bryn = save.getRetainers().get(0);
        Retainer cora = save.getRetainers().get(1);
        forceStats(save.getCharacter(), Armor.LEATHER, false, 10);
        forceStats(bryn, Armor.CHAIN_MAIL, true, 20); // tankiest: best AC
        forceStats(cora, Armor.NONE, false, 8); // squishiest: worst AC
        assertThat(save.getMarchingOrder()).isEmpty();

        List<Combatant> order = save.fullOrder();
        assertThat(order).containsExactly(bryn, save.getCharacter(), cora);
    }

    @Test
    void pcDefaultsTowardTheFrontWhenTougherThanRetainers() {
        SaveGame save = partySave();
        forceStats(save.getCharacter(), Armor.CHAIN_MAIL, true, 30); // tankiest
        forceStats(save.getRetainers().get(0), Armor.LEATHER, false, 15);
        forceStats(save.getRetainers().get(1), Armor.LEATHER, false, 10);

        List<Combatant> order = save.fullOrder();
        assertThat(order.get(0)).isEqualTo(save.getCharacter());
    }

    @Test
    void squishyPcDefaultsTowardTheBackAmongTankierRetainers() {
        SaveGame save = partySave();
        forceStats(save.getCharacter(), Armor.NONE, false, 6); // squishiest
        forceStats(save.getRetainers().get(0), Armor.CHAIN_MAIL, true, 20);
        forceStats(save.getRetainers().get(1), Armor.CHAIN_MAIL, true, 18);

        List<Combatant> order = save.fullOrder();
        assertThat(order.get(order.size() - 1)).isEqualTo(save.getCharacter());
    }

    @Test
    void unlistedLivingRetainersAreAutoAppendedAtTheBack() {
        SaveGame save = partySave();
        // Tied AC/HP so the stable sort's tiebreak (not toughness) determines their relative order.
        forceStats(save.getRetainers().get(0), Armor.CHAIN_MAIL, true, 15);
        forceStats(save.getRetainers().get(1), Armor.CHAIN_MAIL, true, 15);
        save.setMarchingOrder(List.of(SaveGame.PLAYER_SLOT)); // only the character listed
        List<Combatant> order = save.fullOrder();
        assertThat(order).hasSize(3);
        assertThat(order.get(0)).isEqualTo(save.getCharacter());
        assertThat(order.subList(1, 3)).containsExactlyElementsOf(save.getRetainers());
    }

    @Test
    void staleAndUnknownNamesAreToleratedNotThrown() {
        SaveGame save = partySave();
        Retainer bryn = save.getRetainers().get(0);
        save.setMarchingOrder(List.of("Nobody", bryn.getName(), SaveGame.PLAYER_SLOT));
        List<Combatant> order = save.fullOrder();
        assertThat(order).hasSize(3);
        assertThat(order.get(0)).isEqualTo(bryn); // the recognized name still resolves; "Nobody" is skipped
    }

    @Test
    void columnAssignmentIsStableAcrossADeathInAnotherColumn() {
        SaveGame save = partySave();
        save.setMarchingOrder(List.of(SaveGame.PLAYER_SLOT, "Bryn", "Cora"));
        Retainer bryn = save.getRetainers().get(0);
        Retainer cora = save.getRetainers().get(1);
        int width = 1; // single-file: each position is its own column
        int coraRankBefore = Formation.nominalRank(save.fullOrder(), width, cora);

        bryn.setCurrentHp(0); // a casualty in a different column must not shift anyone else's position
        int coraRankAfter = Formation.nominalRank(save.fullOrder(), width, cora);
        assertThat(coraRankAfter).isEqualTo(coraRankBefore);
    }

    @Test
    void soloOrderPlacementSurvivesAddingASecondPc() {
        SaveGame save = new SaveGame();
        Character hero = characterFactory.create("Hero", CharacterClass.MAGIC_USER, NEUTRAL_ABILITIES);
        save.setCharacter(hero);
        forceStats(hero, Armor.NONE, false, 4); // squishy: would sort to the back on toughness alone

        // A solo /order places the sole PC up front — persisted as the reserved @you token.
        save.setMarchingOrder(new ArrayList<>(List.of(SaveGame.PLAYER_SLOT)));

        Character tank = characterFactory.create("Tank", CharacterClass.FIGHTER, NEUTRAL_ABILITIES);
        forceStats(tank, Armor.CHAIN_MAIL, true, 20); // tougher: would seize the front if the hero were dropped
        assertThat(save.addCharacter(tank)).isTrue();

        // @you was rewritten to the hero's real name on the transition, so their explicit front
        // placement survives instead of resolving to null and falling into the toughness-sorted tail.
        assertThat(save.getMarchingOrder()).containsExactly("Hero");
        assertThat(save.fullOrder().get(0)).isEqualTo(hero);
    }

    /** Pins a combatant's AC-relevant stats to fixed values (same neutral abilities across every call,
        so DEX modifiers never perturb the AC math) — differentiate purely via armor/shield/HP. */
    private void forceStats(Combatant c, Armor armor, boolean shield, int hp) {
        if (c instanceof Character pc) {
            pc.setAbilities(NEUTRAL_ABILITIES);
            pc.setArmor(armor);
            pc.setShield(shield);
            pc.setMaxHp(hp);
            pc.setCurrentHp(hp);
        } else if (c instanceof Retainer r) {
            r.setAbilities(NEUTRAL_ABILITIES);
            r.setArmor(armor);
            r.setShield(shield);
            r.setMaxHp(hp);
            r.setCurrentHp(hp);
        }
    }

    private SaveGame partySave() {
        Character hero = characterFactory.create("Hero", CharacterClass.FIGHTER, new AbilityScores(13, 9, 9, 12, 12, 12));
        SaveGame save = new SaveGame();
        save.setCharacter(hero);
        save.getRetainers().add(retainerFactory.create("Bryn", CharacterClass.FIGHTER, 1, 9));
        save.getRetainers().add(retainerFactory.create("Cora", CharacterClass.FIGHTER, 1, 9));
        return save;
    }
}
