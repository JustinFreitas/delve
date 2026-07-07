package dev.freitas.delve.game.engine;

/**
 * A B/X spell. A starter catalog covering the most useful 1st- and 2nd-level arcane (magic-user/elf)
 * and divine (cleric) spells, plus full Nature (druid/wood elf) and Illusion (gnome) spell lists from
 * gygax75-rules. Mechanical magnitudes live in the spell service/{@code CombatService}; this enum
 * carries the tradition, spell level, and broad effect category. Every {@code UTILITY} spell is a
 * flavor-cast (consumes a slot, no mechanical simulation) — the same shallow model the original 6
 * Arcane/Divine utility spells already use; {@code DAMAGE}/{@code SLEEP}/{@code HEAL} all resolve
 * through the one existing generic formula for that effect (see {@code CombatService
 * #applyPlayerSpell}), not a bespoke effect per spell.
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
    REMOVE_FEAR("Remove Fear", Tradition.DIVINE, 1, SpellEffect.UTILITY),

    // Nature — Druid / Wood Elf (gygax75-rules "Druid Spells")
    ANIMAL_FRIENDSHIP("Animal Friendship", Tradition.NATURE, 1, SpellEffect.UTILITY),
    DETECT_DANGER("Detect Danger", Tradition.NATURE, 1, SpellEffect.UTILITY),
    ENTANGLE("Entangle", Tradition.NATURE, 1, SpellEffect.UTILITY),
    FAERIE_FIRE("Faerie Fire", Tradition.NATURE, 1, SpellEffect.UTILITY),
    INVISIBILITY_TO_ANIMALS("Invisibility to Animals", Tradition.NATURE, 1, SpellEffect.UTILITY),
    LOCATE_PLANT_OR_ANIMAL("Locate Plant or Animal", Tradition.NATURE, 1, SpellEffect.UTILITY),
    PREDICT_WEATHER("Predict Weather", Tradition.NATURE, 1, SpellEffect.UTILITY),
    SPEAK_WITH_ANIMALS("Speak with Animals", Tradition.NATURE, 1, SpellEffect.UTILITY),
    BARKSKIN("Barkskin", Tradition.NATURE, 2, SpellEffect.UTILITY),
    CREATE_WATER("Create Water", Tradition.NATURE, 2, SpellEffect.UTILITY),
    DRUID_CURE_LIGHT_WOUNDS("Cure Light Wounds (Druid)", Tradition.NATURE, 2, SpellEffect.HEAL),
    HEAT_METAL("Heat Metal", Tradition.NATURE, 2, SpellEffect.UTILITY),
    OBSCURING_MIST("Obscuring Mist", Tradition.NATURE, 2, SpellEffect.UTILITY),
    PRODUCE_FLAME("Produce Flame", Tradition.NATURE, 2, SpellEffect.UTILITY),
    SLOW_POISON("Slow Poison", Tradition.NATURE, 2, SpellEffect.UTILITY),
    WARP_WOOD("Warp Wood", Tradition.NATURE, 2, SpellEffect.UTILITY),
    CALL_LIGHTNING("Call Lightning", Tradition.NATURE, 3, SpellEffect.DAMAGE),
    GROWTH_OF_NATURE("Growth of Nature", Tradition.NATURE, 3, SpellEffect.UTILITY),
    HOLD_ANIMAL("Hold Animal", Tradition.NATURE, 3, SpellEffect.SLEEP),
    PROTECTION_FROM_POISON("Protection from Poison", Tradition.NATURE, 3, SpellEffect.UTILITY),
    TREE_SHAPE("Tree Shape", Tradition.NATURE, 3, SpellEffect.UTILITY),
    WATER_BREATHING("Water Breathing", Tradition.NATURE, 3, SpellEffect.UTILITY),
    CURE_SERIOUS_WOUNDS("Cure Serious Wounds", Tradition.NATURE, 4, SpellEffect.HEAL),
    NATURE_DISPEL_MAGIC("Dispel Magic", Tradition.NATURE, 4, SpellEffect.UTILITY),
    PROTECTION_FROM_FIRE_AND_LIGHTNING("Protection from Fire and Lightning", Tradition.NATURE, 4, SpellEffect.UTILITY),
    SPEAK_WITH_PLANTS("Speak with Plants", Tradition.NATURE, 4, SpellEffect.UTILITY),
    SUMMON_ANIMALS("Summon Animals", Tradition.NATURE, 4, SpellEffect.UTILITY),
    TEMPERATURE_CONTROL("Temperature Control", Tradition.NATURE, 4, SpellEffect.UTILITY),
    COMMUNE_WITH_NATURE("Commune with Nature", Tradition.NATURE, 5, SpellEffect.UTILITY),
    CONTROL_WEATHER("Control Weather", Tradition.NATURE, 5, SpellEffect.UTILITY),
    PASS_PLANT("Pass Plant", Tradition.NATURE, 5, SpellEffect.UTILITY),
    PROTECTION_FROM_PLANTS_AND_ANIMALS("Protection from Plants and Animals", Tradition.NATURE, 5, SpellEffect.UTILITY),
    TRANSMUTE_ROCK_TO_MUD("Transmute Rock to Mud", Tradition.NATURE, 5, SpellEffect.UTILITY),
    WALL_OF_THORNS("Wall of Thorns", Tradition.NATURE, 5, SpellEffect.UTILITY),

    // Illusion — Gnome (gygax75-rules "Gnome Spells"; Read Magic/Detect Magic/Light are omitted here,
    // already covered by the identically-named Arcane spells above)
    AUDITORY_ILLUSION("Auditory Illusion", Tradition.ILLUSION, 1, SpellEffect.UTILITY),
    CHROMATIC_ORB("Chromatic Orb", Tradition.ILLUSION, 1, SpellEffect.DAMAGE),
    COLOR_SPRAY("Color Spray", Tradition.ILLUSION, 1, SpellEffect.SLEEP),
    DANCING_LIGHTS("Dancing Lights", Tradition.ILLUSION, 1, SpellEffect.UTILITY),
    DETECT_ILLUSION("Detect Illusion", Tradition.ILLUSION, 1, SpellEffect.UTILITY),
    GLAMOUR("Glamour", Tradition.ILLUSION, 1, SpellEffect.UTILITY),
    HYPNOTISM("Hypnotism", Tradition.ILLUSION, 1, SpellEffect.SLEEP),
    PHANTASMAL_FORCE("Phantasmal Force", Tradition.ILLUSION, 1, SpellEffect.UTILITY),
    SPOOK("Spook", Tradition.ILLUSION, 1, SpellEffect.UTILITY),
    WALL_OF_FOG("Wall of Fog", Tradition.ILLUSION, 1, SpellEffect.UTILITY),
    BLINDNESS_DEAFNESS("Blindness/Deafness", Tradition.ILLUSION, 2, SpellEffect.SLEEP),
    BLUR("Blur", Tradition.ILLUSION, 2, SpellEffect.UTILITY),
    FALSE_AURA("False Aura", Tradition.ILLUSION, 2, SpellEffect.UTILITY),
    FASCINATE("Fascinate", Tradition.ILLUSION, 2, SpellEffect.SLEEP),
    HYPNOTIC_PATTERN("Hypnotic Pattern", Tradition.ILLUSION, 2, SpellEffect.SLEEP),
    IMPROVED_PHANTASMAL_FORCE("Improved Phantasmal Force", Tradition.ILLUSION, 2, SpellEffect.UTILITY),
    GNOME_INVISIBILITY("Invisibility", Tradition.ILLUSION, 2, SpellEffect.UTILITY),
    MAGIC_MOUTH("Magic Mouth", Tradition.ILLUSION, 2, SpellEffect.UTILITY),
    MIRROR_IMAGE("Mirror Image", Tradition.ILLUSION, 2, SpellEffect.UTILITY),
    QUASIMORPH("Quasimorph", Tradition.ILLUSION, 2, SpellEffect.UTILITY),
    WHISPERING_WIND("Whispering Wind", Tradition.ILLUSION, 2, SpellEffect.UTILITY),
    BLACKLIGHT("Blacklight", Tradition.ILLUSION, 3, SpellEffect.UTILITY),
    DISPEL_ILLUSION("Dispel Illusion", Tradition.ILLUSION, 3, SpellEffect.UTILITY),
    FEAR("Fear", Tradition.ILLUSION, 3, SpellEffect.SLEEP),
    HALLUCINATORY_TERRAIN("Hallucinatory Terrain", Tradition.ILLUSION, 3, SpellEffect.UTILITY),
    INVISIBILITY_10_RADIUS("Invisibility 10' Radius", Tradition.ILLUSION, 3, SpellEffect.UTILITY),
    NONDETECTION("Nondetection", Tradition.ILLUSION, 3, SpellEffect.UTILITY),
    PARALYZATION("Paralyzation", Tradition.ILLUSION, 3, SpellEffect.SLEEP),
    PHANTOM_STEED("Phantom Steed", Tradition.ILLUSION, 3, SpellEffect.UTILITY),
    ROPE_TRICK("Rope Trick", Tradition.ILLUSION, 3, SpellEffect.UTILITY),
    SPECTRAL_FORCE("Spectral Force", Tradition.ILLUSION, 3, SpellEffect.UTILITY),
    SUGGESTION("Suggestion", Tradition.ILLUSION, 3, SpellEffect.SLEEP),
    WRAITHFORM("Wraithform", Tradition.ILLUSION, 3, SpellEffect.UTILITY),
    CONFUSION("Confusion", Tradition.ILLUSION, 4, SpellEffect.SLEEP),
    ILLUSION_DISPEL_MAGIC("Dispel Magic (Gnome)", Tradition.ILLUSION, 4, SpellEffect.UTILITY),
    EMOTION("Emotion", Tradition.ILLUSION, 4, SpellEffect.UTILITY),
    ILLUSORY_STAMINA("Illusory Stamina", Tradition.ILLUSION, 4, SpellEffect.UTILITY),
    IMPROVED_INVISIBILITY("Improved Invisibility", Tradition.ILLUSION, 4, SpellEffect.UTILITY),
    MASSMORPH("Massmorph", Tradition.ILLUSION, 4, SpellEffect.UTILITY),
    MINOR_CREATION("Minor Creation", Tradition.ILLUSION, 4, SpellEffect.UTILITY),
    PHANTASMAL_KILLER("Phantasmal Killer", Tradition.ILLUSION, 4, SpellEffect.DAMAGE),
    RAINBOW_PATTERN("Rainbow Pattern", Tradition.ILLUSION, 4, SpellEffect.SLEEP),
    SHADOW_MONSTERS("Shadow Monsters", Tradition.ILLUSION, 4, SpellEffect.UTILITY),
    SOLID_FOG("Solid Fog", Tradition.ILLUSION, 4, SpellEffect.UTILITY),
    VEIL_OF_ABANDONMENT("Veil of Abandonment", Tradition.ILLUSION, 4, SpellEffect.UTILITY);

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
        String normalized = input.trim().toLowerCase().replaceAll("[\\s_'/-]", "");
        for (Spell spell : values()) {
            if (spell.displayName.toLowerCase().replaceAll("[\\s_'()/-]", "").equals(normalized)) {
                return spell;
            }
        }
        return null;
    }

    public enum Tradition {
        ARCANE,
        DIVINE,
        NATURE,
        ILLUSION
    }

    public enum SpellEffect {
        DAMAGE,
        HEAL,
        SLEEP,
        UTILITY
    }
}
