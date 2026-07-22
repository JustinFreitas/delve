package dev.freitas.delve.game.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.RetainerRules;
import dev.freitas.delve.game.engine.Toughness;

/**
 * Root of a player's persisted game state — the object serialized into {@code player_save.state_json}.
 * It grows over the milestones (party of retainers, in-progress dungeon run, session state); it holds
 * up to 8 player characters (a solo save has exactly one).
 */
public class SaveGame {

    /** Max PCs a single save may hold. */
    public static final int MAX_CHARACTERS = 8;

    /** Max mules a party may own — gygax75-rules: "a single mule may be brought into a dungeon by a
        party." No "spare mules waiting in town" concept exists elsewhere in delve (the whole party
        always delves together), so this stays a hard cap of one rather than modeling that. */
    public static final int MAX_MULES = 1;

    /** Reserved {@link #marchingOrder} token for a solo PC's own slot (kept alive as a "me" synonym —
        see {@link #resolve}); with 2+ PCs, tokens are real PC names instead, same as retainers. */
    public static final String PLAYER_SLOT = "@you";

    private java.util.List<Character> characters = new java.util.ArrayList<>();
    private GameSession session = new GameSession();
    private java.util.List<Retainer> retainers = new java.util.ArrayList<>();

    private java.util.List<Mule> mules = new java.util.ArrayList<>();

    /** Marching-order sequence of {@link #PLAYER_SLOT}/PC-name/retainer-name tokens; may reference dead
        members so column indices in {@link dev.freitas.delve.game.engine.Formation} stay stable as
        people fall. */
    private java.util.List<String> marchingOrder = new java.util.ArrayList<>();

    /** Wall-clock epoch millis of the party's last real (non-simulated) town visit — see {@code
        TownService#returnToTown}, which caps rest days by real elapsed time since this. Null before the
        first real town visit ever resolves; {@code TownService} treats that the same as "now" (no
        backlog), matching the dynamic-fallback pattern used elsewhere for pre-existing saves. Simulated
        rests ({@code TownService#simulateReturnToTown}, used by {@code AutodelveService}'s compressed
        multi-delve runs) never read or write this. */
    private Long lastTownVisitMillis;

