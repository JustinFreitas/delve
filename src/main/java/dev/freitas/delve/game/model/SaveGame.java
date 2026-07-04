package dev.freitas.delve.game.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.Toughness;

/**
 * Root of a player's persisted game state — the object serialized into {@code player_save.state_json}.
 * It grows over the milestones (party of retainers, in-progress dungeon run, session state); it holds
 * up to 8 player characters (a solo save has exactly one).
 */
public class SaveGame {

    /** Max PCs a single save may hold. */
    public static final int MAX_CHARACTERS = 8;

    /** Reserved {@link #marchingOrder} token for a solo PC's own slot (kept alive as a "me" synonym —
        see {@link #resolve}); with 2+ PCs, tokens are real PC names instead, same as retainers. */
    public static final String PLAYER_SLOT = "@you";

    private java.util.List<Character> characters = new java.util.ArrayList<>();
    private GameSession session = new GameSession();
    private java.util.List<Retainer> retainers = new java.util.ArrayList<>();

    /** Marching-order sequence of {@link #PLAYER_SLOT}/PC-name/retainer-name tokens; may reference dead
        members so column indices in {@link dev.freitas.delve.game.engine.Formation} stay stable as
        people fall. */
    private java.util.List<String> marchingOrder = new java.util.ArrayList<>();

    public SaveGame() {}

    public boolean hasCharacter() {
        return !characters.isEmpty();
    }

    /** The primary (first-rolled) PC — a permanent, well-defined accessor ("your first/main character"),
        not a transitional shim. Every command not yet updated for multiple PCs keeps using this safely. */
    @JsonIgnore
    public Character getCharacter() {
        return characters.isEmpty() ? null : characters.get(0);
    }

    /** Legacy-shape bridge: old saves persisted a single {@code "character"} JSON key. Deserializing one
        populates {@link #characters} (Jackson's {@code FAIL_ON_UNKNOWN_PROPERTIES=false} would otherwise
        silently drop this key once the field is renamed, quietly wiping the save). Also still the normal
        way to set/replace the primary PC. Serialization only ever writes {@code "characters"} (see the
        {@code @JsonIgnore} getter above), so every save upgrades itself in place on its next write. */
    @JsonProperty("character")
    public void setCharacter(Character character) {
        if (character == null) {
            return;
        }
        if (characters.isEmpty()) {
            characters.add(character);
        } else {
            characters.set(0, character);
        }
    }

    public java.util.List<Character> getCharacters() {
        return characters;
    }

    public void setCharacters(java.util.List<Character> characters) {
        this.characters = characters;
    }

    /** Adds a new PC, up to {@link #MAX_CHARACTERS}. Returns {@code false} (does not add) at the cap. */
    public boolean addCharacter(Character character) {
        if (characters.size() >= MAX_CHARACTERS) {
            return false;
        }
        characters.add(character);
        return true;
    }

    /** PCs still able to fight (alive). */
    @JsonIgnore
    public java.util.List<Character> livingCharacters() {
        java.util.List<Character> alive = new java.util.ArrayList<>();
        for (Character c : characters) {
            if (c.isAlive()) {
                alive.add(c);
            }
        }
        return alive;
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
     * The whole party (every PC + every retainer, alive or dead) in marching-order sequence — the list
     * {@link dev.freitas.delve.game.engine.Formation} groups into ranks/columns. Dead members are kept in
     * place so column indices don't shift when someone falls; any current roster member not yet listed in
     * {@link #marchingOrder} is appended at the back, ordered by toughness via
     * {@link Toughness#BY_TOUGHNESS} (better-armored, higher-HP combatants first) — so a heavily armored
     * Fighter or Dwarf, whether a PC or a retainer, defaults toward the front of that auto-appended tail,
     * while a Magic-User or Thief defaults toward the back, unless explicitly placed elsewhere via
     * {@code /order}. A newly hired retainer or newly rolled PC simply joins this sortable pool without
     * any changes needed at the hire/roll site.
     */
    @JsonIgnore
    public java.util.List<Combatant> fullOrder() {
        java.util.List<Combatant> ordered = new java.util.ArrayList<>();
        java.util.Set<String> placed = new java.util.HashSet<>();
        for (String token : marchingOrder) {
            Combatant resolved = resolve(token);
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
        for (Character c : characters) {
            if (placed.add(keyFor(c))) {
                unplaced.add(c);
            }
        }
        unplaced.sort(Toughness.BY_TOUGHNESS);
        ordered.addAll(unplaced);
        return ordered;
    }

    /** {@link #fullOrder()} filtered to combatants still able to act, for simple round-order iteration. */
    @JsonIgnore
    public java.util.List<Combatant> livingInOrder() {
        java.util.List<Combatant> alive = new java.util.ArrayList<>();
        for (Combatant c : fullOrder()) {
            if (c.isAlive()) {
                alive.add(c);
            }
        }
        return alive;
    }

    /** Resolves a marching-order/light-bearer/command-argument token to a party combatant: {@code "me"}
        or {@link #PLAYER_SLOT} only when exactly one PC exists (ambiguous otherwise — callers must name
        a PC once there are 2+), else a PC name, else a retainer name. The single shared replacement for
        what used to be three independent copies of this lookup. */
    @JsonIgnore
    public Combatant resolve(String token) {
        if (token == null) {
            return null;
        }
        if (characters.size() == 1 && (token.equalsIgnoreCase("me") || PLAYER_SLOT.equalsIgnoreCase(token))) {
            return characters.get(0);
        }
        for (Character c : characters) {
            if (c.getName().equalsIgnoreCase(token)) {
                return c;
            }
        }
        for (Retainer r : retainers) {
            if (r.getName().equalsIgnoreCase(token)) {
                return r;
            }
        }
        return null;
    }

    /** The write-side mirror of {@link #resolve}: the token to persist (marching order, light bearer) for
        a given combatant — a PC's own name, or {@link #PLAYER_SLOT} if they're the sole PC; a retainer's
        name unchanged. */
    @JsonIgnore
    public String tokenFor(Combatant c) {
        if (c instanceof Character character) {
            return characters.size() == 1 ? PLAYER_SLOT : character.getName();
        }
        return c.getName();
    }

    private String keyFor(Combatant c) {
        return "k:" + System.identityHashCode(c);
    }
}
