package dev.freitas.delve.game.session;

import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.Hands;
import dev.freitas.delve.game.engine.LightSource;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.GameSession;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import org.springframework.stereotype.Service;

/**
 * Owns the party's shared light source and who's physically holding it. Hand-eligibility is only ever
 * (re-)checked at two moments: lighting a fresh torch/lantern with nobody currently assigned
 * ({@link #lightUp}), and an explicit reassignment ({@link #assignBearer}) — once someone is holding
 * it, {@link #reconcileBearer} trusts that assignment turn-to-turn as long as they're still alive and
 * in the party, since loadout changes that would strand them are blocked at the source (see
 * {@code WieldCommand}).
 */
@Service
public class LightingService {

    /** Retainers (roster order) first, then each living PC in roster order — the classic B/X reason
        hirelings exist: a free-handed hireling carries the torch so a PC's hands stay free for a weapon
        and shield. */
    public String pickEligibleBearer(SaveGame save) {
        for (Retainer r : save.livingRetainers()) {
            if (hasFreeHand(r)) {
                return r.getName();
            }
        }
        for (Character c : save.getCharacters()) {
            if (c.isAlive() && hasFreeHand(c)) {
                return save.tokenFor(c);
            }
        }
        return null;
    }

    /** Attempts to set the current light bearer; returns null on success, or a reason string on failure. */
    public String assignBearer(SaveGame save, String token) {
        Combatant candidate = save.resolve(token);
        if (candidate == null || !candidate.isAlive()) {
            return "No living party member matches that.";
        }
        if (!hasFreeHand(candidate)) {
            return displayName(candidate) + "'s hands are already full.";
        }
        save.getSession().setLightBearer(save.tokenFor(candidate));
        return null;
    }

    /** Lights a fresh unit of the given source: consumes one unit of fuel (drawn from the party's shared
        supply, tracked on the primary PC), (re)assigns a bearer if none is currently eligible, and reports
        the outcome either way. Used by a fresh delve's first torch and by {@code /light}. */
    public boolean lightUp(SaveGame save, LightSource type, ExplorationResult result) {
        Character pc = save.getCharacter();
        if (!hasFuel(pc, type)) {
            result.add(type == LightSource.TORCH
                    ? "You have no torches left."
                    : "You need both a lantern and oil flasks to light it.");
            return false;
        }
        GameSession session = save.getSession();
        String bearer = session.getLightBearer();
        Combatant current = save.resolve(bearer);
        if (current == null || !current.isAlive() || !hasFreeHand(current)) {
            bearer = pickEligibleBearer(save);
        }
        if (bearer == null) {
            session.setActiveLight(null);
            session.setLightBearer(null);
            session.setInDarkness(true);
            result.add("No one has a free hand to hold a light — you remain in darkness.");
            return false;
        }
        consumeFuel(pc, type);
        session.setActiveLight(type);
        session.setLightTurnsRemaining(type.turnsPerUse());
        session.setLightBearer(bearer);
        session.setInDarkness(false);
        result.add(bearerVerb(save, bearer) + " a " + type.displayName() + ".");
        return true;
    }

    /** Re-validates the current bearer, called once per dungeon turn: an already-assigned, still-living
        bearer is trusted as-is; otherwise a real eligibility pick runs (covers death/desertion/dismissal). */
    public void reconcileBearer(SaveGame save, ExplorationResult result) {
        GameSession session = save.getSession();
        if (session.getActiveLight() == null) {
            return;
        }
        String token = session.getLightBearer();
        Combatant current = save.resolve(token);
        if (current != null && current.isAlive()) {
            return;
        }
        String replacement = pickEligibleBearer(save);
        session.setLightBearer(replacement);
        if (replacement == null) {
            if (session.isSpellLightActive()) {
                session.setActiveLight(null);
                session.setInDarkness(false);
                result.add("No one has a free hand for the light anymore, but magical light continues to illuminate your way.");
            } else {
                session.setActiveLight(null);
                session.setInDarkness(true);
                result.add("No one has a free hand for the light anymore — darkness falls!");
            }
        } else if (token != null) {
            result.add(displayName(save.resolve(replacement)) + " takes up the light.");
        }
    }

    /** Burns one turn of the active light, relighting from the same fuel type on depletion (or going
        dark) — called once per dungeon turn, after {@link #reconcileBearer}. */
    public void tickFuel(SaveGame save, ExplorationResult result) {
        GameSession session = save.getSession();
        if (session.getActiveLight() == null) {
            return;
        }
        session.setLightTurnsRemaining(session.getLightTurnsRemaining() - 1);
        if (session.getLightTurnsRemaining() > 0) {
            return;
        }
        LightSource type = session.getActiveLight();
        Character pc = save.getCharacter();
        if (hasFuel(pc, type)) {
            consumeFuel(pc, type);
            session.setLightTurnsRemaining(type.turnsPerUse());
            result.add(type == LightSource.TORCH
                    ? "_Your torch gutters out; you light another (" + pc.getTorches() + " left)._"
                    : "_Your lantern's oil runs dry; you refill it (" + pc.getOilFlasks() + " flasks left)._");
        } else {
            if (session.isSpellLightActive()) {
                session.setActiveLight(null);
                session.setLightBearer(null);
                session.setInDarkness(false);
                result.add(type == LightSource.TORCH
                        ? "**Your last torch burns out, but magical light continues to illuminate your way.**"
                        : "**Your last flask of oil runs dry, but magical light continues to illuminate your way.**");
            } else {
                session.setActiveLight(null);
                session.setLightBearer(null);
                session.setInDarkness(true);
                result.add(type == LightSource.TORCH
                        ? "**Your last torch burns out — you are plunged into darkness!**"
                        : "**Your last flask of oil runs dry — you are plunged into darkness!**");
            }
        }
    }

    private boolean hasFuel(Character pc, LightSource type) {
        return type == LightSource.TORCH ? pc.getTorches() > 0 : (pc.getLanterns() > 0 && pc.getOilFlasks() > 0);
    }

    private void consumeFuel(Character pc, LightSource type) {
        if (type == LightSource.TORCH) {
            pc.setTorches(pc.getTorches() - 1);
        } else {
            pc.setOilFlasks(pc.getOilFlasks() - 1);
        }
    }

    private boolean hasFreeHand(Combatant c) {
        if (c instanceof Character pc) {
            return Hands.free(pc.getMainWeapon(), pc.isShield(), false, pc.getOffHandWeapon() != null) >= 1;
        }
        if (c instanceof Retainer r) {
            return Hands.free(r.getMainWeapon(), r.isShield(), false) >= 1;
        }
        return false;
    }

    private String displayName(Combatant c) {
        return c instanceof Character ? "You" : c.getName();
    }

    private String bearerVerb(SaveGame save, String token) {
        Combatant c = save.resolve(token);
        return c instanceof Character ? "You light" : c.getName() + " lights";
    }
}
