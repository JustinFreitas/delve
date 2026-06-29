package dev.freitas.delve.game.engine;

/**
 * B/X Charisma effects on retainers: how many a character may employ and their base loyalty, plus a
 * descriptor for a loyalty score. Average loyalty is 7, shifted by the Charisma adjustment.
 */
public final class RetainerRules {

    private RetainerRules() {}

    public static int maxRetainers(int charisma) {
        if (charisma <= 3) return 1;
        if (charisma <= 5) return 2;
        if (charisma <= 8) return 3;
        if (charisma <= 12) return 4;
        if (charisma <= 15) return 5;
        if (charisma <= 17) return 6;
        return 7;
    }

    public static int loyaltyAdjustment(int charisma) {
        if (charisma <= 3) return -2;
        if (charisma <= 8) return -1;
        if (charisma <= 12) return 0;
        if (charisma <= 15) return 1;
        if (charisma <= 17) return 2;
        return 4;
    }

    public static int baseLoyalty(int charisma) {
        return 7 + loyaltyAdjustment(charisma);
    }

    public static String loyaltyDescriptor(int loyalty) {
        if (loyalty <= 3) return "disloyal";
        if (loyalty <= 6) return "grudging";
        if (loyalty <= 9) return "steady";
        if (loyalty <= 12) return "loyal";
        return "fanatic";
    }
}
