package dev.freitas.delve.game;

import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.model.MonsterType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small starter set of classic low-level B/X monsters. This is the in-code seed for the dungeon's
 * monster table; it is replaced/augmented by a {@code monsters.json} content file once dungeon
 * stocking lands (Milestone 4).
 */
public final class Bestiary {

    public static final MonsterType GOBLIN =
            new MonsterType("Goblin", 1, -1, 6, new DamageRoll(1, 6), 7, 5, "2d4");
    public static final MonsterType KOBOLD =
            new MonsterType("Kobold", 1, -1, 7, new DamageRoll(1, 4), 6, 5, "4d4");
    public static final MonsterType SKELETON =
            new MonsterType("Skeleton", 1, 0, 7, new DamageRoll(1, 6), 12, 10, "3d4");
    public static final MonsterType ORC =
            new MonsterType("Orc", 1, 0, 6, new DamageRoll(1, 6), 8, 10, "2d4");
    public static final MonsterType STIRGE =
            new MonsterType("Stirge", 1, 0, 7, new DamageRoll(1, 3), 9, 13, "1d10");
    public static final MonsterType HOBGOBLIN =
            new MonsterType("Hobgoblin", 1, 1, 6, new DamageRoll(1, 8), 8, 15, "1d6");
    public static final MonsterType ZOMBIE =
            new MonsterType("Zombie", 2, 0, 8, new DamageRoll(1, 8), 12, 20, "2d4");
    public static final MonsterType GIANT_RAT =
            new MonsterType("Giant Rat", 1, 0, 7, new DamageRoll(1, 3), 8, 5, "3d6");
    public static final MonsterType BUGBEAR =
            new MonsterType("Bugbear", 3, 1, 5, new DamageRoll(2, 4), 9, 75, "2d4");

    private static final Map<String, MonsterType> BY_NAME = new LinkedHashMap<>();

    static {
        for (MonsterType type : List.of(
                GOBLIN, KOBOLD, SKELETON, ORC, STIRGE, HOBGOBLIN, ZOMBIE, GIANT_RAT, BUGBEAR)) {
            BY_NAME.put(type.name().toLowerCase(), type);
        }
    }

    private Bestiary() {}

    public static MonsterType byName(String name) {
        return name == null ? null : BY_NAME.get(name.trim().toLowerCase());
    }

    public static List<MonsterType> all() {
        return List.copyOf(BY_NAME.values());
    }
}
