package dev.freitas.delve.game.session;

import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Spell;
import dev.freitas.delve.game.engine.SpellTables;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.SaveGame;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Spell preparation and casting. Magic-users/elves prepare from their spellbook; clerics from the full
 * divine list. Spells are memorized into the B/X per-level slots, consumed on casting, and refreshed
 * by resting in town. Damaging/sleep spells require combat (handled by {@link CombatService}); healing
 * and utility spells are cast here.
 */
@Service
public class SpellService {

    static final DamageRoll MAGIC_MISSILE_DAMAGE = new DamageRoll(1, 6, 1);
    static final DamageRoll CURE_LIGHT_WOUNDS = new DamageRoll(1, 6, 1);

    private final Dice dice;

    public SpellService(Dice dice) {
        this.dice = dice;
    }

    /** Spells this character could prepare (spellbook for arcane casters; the full list for clerics). */
    public List<Spell> available(Character c) {
        if (!SpellTables.isCaster(c.getCharacterClass())) {
            return List.of();
        }
        Spell.Tradition tradition = SpellTables.tradition(c.getCharacterClass());
        if (tradition == Spell.Tradition.DIVINE) {
            List<Spell> divine = new ArrayList<>();
            for (Spell s : Spell.values()) {
                if (s.tradition() == Spell.Tradition.DIVINE) {
                    divine.add(s);
                }
            }
            return divine;
        }
        // Arcane: only what is written in the spellbook.
        List<Spell> known = new ArrayList<>();
        for (String name : c.getSpellbook()) {
            Spell s = Spell.parse(name);
            if (s != null && s.tradition() == Spell.Tradition.ARCANE && !known.contains(s)) {
                known.add(s);
            }
        }
        return known;
    }

    /** Fills every open slot with sensible defaults; used by town rest. Returns the prepared spells. */
    public List<String> autoPrepare(Character c) {
        c.getMemorizedSpells().clear();
        if (!SpellTables.isCaster(c.getCharacterClass())) {
            return List.of();
        }
        List<Spell> avail = available(c);
        int[] slots = SpellTables.slots(c.getCharacterClass(), c.getLevel());
        List<String> prepared = new ArrayList<>();
        for (int spellLevel = 1; spellLevel <= slots.length; spellLevel++) {
            List<Spell> choices = preferredFor(spellLevel, avail);
            for (int i = 0; i < slots[spellLevel - 1] && !choices.isEmpty(); i++) {
                Spell pick = choices.get(i % choices.size());
                c.getMemorizedSpells().add(pick.name());
                prepared.add(pick.displayName());
            }
        }
        return prepared;
    }

    /** Manually memorizes a spell if a slot of its level is free. */
    public ExplorationResult prepare(Character c, Spell spell) {
        if (!SpellTables.isCaster(c.getCharacterClass())) {
            return ExplorationResult.failure("Your class cannot cast spells.");
        }
        if (!available(c).contains(spell)) {
            return ExplorationResult.failure("You don't have **" + spell.displayName() + "** available to prepare.");
        }
        int capacity = SpellTables.slotsAt(c.getCharacterClass(), c.getLevel(), spell.level());
        long usedAtLevel = c.getMemorizedSpells().stream()
                .map(Spell::valueOf)
                .filter(s -> s.level() == spell.level())
                .count();
        if (usedAtLevel >= capacity) {
            return ExplorationResult.failure("No free level-" + spell.level() + " slots (you have " + capacity + ").");
        }
        c.getMemorizedSpells().add(spell.name());
        return ExplorationResult.of("You memorize **" + spell.displayName() + "**.");
    }

    public boolean isMemorized(Character c, Spell spell) {
        return c.getMemorizedSpells().contains(spell.name());
    }

    public boolean consume(Character c, Spell spell) {
        return c.getMemorizedSpells().remove(spell.name());
    }

    /** Casts a non-combat spell (healing or utility) as {@code c}. Enemy-targeting spells are rejected
        here. Takes the caster directly (not a {@link SaveGame}) so callers can resolve a specific PC in
        a multi-PC party — see {@code CastCommand}'s optional leading PC-name argument. */
    public ExplorationResult castOutOfCombat(Character c, Spell spell) {
        if (!isMemorized(c, spell)) {
            return ExplorationResult.failure("You don't have **" + spell.displayName() + "** prepared.");
        }
        if (spell.targetsEnemy()) {
            return ExplorationResult.failure("**" + spell.displayName() + "** can only be cast in combat.");
        }
        consume(c, spell);
        return switch (spell.effect()) {
            case HEAL -> {
                int healed = CURE_LIGHT_WOUNDS.roll(dice);
                int before = c.getCurrentHp();
                c.setCurrentHp(Math.min(c.getMaxHp(), c.getCurrentHp() + healed));
                yield ExplorationResult.of("You cast **" + spell.displayName() + "**, recovering "
                        + (c.getCurrentHp() - before) + " hp (" + c.getCurrentHp() + "/" + c.getMaxHp() + ").");
            }
            default -> ExplorationResult.of("You cast **" + spell.displayName() + "**.");
        };
    }

    private List<Spell> preferredFor(int spellLevel, List<Spell> available) {
        // Combat-useful first so auto-prepared casters are ready to fight.
        List<Spell> ordered = new ArrayList<>();
        for (Spell s : List.of(Spell.MAGIC_MISSILE, Spell.SLEEP, Spell.CURE_LIGHT_WOUNDS)) {
            if (s.level() == spellLevel && available.contains(s)) {
                ordered.add(s);
            }
        }
        for (Spell s : available) {
            if (s.level() == spellLevel && !ordered.contains(s)) {
                ordered.add(s);
            }
        }
        return ordered;
    }
}
