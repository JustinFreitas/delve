package dev.freitas.delve.game.engine;

import java.util.List;
import java.util.Map;

/**
 * The seven B/X character classes (races are classes in B/X). Carries the data needed for character
 * creation and the sheet: hit die, prime requisite(s), minimum ability requirements, and the XP
 * threshold for level 2. Fuller per-level XP tables arrive with advancement (Milestone 7).
 */
public enum CharacterClass {
    CLERIC("Cleric", 6, List.of(Ability.WIS), Map.of(), 1500, false, true),
    FIGHTER("Fighter", 8, List.of(Ability.STR), Map.of(), 2000, false, false),
    MAGIC_USER("Magic-User", 4, List.of(Ability.INT), Map.of(), 2500, true, false),
    THIEF("Thief", 4, List.of(Ability.DEX), Map.of(), 1200, false, false),
    DWARF("Dwarf", 8, List.of(Ability.STR), Map.of(Ability.CON, 9), 2200, false, false),
    ELF("Elf", 6, List.of(Ability.INT, Ability.STR), Map.of(Ability.INT, 9), 4000, true, false),
    HALFLING("Halfling", 6, List.of(Ability.STR, Ability.DEX), Map.of(Ability.DEX, 9, Ability.CON, 9), 2000, false, false);

    private final String displayName;
    private final int hitDie;
    private final List<Ability> primeRequisites;
    private final Map<Ability, Integer> minimumScores;
    private final int xpForLevel2;
    private final boolean arcaneCaster;
    private final boolean divineCaster;

    CharacterClass(
            String displayName,
            int hitDie,
            List<Ability> primeRequisites,
            Map<Ability, Integer> minimumScores,
            int xpForLevel2,
            boolean arcaneCaster,
            boolean divineCaster) {
        this.displayName = displayName;
        this.hitDie = hitDie;
        this.primeRequisites = primeRequisites;
        this.minimumScores = minimumScores;
        this.xpForLevel2 = xpForLevel2;
        this.arcaneCaster = arcaneCaster;
        this.divineCaster = divineCaster;
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

    public boolean isArcaneCaster() {
        return arcaneCaster;
    }

    public boolean isDivineCaster() {
        return divineCaster;
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
     * The B/X prime-requisite XP adjustment, based on the primary prime requisite score:
     * 3-5 -20%, 6-8 -10%, 9-12 0, 13-15 +5%, 16-18 +10%. For the two dual-prime-requisite classes
     * (Elf, Halfling) the primary (first-listed) requisite is used; the exact dual-requisite rules
     * are refined alongside full advancement (Milestone 7).
     */
    public int xpBonusPercent(AbilityScores scores) {
        int primary = scores.score(primeRequisites.get(0));
        if (primary <= 5) {
            return -20;
        } else if (primary <= 8) {
            return -10;
        } else if (primary <= 12) {
            return 0;
        } else if (primary <= 15) {
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
            default -> null;
        };
    }
}
