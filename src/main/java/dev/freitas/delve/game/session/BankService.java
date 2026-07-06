package dev.freitas.delve.game.session;

import dev.freitas.delve.game.model.Character;
import org.springframework.stereotype.Service;

/**
 * A town bank: gold deposited here stops counting toward {@link Character#carriedWeightCns()} (see
 * {@link Character#getBankedGold()}) — the whole point of banking it — at the cost of a flat handling
 * fee taken off the top (gygax75-rules' "Money Coin Exchange / Banking Fee 3% fee"). Withdrawing back
 * out is free; you're only retrieving your own stored coin. Mirrors {@link MuleService#load}/
 * {@link MuleService#unload}'s exact shape: pure domain logic, clamped at both ends, returning what
 * actually happened rather than throwing.
 */
@Service
public class BankService {

    private static final int DEPOSIT_FEE_PERCENT = 3;

    /** How much of a deposit attempt actually moved: {@code deposited} left the payer's purse,
        {@code fee} was the bank's cut, and {@code credited} (deposited minus fee) is what actually
        landed in the bank. */
    public record DepositResult(int deposited, int fee, int credited) {}

    /** Moves gold from {@code payer}'s purse into their bank balance, clamped to what they're actually
        carrying, minus a flat {@value #DEPOSIT_FEE_PERCENT}% handling fee. */
    public DepositResult deposit(Character payer, int amount) {
        int deposited = Math.max(0, Math.min(amount, payer.getGold()));
        int fee = deposited * DEPOSIT_FEE_PERCENT / 100;
        int credited = deposited - fee;
        payer.setGold(payer.getGold() - deposited);
        payer.setBankedGold(payer.getBankedGold() + credited);
        return new DepositResult(deposited, fee, credited);
    }

    /** Moves gold from {@code payee}'s bank balance back onto their purse — returns the amount actually
        withdrawn (may be less than requested, clamped to what's banked). No fee. */
    public int withdraw(Character payee, int amount) {
        int withdrawn = Math.max(0, Math.min(amount, payee.getBankedGold()));
        payee.setBankedGold(payee.getBankedGold() - withdrawn);
        payee.setGold(payee.getGold() + withdrawn);
        return withdrawn;
    }
}
