package dev.freitas.delve.game.session;

import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.Hands;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.CombatEncounter;
import dev.freitas.delve.game.model.Container;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import java.util.Iterator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Owns what happens to a combatant's held (not worn) sacks when a fight starts. A held container costs
 * a hand just like a shield or the party's light does, but it's the lowest-priority claim on that
 * budget: wielding a weapon, raising a shield, dual-wielding, and bearing the light are all gated at the
 * point they're taken (see {@code WieldCommand}/{@code LightingService}/{@code MuleService}) without any
 * regard for held sacks, so a held sack never blocks one of those. Instead, once combat starts, any held
 * container that no longer fits the hand budget those already claim is dropped where it stands.
 */
@Service
public class ContainerService {

    /** Called once per {@link CombatService#startCombat}: for every living PC/retainer, any held
        container beyond what their current weapon/shield/light/off-hand budget leaves free moves from
        their active {@code containers} list into {@code encounter.droppedContainers} (each keeps its own
        {@code owner} token, so no separate per-owner bookkeeping is needed). A worn backpack or worn
        small sack is never affected here — only held ones cost a hand. */
    public void reconcileHeldContainers(SaveGame save, ExplorationResult result) {
        CombatEncounter encounter = save.getSession().getCombat();
        String bearerToken = save.getSession().getLightBearer();
        for (Character pc : save.livingCharacters()) {
            boolean isBearer = bearerToken != null && bearerToken.equalsIgnoreCase(save.tokenFor(pc));
            int freeForSacks = Hands.free(pc.getMainWeapon(), pc.isShield(), isBearer, pc.getOffHandWeapon() != null);
            dropExcess(pc.getContainers(), freeForSacks, encounter, displayName(save, pc.getName()), result);
        }
        for (Retainer r : save.livingRetainers()) {
            boolean isBearer = bearerToken != null && bearerToken.equalsIgnoreCase(save.tokenFor(r));
            int freeForSacks = Hands.free(r.getMainWeapon(), r.isShield(), isBearer);
            dropExcess(r.getContainers(), freeForSacks, encounter, displayName(save, r.getName()), result);
        }
    }

    private void dropExcess(
            List<Container> active, int freeForSacks, CombatEncounter encounter, String ownerLabel,
            ExplorationResult result) {
        int held = (int) active.stream().filter(Container::isHeld).count();
        int toDrop = held - freeForSacks;
        if (toDrop <= 0) {
            return;
        }
        Iterator<Container> it = active.iterator();
        while (it.hasNext() && toDrop > 0) {
            Container c = it.next();
            if (c.isHeld()) {
                encounter.getDroppedContainers().add(c);
                it.remove();
                toDrop--;
                result.add(ownerLabel + " drops the " + c.getType().displayName().toLowerCase()
                        + " to free up a hand for the fight.");
            }
        }
    }

    /** On victory: every dropped container returns to its owner's active list — nothing lost, since you
        won and can pick your things back up. */
    public void returnDroppedContainers(SaveGame save, CombatEncounter encounter) {
        for (Container c : encounter.getDroppedContainers()) {
            List<Container> owner = containersFor(save, c.getOwner());
            if (owner != null) {
                owner.add(c);
            }
        }
        encounter.getDroppedContainers().clear();
    }

    /** On a successful flee: dropped containers (and everything in them) are gone for good — reports
        what was lost, by name, one line per non-empty container. */
    public void discardDroppedContainers(SaveGame save, CombatEncounter encounter, ExplorationResult result) {
        for (Container c : encounter.getDroppedContainers()) {
            if (!c.getItems().isEmpty()) {
                result.add(displayName(save, c.getOwner()) + " leaves the " + c.getType().displayName().toLowerCase()
                        + " (" + String.join(", ", c.getItems()) + ") behind in the retreat.");
            }
        }
        encounter.getDroppedContainers().clear();
    }

    private List<Container> containersFor(SaveGame save, String owner) {
        Combatant combatant = save.resolve(owner);
        if (combatant instanceof Character pc) {
            return pc.getContainers();
        }
        if (combatant instanceof Retainer r) {
            return r.getContainers();
        }
        return null;
    }

    private String displayName(SaveGame save, String owner) {
        Combatant combatant = save.resolve(owner);
        if (combatant instanceof Character) {
            return save.getCharacters().size() == 1 ? "You" : combatant.getName();
        }
        return combatant != null ? combatant.getName() : "Someone";
    }
}
