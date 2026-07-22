package dev.freitas.delve.game.engine;

/**
 * A monster's optional ranged attack: the damage a hit deals and the Short/Medium/Long range table it
 * fires on. Resolved with the exact same B/X missile to-hit math a PC's missile weapon uses (the
 * {@link MissileRangeTable} band's {@link RangeBand#toHitModifier()} folded into the attack roll) —
 * B/X places no monster-specific restriction on missile fire, so a monster armed with one shoots under
 * the same rules the party already follows.
 */
public record RangedAttack(DamageRoll damage, MissileRangeTable range) {}
