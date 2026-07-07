package dev.freitas.delve.game.engine;

import java.util.List;
import java.util.Map;

/**
 * The B/X character classes (races are classes in B/X): the 7 standard classes, plus 7 optional
 * gygax75-rules custom classes (see {@link #isCustom()}, gated per-DM by {@code GameProps
 * #isClassEnabled}). Carries the data needed for character creation and the sheet: hit die, prime
 * requisite(s), minimum ability requirements, and the XP threshold for level 2. Fuller per-level XP
 * tables arrive with advancement (Milestone 7).
 */
public enum CharacterClass {
    CLERIC("Cleric", 6, List.of(Ability.WIS), Map.of(), 1500, Spell.Tradition.DIVINE, false),
    FIGHTER("Fighter", 8, List.of(Ability.STR), Map.of(), 2000, null, false),
    MAGIC_USER("Magic-User", 4, List.of(Ability.INT), Map.of(), 2500, Spell.Tradition.ARCANE, false),
    THIEF("Thief", 4, List.of(Ability.DEX), Map.of(), 1200, null, false),
    DWARF("Dwarf", 8, List.of(Ability.STR), Map.of(Ability.CON, 9), 2200, null, false),
    ELF("Elf", 6, List.of(Ability.INT, Ability.STR), Map.of(Ability.INT, 9), 4000, Spell.Tradition.ARCANE, false),
    HALFLING("Halfling", 6, List.of(Ability.STR, Ability.DEX), Map.of(Ability.DEX, 9, Ability.CON, 9), 2000, null, false),

    // gygax75-rules custom classes, off by default -- see GameProps#enabledCustomClasses.
    BARBARIAN("Barbarian", 8, List.of(Ability.CON, Ability.STR), Map.of(Ability.DEX, 9), 2500, null, true),
    DRUID("Druid", 6, List.of(Ability.WIS), Map.of(), 2000, Spell.Tradition.NATURE, true),
    KNIGHT("Knight", 8, List.of(Ability.STR), Map.of(Ability.CON, 9, Ability.DEX, 9), 2500, null, true),
    WARDEN("Warden", 8, List.of(Ability.STR), Map.of(Ability.CON, 9, Ability.WIS, 9), 2000, null, true),
    GNOME("Gnome", 4, List.of(Ability.DEX, Ability.INT), Map.of(Ability.INT, 9), 2000, Spell.Tradition.ILLUSION, true),
    HALF_ORC("Half-Orc", 6, List.of(Ability.DEX, Ability.STR), Map.of(), 1800, null, true),
    WOOD_ELF("Wood Elf", 6, List.of(Ability.DEX, Ability.WIS), Map.of(Ability.DEX, 9, Ability.INT, 9), 3000, Spell.Tradition.NATURE, true);

    private final String displayName;
    private final int hitDie;
    private final List<Ability> primeRequisites;
    private final Map<Ability, Integer> minimumScores;
    private final int xpForLevel2;
    private final Spell.Tradition tradition;
    private final boolean custom;

    CharacterClass(
            String displayName,
            int hitDie,
            List<Ability> primeRequisites,
            Map<Ability, Integer> minimumScores,
            int xpForLevel2,
            Spell.Tradition tradition,
            boolean custom) {
        this.displayName = displayName;
        this.hitDie = hitDie;
        this.primeRequisites = primeRequisites;
        this.minimumScores = minimumScores;
        this.xpForLevel2 = xpForLevel2;
        this.tradition = tradition;
        this.custom = custom;
    }

    public String displayName() {
        return displayName;
    }

    public int hitDie() {
        return hitDie;
    }

    public List<Ability> primeRequisites() {
        return primeRequisites;
    }

    public Map<Ability, Integer> minimumScores() {
        return minimumScores;
    }

    public int xpForLevel2() {
        return xpForLevel2;
    }

    /** {@code null} for a non-caster; otherwise which of the four spell traditions this class draws
        from (see {@link Spell.Tradition}) -- the single source of truth {@link SpellTables} reads
        rather than re-deriving caster status independently. */
    public Spell.Tradition tradition() {
        return tradition;
    }

    public boolean isArcaneCaster() {
        return tradition == Spell.Tradition.ARCANE;
    }

    public boolean isDivineCaster() {
        return tradition == Spell.Tradition.DIVINE;
    }

    /** Whether this is one of the optional gygax75-rules classes (off by default, per-DM enabled via
        {@code GameProps#enabledCustomClasses}) rather than one of the 7 standard B/X classes. */
    public boolean isCustom() {
        return custom;
    }

    /** Whether the given scores meet this class's minimum ability requirements. */
    public boolean meetsRequirements(AbilityScores scores) {
        return minimumScores.entrySet().stream()
                .allMatch(e -> scores.score(e.getKey()) >= e.getValue());
    }

    /** Human-readable description of any unmet minimums, or empty if all are met. */
    public String unmetRequirements(AbilityScores scores) {
        return minimumScores.entrySet().stream()
                .filter(e -> scores.score(e.getKey()) < e.getValue())
                .map(e -> e.getKey().abbreviation() + " " + e.getValue() + "+ (rolled " + scores.score(e.getKey()) + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    /**
     * The B/X prime-requisite XP adjustment: 3-5 -20%, 6-8 -10%, 9-12 0, 13-15 +5%, 16-18 +10%. For the
     * two dual-prime-requisite classes (Elf, Halfling) the average of both requisites' scores (rounded
     * down) is used, per the B/X dual-requisite rule; single-PR classes trivially average to themselves.
     */
    public int xpBonusPercent(AbilityScores scores) {
        int sum = 0;
        for (Ability a : primeRequisites) {
            sum += scores.score(a);
        }
        int average = sum / primeRequisites.size();
        if (average <= 5) {
            return -20;
        } else if (average <= 8) {
            return -10;
        } else if (average <= 12) {
            return 0;
        } else if (average <= 15) {
            return 5;
        } else {
            return 10;
        }
    }

    /** Parses user input like "magic-user", "magicuser", "mu", "magic_user" to a class. */
    public static CharacterClass parse(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toLowerCase().replaceAll("[\\s_-]", "");
        return switch (normalized) {
            case "cleric" -> CLERIC;
            case "fighter" -> FIGHTER;
            case "magicuser", "mu", "wizard", "mage" -> MAGIC_USER;
            case "thief", "rogue" -> THIEF;
            case "dwarf" -> DWARF;
            case "elf" -> ELF;
            case "halfling", "hobbit" -> HALFLING;
            case "barbarian" -> BARBARIAN;
            case "druid" -> DRUID;
            case "knight" -> KNIGHT;
            case "warden" -> WARDEN;
            case "gnome" -> GNOME;
            case "halforc" -> HALF_ORC;
            case "woodelf" -> WOOD_ELF;
            default -> null;
        };
    }
}