    /** JSON serialized backups of the last 3-5 exploration turns for undo/redo rollbacks. */
    private java.util.List<String> history = new java.util.ArrayList<>();

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
        // Solo → multi-PC transition: the sole PC's marching-order slot was persisted as the reserved
        // PLAYER_SLOT ("@you") token, which resolve() maps back to a PC only while exactly one exists.
        // Rewrite it to that PC's real name before the second PC lands, so their explicit /order
        // placement survives instead of resolving to null and being dropped to the toughness-sorted tail.
        if (characters.size() == 1) {
            String primaryName = characters.get(0).getName();
            marchingOrder.replaceAll(token -> PLAYER_SLOT.equalsIgnoreCase(token) ? primaryName : token);
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

    public java.util.List<Mule> getMules() {
        return mules;
    }

    /** Mules still able to accompany the party (alive) — in practice always all of {@link #getMules()},
        since a mule that dies in combat is removed on the spot ({@code CombatService}), but kept
        consistent with {@link #livingCharacters()}/{@link #livingRetainers()} for callers (party-size,
        group movement rate) that shouldn't have to know that. */
    public java.util.List<Mule> livingMules() {
        java.util.List<Mule> alive = new java.util.ArrayList<>();
        for (Mule m : mules) {
            if (m.isAlive()) {
                alive.add(m);
            }
        }
        return alive;
    }

    public void setMules(java.util.List<Mule> mules) {
        this.mules = mules;
    }

    /** Resolves a mule by name (case-insensitive), or {@code null} if the party has none matching. Mules
        are also {@link dev.freitas.delve.game.engine.Combatant}s and resolvable via {@link #resolve} for
        marching-order/combat purposes; this typed lookup is for mule-specific commands ({@code /mule},
        {@code /dismiss}) that want a {@link Mule} directly instead of a generic {@code Combatant}. */
    @JsonIgnore
    public Mule findMule(String name) {
        if (name == null) {
            return null;
        }
        for (Mule m : mules) {
            if (name.equalsIgnoreCase(m.getName())) {
                return m;
            }
        }
        return null;
    }

    /** The party's one mule, or {@code null} if it hasn't bought one — a convenience for call sites
        that don't need to name it (there's only ever {@link #MAX_MULES}). */
    @JsonIgnore
    public Mule getMule() {
        return mules.isEmpty() ? null : mules.get(0);
    }

    public java.util.List<String> getMarchingOrder() {
        return marchingOrder;
    }

    public void setMarchingOrder(java.util.List<String> marchingOrder) {
        this.marchingOrder = marchingOrder;
    }

    public Long getLastTownVisitMillis() {
        return lastTownVisitMillis;
    }

    public void setLastTownVisitMillis(Long lastTownVisitMillis) {
        this.lastTownVisitMillis = lastTownVisitMillis;
    }

    /**
     * The whole party (every PC + every retainer + the mule, alive or dead) in marching-order sequence —
     * the list {@link dev.freitas.delve.game.engine.Formation} groups into ranks/columns. Dead members
     * are kept in place so column indices don't shift when someone falls; any current roster member not
     * yet listed in {@link #marchingOrder} is appended at the back, ordered by toughness via
     * {@link Toughness#BY_TOUGHNESS} (better-armored, higher-HP combatants first) — so a heavily armored
     * Fighter or Dwarf, whether a PC or a retainer, defaults toward the front of that auto-appended tail,
     * while a Magic-User, Thief, or the mule (AC 7, 9hp) defaults toward the back, unless explicitly
     * placed elsewhere via {@code /order}. A newly hired retainer, newly rolled PC, or newly bought mule
     * simply joins this sortable pool without any changes needed at the hire/roll/buy site.
     */
    @JsonIgnore
    public java.util.List<Combatant> fullOrder() {
        java.util.List<Combatant> ordered = new java.util.ArrayList<>();
        // Identity-keyed (not equals/hashCode) so two members can never collide however they compare.
        java.util.Set<Combatant> placed =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (String token : marchingOrder) {
            Combatant resolved = resolve(token);
            if (resolved != null && placed.add(resolved)) {
                ordered.add(resolved);
            }
        }
        java.util.List<Combatant> unplaced = new java.util.ArrayList<>();
        for (Retainer r : retainers) {
            if (placed.add(r)) {
                unplaced.add(r);
            }
        }
        for (Mule m : mules) {
            if (placed.add(m)) {
                unplaced.add(m);
            }
        }
        for (Character c : characters) {
            if (placed.add(c)) {
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
        a PC once there are 2+), else a PC name, else a retainer name, else the mule's name. The single
        shared replacement for what used to be three independent copies of this lookup. */
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
        for (Mule m : mules) {
            if (m.getName().equalsIgnoreCase(token)) {
                return m;
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

    /** The PC that owns {@code r}: the retainer's stored owner name resolved to a live {@link
        Character} in this save (case-insensitive, matching {@link #resolve}'s convention), falling
        back to the primary PC ({@link #getCharacter()}) when the retainer predates ownership (owner
        is null/blank) or its stored owner name no longer matches any current PC. */
    @JsonIgnore
    public Character ownerOf(Retainer r) {
        String name = r.getOwner();
        if (name != null && !name.isBlank()) {
            for (Character c : characters) {
                if (c.getName().equalsIgnoreCase(name)) {
                    return c;
                }
            }
        }
        return getCharacter();
    }

    /** The PC that owns {@code mule}, same fallback convention as {@link #ownerOf(Retainer)}. */
    @JsonIgnore
    public Character ownerOf(Mule mule) {
        String name = mule.getOwner();
        if (name != null && !name.isBlank()) {
            for (Character c : characters) {
                if (c.getName().equalsIgnoreCase(name)) {
                    return c;
                }
            }
        }
        return getCharacter();
    }

    /** Every retainer owned by {@code pc}, per {@link #ownerOf} (identity comparison against {@code
        pc}, not name comparison, so this can't be confused by two PCs sharing a name). */
    @JsonIgnore
    public java.util.List<Retainer> retainersOwnedBy(Character pc) {
        java.util.List<Retainer> owned = new java.util.ArrayList<>();
        for (Retainer r : retainers) {
            if (ownerOf(r) == pc) {
                owned.add(r);
            }
        }
        return owned;
    }

    /** This PC's Charisma-based retainer cap (see {@link RetainerRules#maxRetainers}). */
    @JsonIgnore
    public int retainerCapFor(Character pc) {
        return RetainerRules.maxRetainers(pc.getAbilities().score(Ability.CHA));
    }

    /** Whether {@code pc} has hit their own Charisma-based retainer cap and cannot hire another right
        now — the single source of truth for this rule, shared by every hire path ({@code HireCommand},
        {@code GameFacade}) instead of each re-deriving it from {@link #retainersOwnedBy} and
        {@link RetainerRules#maxRetainers} independently. */
    @JsonIgnore
    public boolean atRetainerCap(Character pc) {
        return retainersOwnedBy(pc).size() >= retainerCapFor(pc);
    }

    /** The result of {@link #peelLeadingPcName}: the resolved acting PC and the remaining argument
        text with that leading token removed (unchanged if nothing was peeled). */
    public record PcNameToken(Character actor, String remainder) {}

    /** Peels an optional leading PC-name token off {@code argsText}, for commands that accept an
        optional {@code [pc-name] ...} prefix in a multi-PC party (see {@code /hire}, {@code /dismiss}):
        if the text up to the first space resolves to a live PC via {@link #resolve}, that PC becomes
        the actor and the trimmed remainder is returned; otherwise {@code defaultActor} and the full
        text are returned unchanged. Callers whose remaining argument shape could itself contain a
        space-separated value matching a PC's name (e.g. a multi-word retainer name) should check that
        case first and skip calling this when it applies. */
    @JsonIgnore
    public PcNameToken peelLeadingPcName(String argsText, Character defaultActor) {
        int leadSpace = argsText.indexOf(' ');
        String leadToken = leadSpace > 0 ? argsText.substring(0, leadSpace) : argsText;
        if (leadSpace > 0 && resolve(leadToken) instanceof Character named) {
            return new PcNameToken(named, argsText.substring(leadSpace + 1).trim());
        }
        return new PcNameToken(defaultActor, argsText);
    }

    public java.util.List<String> getHistory() {
        return history;
    }

    public void setHistory(java.util.List<String> history) {
        this.history = history == null ? new java.util.ArrayList<>() : history;
    }
}
