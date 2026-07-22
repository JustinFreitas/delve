package dev.freitas.delve.game;

import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.MissileRangeTable;
import dev.freitas.delve.game.engine.RangedAttack;
import dev.freitas.delve.game.model.AttackEffect;
import dev.freitas.delve.game.model.MonsterType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The full monster table: a small set of classic low-level B/X monsters. Both the procedural
 * generator's stocking and authored modules' monster references ({@code ModuleLoader}) resolve
 * against this list by name — there is deliberately no external {@code monsters.json}; a new monster
 * is a new constant here.
 */
public final class Bestiary {

    // moveRate (feet/turn) is a proposed default per monster, consistent in spirit with this file's
    // existing approximated AC/HD/morale values — adjustable later.

    // Missile ranges for the armed humanoids below (feet; Short/Medium/Long), matching WeaponCatalog's
    // own short-bow and sling tables so a goblin's bow and a PC's bow reach exactly the same. A
    // conservative starter set — the classic B/X humanoid archers carry a weapon and freely fire it
    // during the approach; add or remove `.withRanged(...)` below to taste (a pure content/tuning call).
    private static final RangedAttack SHORT_BOW = new RangedAttack(new DamageRoll(1, 6), new MissileRangeTable(50, 100, 150));
    private static final RangedAttack SLING = new RangedAttack(new DamageRoll(1, 4), new MissileRangeTable(40, 80, 160));

    public static final MonsterType GOBLIN =
            new MonsterType("Goblin", 1, -1, 6, new DamageRoll(1, 6), 7, 5, "2d4", 60, AttackEffect.NORMAL)
                    .withRanged(SHORT_BOW);
    public static final MonsterType KOBOLD =
            new MonsterType("Kobold", 1, -1, 7, new DamageRoll(1, 4), 6, 5, "4d4", 60, AttackEffect.NORMAL)
                    .withRanged(SLING);
    public static final MonsterType SKELETON =
            new MonsterType("Skeleton", 1, 0, 7, new DamageRoll(1, 6), 12, 10, "3d4", 60, AttackEffect.NORMAL, true);
    public static final MonsterType ORC =
            new MonsterType("Orc", 1, 0, 6, new DamageRoll(1, 6), 8, 10, "2d4", 60, AttackEffect.NORMAL)
                    .withRanged(SHORT_BOW);
    public static final MonsterType STIRGE =
            new MonsterType("Stirge", 1, 0, 7, new DamageRoll(1, 3), 9, 13, "1d10", 30, AttackEffect.NORMAL);
    public static final MonsterType HOBGOBLIN =
            new MonsterType("Hobgoblin", 1, 1, 6, new DamageRoll(1, 8), 8, 15, "1d6", 90, AttackEffect.NORMAL)
                    .withRanged(SHORT_BOW);
    public static final MonsterType ZOMBIE =
            new MonsterType("Zombie", 2, 0, 8, new DamageRoll(1, 8), 12, 20, "2d4", 60, AttackEffect.NORMAL, true);
    public static final MonsterType GIANT_RAT =
            new MonsterType("Giant Rat", 1, 0, 7, new DamageRoll(1, 3), 8, 5, "3d6", 120, AttackEffect.NORMAL);
    public static final MonsterType BUGBEAR =
            new MonsterType("Bugbear", 3, 1, 5, new DamageRoll(2, 4), 9, 75, "2d4", 90, AttackEffect.NORMAL);
    /** Classic B/X energy drainer: a hit permanently drains a level instead of dealing normal damage. */
    public static final MonsterType WIGHT =
            new MonsterType("Wight", 3, 0, 5, new DamageRoll(1, 4), 12, 175, "1d4", 90, AttackEffect.DRAIN, true);

    private static final Map<String, MonsterType> BY_NAME = new LinkedHashMap<>();

    static {
        for (MonsterType type : List.of(
                GOBLIN, KOBOLD, SKELETON, ORC, STIRGE, HOBGOBLIN, ZOMBIE, GIANT_RAT, BUGBEAR, WIGHT)) {
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
