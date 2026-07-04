package dev.freitas.delve.game.model;

import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.Toughness;

/**
 * Root of a player's persisted game state — the object serialized into {@code player_save.state_json}.
 * It grows over the milestones (party of retainers, in-progress dungeon run, session state); for now
 * it holds the active character.
 */
public class SaveGame {

    /** Reserved {@link #marchingOrder} token for the player character's own slot. */
    public static final String PLAYER_SLOT = "@you";

    private Character character;
    private GameSession session = new GameSession();
    private java.util.List<Retainer> retainers = new java.util.ArrayList<>();

    /** Marching-order sequence of {@link #PLAYER_SLOT}/retainer-name tokens; may reference dead members
        so column indices in {@link dev.freitas.delve.game.engine.Formation} stay stable as people fall. */
    private java.util.List<String> marchingOrder = new java.util.ArrayList<>();

    public SaveGame() {}

    public boolean hasCharacter() {
        return character != null;
    }

    public Character getCharacter() {
        return character;
    }

    public void setCharacter(Character character) {
        this.character = character;
    }

    public GameSession getSession() {
        return session;
    }

    public void setSession(GameSession session) {
        this.session = session;
    }

    public java.util.List<Retainer> getRetainers() {
        return retainers;
    }

    public void setRetainers(java.util.List<Retainer> retainers) {
        this.retainers = retainers;
    }

    /** Retainers still able to fight (alive and not fled). */
    public java.util.List<Retainer> livingRetainers() {
        java.util.List<Retainer> alive = new java.util.ArrayList<>();
        for (Retainer r : retainers) {
            if (r.isAlive()) {
                alive.add(r);
            }
        }
        return alive;
    }

    public java.util.List<String> getMarchingOrder() {
        return marchingOrder;
    }

    public void setMarchingOrder(java.util.List<String> marchingOrder) {
        this.marchingOrder = marchingOrder;
    }

    /**
     * The whole party (character + every retainer, alive or dead) in marching-order sequence — the
     * list {@link dev.freitas.delve.game.engine.Formation} groups into ranks/columns. Dead members are
     * kept in place so column indices don't shift when someone falls; any current roster member not yet
     * listed in {@link #marchingOrder} is appended at the back, ordered by toughness via
     * {@link Toughness#BY_TOUGHNESS} (better-armored, higher-HP combatants first) — so a heavily
     * armored Fighter or Dwarf, whether the PC or a retainer, defaults toward the front of that
     * auto-appended tail, while a Magic-User or Thief defaults toward the back, unless explicitly placed
     * elsewhere via {@code /order}. A newly hired retainer simply joins this sortable pool without any
     * changes needed at the hire site.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public java.util.List<Combatant> fullOrder() {
        java.util.List<Combatant> ordered = new java.util.ArrayList<>();
        java.util.Set<String> placed = new java.util.HashSet<>();
        for (String token : marchingOrder) {
            Combatant resolved = resolveSlot(token);
            if (resolved != null && placed.add(keyFor(resolved))) {
                ordered.add(resolved);
            }
        }
        java.util.List<Combatant> unplaced = new java.util.ArrayList<>();
        for (Retainer r : retainers) {
            if (placed.add(keyFor(r))) {
                unplaced.add(r);
            }
        }
        if (character != null && placed.add(keyFor(character))) {
            unplaced.add(character);
        }
        unplaced.sort(Toughness.BY_TOUGHNESS);
        ordered.addAll(unplaced);
        return ordered;
    }

    /** {@link #fullOrder()} filtered to combatants still able to act, for simple round-order iteration. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public java.util.List<Combatant> livingInOrder() {
        java.util.List<Combatant> alive = new java.util.ArrayList<>();
        for (Combatant c : fullOrder()) {
            if (c.isAlive()) {
                alive.add(c);
            }
        }
        return alive;
    }

    private Combatant resolveSlot(String token) {
        if (PLAYER_SLOT.equalsIgnoreCase(token)) {
            return character;
        }
        for (Retainer r : retainers) {
            if (r.getName().equalsIgnoreCase(token)) {
                return r;
            }
        }
        return null;
    }

    private String keyFor(Combatant c) {
        return c == character ? PLAYER_SLOT : "r:" + System.identityHashCode(c);
    }
}

