package dev.freitas.delve.game;

import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.MuleRules;
import dev.freitas.delve.game.model.Mule;
import org.springframework.stereotype.Component;

/** Rolls up a fresh pack mule: OSE bestiary hit points ({@link MuleRules#HIT_DICE_COUNT}d
    {@link MuleRules#HIT_DIE_SIDES}), same {@code Math.max(1, ...)} floor {@code Monster.roll} uses. */
@Component
public class MuleFactory {

    private final Dice dice;

    public MuleFactory(Dice dice) {
        this.dice = dice;
    }

    public Mule create(String name) {
        Mule mule = new Mule();
        mule.setName(name);
        int hp = Math.max(1, dice.roll(MuleRules.HIT_DICE_COUNT, MuleRules.HIT_DIE_SIDES));
        mule.setMaxHp(hp);
        mule.setCurrentHp(hp);
        return mule;
    }
}
