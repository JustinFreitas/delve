package dev.freitas.delve.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.session.BankService;
import org.junit.jupiter.api.Test;

/** {@link BankService} -- a town bank's 3% deposit fee and clamping at both ends, mirroring
    {@code MuleServiceTest}-style direct-domain-logic testing (no {@code CommandContext}). */
class BankServiceTest {

    private final BankService bank = new BankService();

    @Test
    void depositTakesA3PercentFeeAndCreditsTheRest() {
        Character pc = bareCharacter(1000);

        BankService.DepositResult result = bank.deposit(pc, 100);

        assertThat(result.deposited()).isEqualTo(100);
        assertThat(result.fee()).isEqualTo(3);
        assertThat(result.credited()).isEqualTo(97);
        assertThat(pc.getGold()).isEqualTo(900);
        assertThat(pc.getBankedGold()).isEqualTo(97);
    }

    @Test
    void depositIsClampedToWhatThePcIsCarrying() {
        Character pc = bareCharacter(50);

        BankService.DepositResult result = bank.deposit(pc, 1000);

        assertThat(result.deposited()).isEqualTo(50);
        assertThat(pc.getGold()).isZero();
        assertThat(pc.getBankedGold()).isEqualTo(50 - result.fee());
    }

    @Test
    void depositingZeroOrNegativeMovesNothing() {
        Character pc = bareCharacter(100);

        BankService.DepositResult zero = bank.deposit(pc, 0);
        BankService.DepositResult negative = bank.deposit(pc, -50);

        assertThat(zero.deposited()).isZero();
        assertThat(negative.deposited()).isZero();
        assertThat(pc.getGold()).isEqualTo(100);
        assertThat(pc.getBankedGold()).isZero();
    }

    @Test
    void depositingFromAnEmptyPurseMovesNothing() {
        Character pc = bareCharacter(0);

        BankService.DepositResult result = bank.deposit(pc, 100);

        assertThat(result.deposited()).isZero();
        assertThat(result.fee()).isZero();
        assertThat(result.credited()).isZero();
    }

    @Test
    void withdrawMovesGoldBackWithNoFee() {
        Character pc = bareCharacter(0);
        pc.setBankedGold(200);

        int withdrawn = bank.withdraw(pc, 150);

        assertThat(withdrawn).isEqualTo(150);
        assertThat(pc.getGold()).isEqualTo(150); // no fee taken
        assertThat(pc.getBankedGold()).isEqualTo(50);
    }

    @Test
    void withdrawIsClampedToWhatsBanked() {
        Character pc = bareCharacter(0);
        pc.setBankedGold(30);

        int withdrawn = bank.withdraw(pc, 100);

        assertThat(withdrawn).isEqualTo(30);
        assertThat(pc.getGold()).isEqualTo(30);
        assertThat(pc.getBankedGold()).isZero();
    }

    @Test
    void withdrawingFromAnEmptyAccountMovesNothing() {
        Character pc = bareCharacter(0);

        int withdrawn = bank.withdraw(pc, 100);

        assertThat(withdrawn).isZero();
        assertThat(pc.getGold()).isZero();
    }

    @Test
    void bankedGoldDoesNotCountTowardCarriedWeight() {
        Character pc = bareCharacter(0);
        pc.setTorches(0);

        bank.deposit(pc, 0); // no-op, just confirms baseline
        int weightBefore = pc.carriedWeightCns();
        pc.setBankedGold(5000); // a large sum, would be very heavy if it were carried gold
        int weightAfter = pc.carriedWeightCns();

        assertThat(weightAfter).isEqualTo(weightBefore);
    }

    private Character bareCharacter(int gold) {
        Character c = new Character();
        c.setName("Hero");
        c.setCharacterClass(CharacterClass.FIGHTER);
        c.setAbilities(new AbilityScores(9, 9, 9, 9, 9, 9));
        c.setMaxHp(10);
        c.setCurrentHp(10);
        c.setGold(gold);
        return c;
    }
}
