package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.GameClock;
import dev.freitas.delve.game.engine.LodgingTier;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.ExplorationResult;
import dev.freitas.delve.game.session.SpellService;
import dev.freitas.delve.game.session.TownService;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** gygax75-rules' Inn-tier lodging during a {@code /town} stay: {@link LodgingTier#ROOM} (default, no
    consequence), {@link LodgingTier#DORMITORY} ("Retainers will quit service" -- unconditional), and
    {@link LodgingTier#SHARED_ROOM} (a per-full-week loyalty roll may cost a PC their retainers). */
class LodgingTierTest {

    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private final Dice dice = new Dice(new Random(91));
    private final TownService town = new TownService(new SpellService(dice), dice, new GameClock(), new dev.freitas.delve.config.GameProps());

    /** Backdates the save's last-real-visit clock so the real-time rest cap doesn't bind. */
    private static void allowRestDays(SaveGame save, int days) {
        save.setLastTownVisitMillis(System.currentTimeMillis() - days * DAY_MILLIS - 60_000);
    }

    @Test
    void roomTierChargesGpPerDayWithNoRetainerConsequence() {
        SaveGame save = new SaveGame();
        Character pc = bareCharacter("Hero", 1000);
        save.setCharacter(pc);
        Retainer r = bareRetainer("Bryn", pc.getName(), 7);
        save.getRetainers().add(r);
        allowRestDays(save, 5);

        town.returnToTown(save, 5, LodgingTier.ROOM);

        // Plus TownService's existing flat 10 gp/retainer upkeep, unrelated to lodging tier.
        assertThat(pc.getGold()).isEqualTo(1000 - LodgingTier.ROOM.gpPerDay() * 5 - 10);
        assertThat(save.getRetainers()).containsExactly(r); // untouched
        assertThat(r.getLoyalty()).isEqualTo(7); // untouched
    }

    @Test
    void dormitoryTierDesertsEveryOwnedRetainerUnconditionally() {
        SaveGame save = new SaveGame();
        Character pc = bareCharacter("Hero", 1000);
        save.setCharacter(pc);
        // Max loyalty -- would never fail a roll-based check, but Dormitory has no roll at all.
        Retainer r1 = bareRetainer("Bryn", pc.getName(), 12);
        Retainer r2 = bareRetainer("Nessa", pc.getName(), 12);
        save.getRetainers().add(r1);
        save.getRetainers().add(r2);
        allowRestDays(save, 3);

        ExplorationResult result = town.returnToTown(save, 3, LodgingTier.DORMITORY);

        assertThat(save.getRetainers()).isEmpty();
        // Plus TownService's existing flat 10 gp/retainer upkeep (both retainers), unrelated to lodging.
        assertThat(pc.getGold()).isEqualTo(1000 - LodgingTier.DORMITORY.gpPerDay() * 3 - 2 * 10);
        assertThat(result.text()).contains("quit");
    }

    @Test
    void sharedRoomTierNeverDesertsAMaxLoyaltyRetainer() {
        SaveGame save = new SaveGame();
        Character pc = bareCharacter("Hero", 1000);
        save.setCharacter(pc);
        // 2d6 can roll at most 12, so "> 12" can never be true -- this retainer can never fail the check.
        Retainer r = bareRetainer("Bryn", pc.getName(), 12);
        save.getRetainers().add(r);
        allowRestDays(save, 21); // three full weeks -- three separate checks

        town.returnToTown(save, 21, LodgingTier.SHARED_ROOM);

        assertThat(save.getRetainers()).containsExactly(r);
    }

    @Test
    void sharedRoomTierEventuallyDesertsAVeryLowLoyaltyRetainer() {
        // Loyalty 2: only a roll of exactly 2 (2d6's minimum) avoids desertion -- across many
        // independent trials of a full week's stay, this must fail at least once.
        boolean deserted = false;
        for (int seed = 0; seed < 100 && !deserted; seed++) {
            Dice trialDice = new Dice(new Random(seed));
            TownService trialTown = new TownService(new SpellService(trialDice), trialDice, new GameClock(), new dev.freitas.delve.config.GameProps());
            SaveGame save = new SaveGame();
            Character pc = bareCharacter("Hero", 1000);
            save.setCharacter(pc);
            Retainer r = bareRetainer("Bryn", pc.getName(), 2);
            save.getRetainers().add(r);
            allowRestDays(save, 7);

            trialTown.returnToTown(save, 7, LodgingTier.SHARED_ROOM);
            deserted = save.getRetainers().isEmpty();
        }
        assertThat(deserted).isTrue();
    }

    @Test
    void sharedRoomTierOnlyChecksOnCompletedFullWeeks() {
        SaveGame save = new SaveGame();
        Character pc = bareCharacter("Hero", 1000);
        save.setCharacter(pc);
        Retainer r = bareRetainer("Bryn", pc.getName(), 2); // lowest possible loyalty
        save.getRetainers().add(r);
        allowRestDays(save, 3); // under a full week -- zero checks, regardless of loyalty

        town.returnToTown(save, 3, LodgingTier.SHARED_ROOM);

        assertThat(save.getRetainers()).containsExactly(r);
    }

    @Test
    void lodgingCostIsClampedToWhatThePcHasOnHand() {
        SaveGame save = new SaveGame();
        Character pc = bareCharacter("Hero", 5);
        save.setCharacter(pc);
        allowRestDays(save, 7);

        town.returnToTown(save, 7, LodgingTier.ROOM); // would cost 70 gp -- only has 5

        assertThat(pc.getGold()).isZero(); // clamped, never negative
    }

    @Test
    void zeroDaysChargesNoLodgingCostAtAll() {
        SaveGame save = new SaveGame();
        Character pc = bareCharacter("Hero", 1000);
        save.setCharacter(pc);
        // No real time has passed since the (implicit) last visit -- days resolves to 0.

        town.returnToTown(save, 7, LodgingTier.DORMITORY);

        assertThat(pc.getGold()).isEqualTo(1000); // unchanged -- not a new visit
    }

    private Character bareCharacter(String name, int gold) {
        Character c = new Character();
        c.setName(name);
        c.setCharacterClass(CharacterClass.FIGHTER);
        c.setAbilities(new AbilityScores(9, 9, 9, 9, 9, 9));
        c.setMaxHp(10);
        c.setCurrentHp(10);
        c.setGold(gold);
        return c;
    }

    private Retainer bareRetainer(String name, String owner, int loyalty) {
        Retainer r = new Retainer();
        r.setName(name);
        r.setCharacterClass(CharacterClass.FIGHTER);
        r.setMaxHp(10);
        r.setCurrentHp(10);
        r.setOwner(owner);
        r.setLoyalty(loyalty);
        return r;
    }
}
