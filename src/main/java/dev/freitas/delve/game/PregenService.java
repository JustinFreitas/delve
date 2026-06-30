package dev.freitas.delve.game;

import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.Advancement;
import dev.freitas.delve.game.engine.Armor;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.Leveling;
import dev.freitas.delve.game.engine.MagicItemTable;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.session.SpellService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds a finished, table-ready B/X character at a target level — the heart of the
 * character-seeding feature. A pregen rolls qualifying abilities, is advanced to its level (rolling
 * hit points per level via {@link Leveling#advanceTo}), receives level-appropriate armor, wealth and
 * a few magic items, and (for casters) arrives with spells prepared.
 *
 * <p>Output is pure B/X, so the result drops straight into an OSE-compatible game such as Desert of
 * Desolation.
 */
@Component
public class PregenService {

    private static final int MAX_ABILITY_ROLLS = 50; // re-roll until the class's minimums are met

    private final Dice dice;
    private final CharacterFactory characterFactory;
    private final SpellService spellService;

    public PregenService(Dice dice, CharacterFactory characterFactory, SpellService spellService) {
        this.dice = dice;
        this.characterFactory = characterFactory;
        this.spellService = spellService;
    }

    /** Highest level this class can reach (so commands can validate input). */
    public int maxLevel(CharacterClass characterClass) {
        return Advancement.maxLevel(characterClass);
    }

    /** Builds a complete character of the given class at the given level. */
    public Character create(String name, CharacterClass characterClass, int targetLevel) {
        int level = Math.max(1, Math.min(targetLevel, maxLevel(characterClass)));

        AbilityScores abilities = rollQualifyingAbilities(characterClass);
        Character c = characterFactory.create(name, characterClass, abilities);
        Leveling.advanceTo(c, level, dice);

        scaleGearAndWealth(c, level);
        applyMagicItems(c, level);
        spellService.autoPrepare(c); // no-op for non-casters

        return c;
    }

    /** Rolls 3d6 ability sets until one meets the class minimums (fighters etc. always pass). */
    private AbilityScores rollQualifyingAbilities(CharacterClass characterClass) {
        AbilityScores best = AbilityScores.roll(dice);
        for (int i = 0; i < MAX_ABILITY_ROLLS && !characterClass.meetsRequirements(best); i++) {
            best = AbilityScores.roll(dice);
        }
        return best;
    }

    /** Mid-level martial characters upgrade to plate; everyone gets level-scaled adventuring wealth. */
    private void scaleGearAndWealth(Character c, int level) {
        CharacterClass cls = c.getCharacterClass();
        boolean martial = cls == CharacterClass.FIGHTER || cls == CharacterClass.DWARF;
        if (martial && level >= 3 && c.getArmor() == Armor.CHAIN_MAIL) {
            c.setArmor(Armor.PLATE_MAIL);
            if (!c.getInventory().contains("Plate mail")) {
                c.getInventory().add("Plate mail");
            }
        }
        // A seasoned delver has accumulated coin: a base purse that grows with level.
        int wealth = 200 + dice.roll(2, 6) * 100 * level;
        c.setGold(c.getGold() + wealth);
        // Lay in extra torches for a deeper expedition.
        c.setTorches(c.getTorches() + level);
    }

    private void applyMagicItems(Character c, int level) {
        MagicItemTable.Loot loot = MagicItemTable.roll(c.getCharacterClass(), level, dice);
        c.getInventory().addAll(loot.items());
        c.setHealingPotions(c.getHealingPotions() + loot.healingPotions());
        // A "+1 weapon" signature item improves the character's attack damage modestly.
        for (String item : loot.items()) {
            if (item.startsWith("+1 ") && item.toLowerCase().contains(c.getMainWeapon().toLowerCase())) {
                c.setMainWeapon("+1 " + c.getMainWeapon());
                break;
            }
        }
    }

    /** Convenience for batch tools: a list of pregens of the given (or random) class at a level. */
    public List<Character> createBatch(int count, int level, CharacterClass fixedClass, List<String> names) {
        List<Character> party = new java.util.ArrayList<>();
        CharacterClass[] all = CharacterClass.values();
        for (int i = 0; i < count; i++) {
            CharacterClass cls = fixedClass != null ? fixedClass : all[dice.d(all.length) - 1];
            String name = (names != null && i < names.size()) ? names.get(i) : cls.displayName() + " " + (i + 1);
            party.add(create(name, cls, level));
        }
        return party;
    }
}
