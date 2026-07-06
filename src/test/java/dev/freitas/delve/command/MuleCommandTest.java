package dev.freitas.delve.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.game.MuleFactory;
import dev.freitas.delve.game.engine.AbilityScores;
import dev.freitas.delve.game.engine.CharacterClass;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.MuleRules;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Mule;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.MuleService;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@link MuleCommand#buyOne} -- the extracted purchase logic behind {@code /mule buy}. No test here
    constructs a {@link dev.freitas.delve.discord.CommandContext}, matching this codebase's established
    pattern of testing small extracted pure logic directly (see {@code HireCommandTest}). */
class MuleCommandTest {

    private final Dice dice = new Dice(new Random(3));
    private final MuleCommand muleCommand = new MuleCommand(new MuleService(), new MuleFactory(dice));

    @Test
    void buyOneDeductsTheFlatCostAndAssignsAnEligibleHandlerAndRollsOseStats() {
        SaveGame save = new SaveGame();
        Character pc = pc("Hero", 100);
        save.setCharacter(pc); // sword + shield -- 0 free hands

        Mule mule = muleCommand.buyOne(save, pc, "Jenny");

        assertThat(pc.getGold()).isEqualTo(100 - MuleRules.PURCHASE_COST_GP);
        assertThat(mule.getOwner()).isEqualTo("Hero");
        assertThat(save.getMules()).containsExactly(mule);
        assertThat(mule.getHandler()).isNull(); // the only PC has no free hand
        assertThat(mule.armorClass()).isEqualTo(MuleRules.ARMOR_CLASS);
        assertThat(mule.getMaxHp()).isEqualTo(mule.getCurrentHp()).isPositive();
    }

    @Test
    void buyOnePicksAFreeHandedPcAsHandlerWhenOneExists() {
        SaveGame save = new SaveGame();
        Character pc = pc("Hero", 100);
        pc.setShield(false); // now has a free hand
        save.setCharacter(pc);

        Mule mule = muleCommand.buyOne(save, pc, "Jenny");

        assertThat(mule.getHandler()).isEqualTo(SaveGame.PLAYER_SLOT);
    }

    private Character pc(String name, int gold) {
        Character c = new Character();
        c.setName(name);
        c.setCharacterClass(CharacterClass.FIGHTER);
        c.setAbilities(new AbilityScores(13, 9, 9, 12, 12, 12));
        c.setMaxHp(10);
        c.setCurrentHp(10);
        c.setGold(gold);
        c.setShield(true);
        c.setMainWeapon("Sword");
        return c;
    }
}
