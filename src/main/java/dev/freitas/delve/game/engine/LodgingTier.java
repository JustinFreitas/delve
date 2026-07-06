package dev.freitas.delve.game.engine;

/**
 * A safe haven's Inn accommodations for a party's PCs during a {@code /town} stay (gygax75-rules:
 * Dormitory 1sp/night, Shared Room 5sp/night, Room 1gp/night). Rates here are that same 1:5:10 ratio
 * scaled ×10 into whole gp — delve has no sub-gp currency modeled anywhere else either, so this is a
 * deliberate simplification, same spirit as {@link GearCatalog}'s existing gygax75-price reconciliation.
 * The cheaper tiers carry a real cost beyond gold: see {@code TownService#rest}.
 */
public enum LodgingTier {
    /** Cheapest — "Retainers will quit service" per the rules: every owned retainer deserts,
        unconditionally, the moment a PC stays here for any real length of time. */
    DORMITORY("Dormitory", 1),
    /** "Standard for Retainers" — the PC staying here themselves (not their retainers) risks their
        retainers deciding they're an unfit boss: once per full week actually rested, each owned
        retainer rolls against their own loyalty and may desert. */
    SHARED_ROOM("Shared room", 5),
    /** "Standard for PCs" — the default tier, no consequence beyond the gold cost. */
    ROOM("Room", 10);

    private final String displayName;
    private final int gpPerDay;

    LodgingTier(String displayName, int gpPerDay) {
        this.displayName = displayName;
        this.gpPerDay = gpPerDay;
    }

    public String displayName() {
        return displayName;
    }

    public int gpPerDay() {
        return gpPerDay;
    }
}
