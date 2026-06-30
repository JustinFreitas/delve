package dev.freitas.delve.game.engine;

/**
 * A B/X spell. A starter catalog covering the most useful 1st- and 2nd-level arcane (magic-user/elf)
 * and divine (cleric) spells. Mechanical magnitudes live in the spell service; this enum carries the
 * tradition, spell level, and broad effect category.
 */
public enum Spell {
    // Arcane — Magic-User / Elf
    MAGIC_MISSILE("Magic Missile", Tradition.ARCANE, 1, SpellEffect.DAMAGE),
    SLEEP("Sleep", Tradition.ARCANE, 1, SpellEffect.SLEEP),
    SHIELD("Shield", Tradition.ARCANE, 1, SpellEffect.UTILITY),
    CHARM_PERSON("Charm Person", Tradition.ARCANE, 1, SpellEffect.UTILITY),
    LIGHT("Light", Tradition.ARCANE, 1, SpellEffect.UTILITY),
    DETECT_MAGIC("Detect Magic", Tradition.ARCANE, 1, SpellEffect.UTILITY),
    READ_MAGIC("Read Magic", Tradition.ARCANE, 1, SpellEffect.UTILITY),
    FLOATING_DISC("Floating Disc", Tradition.ARCANE, 1, SpellEffect.UTILITY),
    HOLD_PORTAL("Hold Portal", Tradition.ARCANE, 1, SpellEffect.UTILITY),
    PROTECTION_FROM_EVIL("Protection from Evil", Tradition.ARCANE, 1, SpellEffect.UTILITY),
    VENTRILOQUISM("Ventriloquism", Tradition.ARCANE, 1, SpellEffect.UTILITY),
    READ_LANGUAGES("Read Languages", Tradition.ARCANE, 1, SpellEffect.UTILITY),
    WEB("Web", Tradition.ARCANE, 2, SpellEffect.UTILITY),

    // Divine — Cleric
    CURE_LIGHT_WOUNDS("Cure Light Wounds", Tradition.DIVINE, 1, SpellEffect.HEAL),
    DIVINE_LIGHT("Light (Cleric)", Tradition.DIVINE, 1, SpellEffect.UTILITY),
    PROTECTION_FROM_EVIL_CLERIC("Protection from Evil (Cleric)", Tradition.DIVINE, 1, SpellEffect.UTILITY),
    RESIST_COLD("Resist Cold", Tradition.DIVINE, 1, SpellEffect.UTILITY),
    PURIFY_FOOD("Purify Food and Water", Tradition.DIVINE, 1, SpellEffect.UTILITY),
    REMOVE_FEAR("Remove Fear", Tradition.DIVINE, 1, SpellEffect.UTILITY);

    private final String displayName;
    private final Tradition tradition;
    private final int level;
    private final SpellEffect effect;

    Spell(String displayName, Tradition tradition, int level, SpellEffect effect) {
        this.displayName = displayName;
        this.tradition = tradition;
        this.level = level;
        this.effect = effect;
    }

    public String displayName() {
        return displayName;
    }

    public Tradition tradition() {
        return tradition;
    }

    public int level() {
        return level;
    }

    public SpellEffect effect() {
        return effect;
    }

    /** Whether this spell must be aimed at an enemy (and so requires being in combat). */
    public boolean targetsEnemy() {
        return effect == SpellEffect.DAMAGE || effect == SpellEffect.SLEEP;
    }

    public static Spell parse(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toLowerCase().replaceAll("[\\s_'-]", "");
        for (Spell spell : values()) {
            if (spell.displayName.toLowerCase().replaceAll("[\\s_'()-]", "").equals(normalized)) {
                return spell;
            }
        }
        return null;
    }

    public enum Tradition {
        ARCANE,
        DIVINE
    }

    public enum SpellEffect {
        DAMAGE,
        HEAL,
        SLEEP,
        UTILITY
    }
}
