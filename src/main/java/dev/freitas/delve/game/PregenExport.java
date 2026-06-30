package dev.freitas.delve.game;

import dev.freitas.delve.game.engine.Ability;
import dev.freitas.delve.game.engine.Encumbrance;
import dev.freitas.delve.game.engine.SavingThrows;
import dev.freitas.delve.game.engine.Spell;
import dev.freitas.delve.game.model.Character;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A clean, stable JSON export schema for a finished character — independent of the internal
 * {@link Character} persistence shape, so DMs and VTTs get a tidy B/X stat block. Built as ordered
 * maps so the serialized JSON reads top-to-bottom like a character sheet.
 */
public final class PregenExport {

    private PregenExport() {}

    public static Map<String, Object> of(Character c) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("name", c.getName());
        root.put("class", c.getCharacterClass().displayName());
        root.put("level", c.getLevel());
        root.put("xp", c.getXp());
        root.put("hitPoints", c.getMaxHp());
        root.put("armorClass", c.armorClass());
        root.put("armorClassAscending", c.ascendingArmorClass());
        root.put("thac0", c.thac0());

        Map<String, Integer> abilities = new LinkedHashMap<>();
        for (Ability ability : Ability.values()) {
            abilities.put(ability.abbreviation(), c.getAbilities().score(ability));
        }
        root.put("abilities", abilities);

        SavingThrows.Saves s = SavingThrows.forCharacter(c.getCharacterClass(), c.getLevel());
        Map<String, Integer> saves = new LinkedHashMap<>();
        saves.put("deathPoison", s.deathPoison());
        saves.put("wands", s.wands());
        saves.put("paralysisPetrify", s.paralysisPetrify());
        saves.put("breath", s.breath());
        saves.put("spells", s.spells());
        root.put("savingThrows", saves);

        root.put("weapon", c.getMainWeapon());
        root.put("weaponDamage", c.getMainWeaponDamage().toString());
        root.put("armor", c.getArmor().displayName());
        root.put("shield", c.isShield());
        boolean heavy = Encumbrance.heavyLoad(c.getGold());
        root.put("movementRate", Encumbrance.movementRate(c.getArmor(), heavy));
        root.put("gold", c.getGold());
        root.put("torches", c.getTorches());
        root.put("healingPotions", c.getHealingPotions());
        root.put("equipment", c.getInventory());

        List<String> prepared = c.getMemorizedSpells().stream()
                .map(Spell::valueOf)
                .map(Spell::displayName)
                .toList();
        if (!prepared.isEmpty()) {
            root.put("preparedSpells", prepared);
        }
        if (!c.getSpellbook().isEmpty()) {
            root.put("spellbook", c.getSpellbook());
        }
        return root;
    }
}
