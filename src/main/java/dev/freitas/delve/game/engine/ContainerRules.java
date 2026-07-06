package dev.freitas.delve.game.engine;

import dev.freitas.delve.game.model.Container;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * gygax75-rules' container/stowage rules: which items never need a container at all, how much room is
 * left in a given one, and which of a character's containers to auto-assign a new item into first
 * (backpack, then a worn sack, then held sacks — matching how they'd naturally reach for the free-
 * handed option before something already occupying a hand).
 */
public final class ContainerRules {

    // Matched case-insensitively by substring, same convention as GearCatalog/WeaponCatalog. These are
    // gygax75-rules' named exceptions that stay "on the person" rather than needing a container.
    private static final List<String> EXEMPT_NEEDLES = List.of("waterskin", "holy symbol", "quiver", "bolt case");

    // Worn containers first (no hands to free up), backpack ahead of a worn sack (more capacity).
    private static final Comparator<Container> FILL_ORDER = Comparator
            .comparing((Container c) -> c.isHeld())
            .thenComparing(c -> c.getType() != ContainerType.BACKPACK);

    private ContainerRules() {}

    /** Whether this item name never needs to be stowed in a container (gygax75-rules' named
        exceptions) — stays in the exempt on-person {@code inventory} list instead. */
    public static boolean isExempt(String itemName) {
        if (itemName == null) {
            return false;
        }
        String lower = itemName.toLowerCase(Locale.ROOT);
        return EXEMPT_NEEDLES.stream().anyMatch(lower::contains);
    }

    /** How much more weight this container can hold before hitting its type's capacity. */
    public static int capacityRemaining(Container container) {
        int used = container.getItems().stream().mapToInt(GearCatalog::weightCns).sum();
        return Math.max(0, container.getType().capacityCns() - used);
    }

    /** The first of these containers (backpack, then a worn sack, then held sacks) with enough room for
        an item of this weight, or {@code null} if none fits. Does not mutate anything — callers add the
        item to the returned container themselves. */
    public static Container findRoomFor(List<Container> containers, int itemWeightCns) {
        return containers.stream()
                .sorted(FILL_ORDER)
                .filter(c -> capacityRemaining(c) >= itemWeightCns)
                .findFirst()
                .orElse(null);
    }

    /** How many of these containers are currently held (not worn) — the {@code heldSacks} input to
        {@link Hands}, since each held container costs a hand the same way a shield does. */
    public static int heldCount(List<Container> containers) {
        return (int) containers.stream().filter(Container::isHeld).count();
    }

    /** Whether a newly bought container of this type could be worn (no hand cost) rather than held —
        gygax75-rules caps this at one worn backpack and one worn small sack; a large sack is never worn
        ({@link ContainerType#canBeWorn()}) so this is always {@code false} for one. */
    public static boolean canWearAnother(List<Container> containers, ContainerType type) {
        if (!type.canBeWorn()) {
            return false;
        }
        return containers.stream().noneMatch(c -> c.getType() == type && !c.isHeld());
    }
}
