package dev.freitas.delve.game.model;

import dev.freitas.delve.game.engine.ContainerType;
import java.util.ArrayList;
import java.util.List;

/**
 * A backpack or sack a PC/retainer carries, holding a real list of item names (not just an aggregate
 * weight) so that losing one — a held sack dropped and left behind on a failed fight, see
 * {@code ContainerService} — removes exactly what was in it. A backpack is always worn ({@code held =
 * false}); a small sack may be worn (at most one) or held; a large sack is always held. Held containers
 * cost a hand (see {@link dev.freitas.delve.game.engine.Hands}) and can be dropped in combat.
 */
public class Container {

    private ContainerType type;
    private boolean held;
    private List<String> items = new ArrayList<>();

    // The owning PC/retainer's name, so a dropped container (moved to CombatEncounter.droppedContainers)
    // still knows who to return to on victory.
    private String owner;

    public Container() {}

    public Container(ContainerType type, boolean held, String owner) {
        this.type = type;
        this.held = held;
        this.owner = owner;
    }

    public ContainerType getType() {
        return type;
    }

    public void setType(ContainerType type) {
        this.type = type;
    }

    public boolean isHeld() {
        return held;
    }

    public void setHeld(boolean held) {
        this.held = held;
    }

    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = items;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
