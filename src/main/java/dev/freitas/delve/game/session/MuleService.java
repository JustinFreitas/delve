package dev.freitas.delve.game.session;

import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.Hands;
import dev.freitas.delve.game.engine.MuleRules;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Mule;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Service;

/**
 * Owns the party's mule handler assignment: someone (a PC or retainer) leads it, and that costs a hand
 * just like holding the party's light does — see {@link Hands}. Mirrors {@link LightingService}'s
 * bearer-assignment pattern (pick/assign/reconcile), since a mule handler and a light bearer are the
 * same kind of "needs a free hand, re-picked on death/desertion" role.
 */
@Service
public class MuleService {

    /** Retainers (roster order) first, then each living PC in roster order — same precedence
        {@link LightingService#pickEligibleBearer} uses, and for the same reason: a free-handed hireling
        leads the mule so a PC's hands stay free for a weapon and shield. */
    public String pickEligibleHandler(SaveGame save) {
        for (Retainer r : save.livingRetainers()) {
            if (hasFreeHand(save, r)) {
                return r.getName();
            }
        }
        for (Character c : save.getCharacters()) {
            if (c.isAlive() && hasFreeHand(save, c)) {
                return save.tokenFor(c);
            }
        }
        return null;
    }

    /** Attempts to set the mule's handler; returns null on success, or a reason string on failure. */
    public String assignHandler(SaveGame save, Mule mule, String token) {
        Combatant candidate = save.resolve(token);
        if (candidate == null || !candidate.isAlive()) {
            return "No living party member matches that.";
        }
        if (!hasFreeHand(save, candidate)) {
            return displayName(candidate) + "'s hands are already full.";
        }
        mule.setHandler(save.tokenFor(candidate));
        return null;
    }

    /** Re-validates the mule's handler, called once per dungeon turn alongside
        {@link LightingService#reconcileBearer}: an already-assigned, still-living, still-free-handed
        handler is trusted as-is; otherwise a real eligibility pick runs (covers death/desertion/
        dismissal/a weapon or shield change that fills their hands). */
    public void reconcileHandler(SaveGame save, ExplorationResult result) {
        Mule mule = save.getMule();
        if (mule == null) {
            return;
        }
        Combatant current = save.resolve(mule.getHandler());
        if (current != null && current.isAlive() && hasFreeHand(save, current)) {
            return;
        }
        String replacement = pickEligibleHandler(save);
        String previous = mule.getHandler();
        mule.setHandler(replacement);
        if (replacement == null) {
            result.add("No one has a free hand to lead " + mule.getName() + " anymore.");
        } else if (previous != null) {
            result.add(displayName(save.resolve(replacement)) + " takes up " + mule.getName() + "'s lead.");
        }
    }

    /** Moves gold from {@code payer}'s purse onto the mule, clamped to {@link MuleRules#CAPACITY_MAX_CNS}
        — returns the amount actually loaded (may be less than requested). */
    public int load(Mule mule, Character payer, int amount) {
        int loadable = Math.min(amount, payer.getGold());
        loadable = Math.min(loadable, MuleRules.capacityRemaining(mule.getCarriedGold()));
        loadable = Math.max(0, loadable);
        payer.setGold(payer.getGold() - loadable);
        mule.setCarriedGold(mule.getCarriedGold() + loadable);
        return loadable;
    }

    /** Moves gold from the mule back onto {@code payee}'s purse — returns the amount actually unloaded
        (may be less than requested, clamped to what the mule is carrying). */
    public int unload(Mule mule, Character payee, int amount) {
        int unloadable = Math.max(0, Math.min(amount, mule.getCarriedGold()));
        mule.setCarriedGold(mule.getCarriedGold() - unloadable);
        payee.setGold(payee.getGold() + unloadable);
        return unloadable;
    }

    private boolean hasFreeHand(SaveGame save, Combatant c) {
        String bearerToken = save.getSession().getLightBearer();
        boolean isBearer = bearerToken != null && bearerToken.equalsIgnoreCase(save.tokenFor(c));
        if (c instanceof Character pc) {
            return Hands.free(pc.getMainWeapon(), pc.isShield(), isBearer, pc.getOffHandWeapon() != null) >= 1;
        }
        if (c instanceof Retainer r) {
            return Hands.free(r.getMainWeapon(), r.isShield(), isBearer) >= 1;
        }
        return false;
    }

    private String displayName(Combatant c) {
        return c instanceof Character ? "You" : c.getName();
    }
}
