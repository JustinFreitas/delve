package dev.freitas.delve.game.model;

import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Combatant;
import dev.freitas.delve.game.engine.DamageRoll;
import dev.freitas.delve.game.engine.MuleRules;

/**
 * A party pack animal that hauls gold so the party's own encumbrance isn't dragged down by dungeon
 * loot. Has a real OSE stat block (AC 7 [12], HD 2, THAC0 18, kick 1d4 — see {@link MuleRules}), so it
 * takes a slot in {@link dev.freitas.delve.game.engine.Formation}/marching order alongside PCs and
 * retainers and can be targeted and killed by monsters. It never attacks on its own initiative though
 * (OSE: "cannot be trained to attack on command") — nothing in {@code CombatService}'s auto-attack loop
 * ever calls its action, only monsters attacking the party can affect it.
 */
public class Mule implements Combatant {

    private String name;

    // The PC who paid for and is responsible for this mule (upkeep), same pattern as Retainer.owner.
    private String owner;

    // Who's currently leading it (a PC or retainer name token) — needs a free hand, same concept as the
    // party's shared light bearer, but persisted on the mule itself since (unlike light) there's no
    // single shared GameSession slot for it. Null if nobody currently has a free hand to lead it.
    private String handler;

    // Gold currently loaded onto the mule. 1 coin = 1 cn (gygax75-rules), so this doubles directly as
    // the mule's carried weight in cns — no separate item-weight system needed.
    private int carriedGold;

    private int maxHp;
    private int currentHp;

    public Mule() {}

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public int getCarriedGold() {
        return carriedGold;
    }

    public void setCarriedGold(int carriedGold) {
        this.carriedGold = carriedGold;
    }

    @Override
    public int getMaxHp() {
        return maxHp;
    }

    public void setMaxHp(int maxHp) {
        this.maxHp = maxHp;
    }

    @Override
    public int getCurrentHp() {
        return currentHp;
    }

    @Override
    public void setCurrentHp(int currentHp) {
        this.currentHp = currentHp;
    }

    @Override
    public int armorClass() {
        return MuleRules.ARMOR_CLASS;
    }

    @Override
    public int thac0() {
        return MuleRules.THAC0;
    }

    @Override
    public int meleeToHitModifier() {
        return 0;
    }

    @Override
    public int missileToHitModifier() {
        return 0;
    }

    @Override
    public int meleeDamageModifier() {
        return 0;
    }

    @Override
    public DamageRoll getMainWeaponDamage() {
        return MuleRules.KICK_DAMAGE;
    }

    @Override
    public String getMainWeapon() {
        return "Kick";
    }

    @Override
    public boolean isAlive() {
        return currentHp > 0;
    }

    /** No class — an animal, not deliberately excluded from {@code Advanceable} either (it doesn't gain
        levels/XP). Safe: every generic-{@code Combatant} call site that reads this only compares it
        with {@code ==}/{@code !=} against a specific {@link CharacterClass}, never dereferences it. */
    @Override
    public CharacterClass getCharacterClass() {
        return null;
    }
}
