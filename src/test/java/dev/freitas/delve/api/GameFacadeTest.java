package dev.freitas.delve.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.freitas.delve.api.dto.ActionResult;
import dev.freitas.delve.command.PartySummary;
import dev.freitas.delve.data.GameStateService;
import dev.freitas.delve.game.CharacterFactory;
import dev.freitas.delve.game.MuleFactory;
import dev.freitas.delve.game.PregenService;
import dev.freitas.delve.game.RetainerFactory;
import dev.freitas.delve.game.dungeon.DungeonGenerator;
import dev.freitas.delve.game.engine.Dice;
import dev.freitas.delve.game.engine.GameClock;
import dev.freitas.delve.game.model.Character;
import dev.freitas.delve.game.model.Mule;
import dev.freitas.delve.game.model.Retainer;
import dev.freitas.delve.game.model.SaveGame;
import dev.freitas.delve.game.session.CombatService;
import dev.freitas.delve.game.session.ExplorationService;
import dev.freitas.delve.game.session.LightingService;
import dev.freitas.delve.game.session.MuleService;
import dev.freitas.delve.game.session.SpellService;
import dev.freitas.delve.game.session.TownService;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** {@link GameFacade} — the web layer's Discord-agnostic orchestration, first test coverage for the
    {@code api} package. Constructs the facade directly against real domain services (no Spring context,
    no HTTP/security) with an in-memory fake {@link GameStateService} in place of the real
    JSON+H2-backed one, exercising: additive multi-PC roll/pregen, {@link GameFacade#resolveActor}-style
    PC-name resolution, {@code party()} matching {@link PartySummary} exactly, and the four mule
    actions. */
class GameFacadeTest {

    private final Dice dice = new Dice(new Random(77));
    private final Map<Long, SaveGame> store = new HashMap<>();
    private final GameStateService gameState = new GameStateService(null, null) {
        @Override
        public SaveGame load(long userId) {
            return store.computeIfAbsent(userId, k -> new SaveGame());
        }

        @Override
        public SaveGame mutate(long userId, Consumer<SaveGame> mutator) {
            SaveGame s = load(userId);
            mutator.accept(s);
            save(userId, s);
            return s;
        }

        @Override
        public void save(long userId, SaveGame state) {
            store.put(userId, state);
        }
    };

    private final SpellService spells = new SpellService(dice);
    private final CombatService combat = new CombatService(dice, spells);
    private final GameFacade facade = new GameFacade(
            gameState,
            new ExplorationService(dice, new DungeonGenerator(dice), combat, new LightingService(), new MuleService()),
            combat,
            spells,
            new TownService(spells, dice, new GameClock()),
            new PregenService(dice, new CharacterFactory(dice), spells),
            new RetainerFactory(dice),
            new CharacterFactory(dice),
            new MuleService(),
            new MuleFactory(dice),
            dice);

    private static final long USER = 42L;

    // --- rollCharacter/pregen: additive ---------------------------------------

    @Test
    void firstRollBecomesThePrimaryPc() {
        ActionResult result = facade.rollCharacter(USER, "fighter", "Conan");

        assertThat(result.ok()).isTrue();
        SaveGame save = store.get(USER);
        assertThat(save.getCharacters()).hasSize(1);
        assertThat(save.getCharacter().getName()).isEqualTo("Conan");
    }

    @Test
    void secondRollAddsAPcRatherThanReplacingThePrimary() {
        facade.rollCharacter(USER, "fighter", "Conan");
        ActionResult result = facade.rollCharacter(USER, "cleric", "Brother Tuck");

        assertThat(result.ok()).isTrue();
        SaveGame save = store.get(USER);
        assertThat(save.getCharacters()).hasSize(2);
        assertThat(save.getCharacter().getName()).isEqualTo("Conan"); // primary unchanged
        assertThat(save.getCharacters().get(1).getName()).isEqualTo("Brother Tuck");
    }

    @Test
    void rollingAPcWithAnAlreadyTakenNameFails() {
        facade.rollCharacter(USER, "fighter", "Conan");
        ActionResult result = facade.rollCharacter(USER, "cleric", "Conan");

        assertThat(result.ok()).isFalse();
        assertThat(store.get(USER).getCharacters()).hasSize(1);
    }

    @Test
    void rollingPastTheMaxCharacterCapFails() {
        for (int i = 0; i < SaveGame.MAX_CHARACTERS; i++) {
            assertThat(facade.rollCharacter(USER, "fighter", "PC" + i).ok()).isTrue();
        }
        ActionResult result = facade.rollCharacter(USER, "fighter", "OneTooMany");

        assertThat(result.ok()).isFalse();
        assertThat(store.get(USER).getCharacters()).hasSize(SaveGame.MAX_CHARACTERS);
    }

    // --- resolveActor-style pcName resolution (via quaff, a simple representative action) ---

    @Test
    void actionsDefaultToThePrimaryPcWhenNoPcNameIsGiven() {
        facade.rollCharacter(USER, "fighter", "Conan");
        Character conan = store.get(USER).getCharacter();
        conan.setHealingPotions(1);
        conan.setCurrentHp(1);

        ActionResult result = facade.quaff(USER, null);

        assertThat(result.ok()).isTrue();
        assertThat(conan.getHealingPotions()).isZero();
    }

    @Test
    void actionsCanTargetANamedPcInAMultiPcParty() {
        facade.rollCharacter(USER, "fighter", "Conan");
        facade.rollCharacter(USER, "cleric", "Brother Tuck");
        SaveGame save = store.get(USER);
        Character tuck = save.getCharacters().get(1);
        tuck.setHealingPotions(1);
        tuck.setCurrentHp(1);

        ActionResult result = facade.quaff(USER, "Brother Tuck");

        assertThat(result.ok()).isTrue();
        assertThat(tuck.getHealingPotions()).isZero();
        assertThat(save.getCharacter().getHealingPotions()).isZero(); // Conan untouched (had none anyway)
    }

    @Test
    void anUnresolvablePcNameFailsRatherThanSilentlyActingAsThePrimary() {
        facade.rollCharacter(USER, "fighter", "Conan");

        ActionResult result = facade.quaff(USER, "Nobody Here");

        assertThat(result.ok()).isFalse();
        assertThat(result.lines().get(0)).contains("No character named");
    }

    // --- party(): matches PartySummary exactly ---------------------------------

    @Test
    void partyMatchesPartySummaryForAMultiPcMuleParty() {
        facade.rollCharacter(USER, "fighter", "Conan");
        facade.rollCharacter(USER, "cleric", "Brother Tuck");
        SaveGame save = store.get(USER);
        facade.buyMule(USER, "Conan", "Ned");

        ActionResult result = facade.party(USER);

        assertThat(result.ok()).isTrue();
        assertThat(String.join("\n", result.lines())).isEqualTo(PartySummary.text(save));
    }

    // --- mule actions -----------------------------------------------------

    @Test
    void buyMuleChargesThePayerAndAddsItToTheParty() {
        facade.rollCharacter(USER, "fighter", "Conan");
        SaveGame save = store.get(USER);
        Character conan = save.getCharacter();
        conan.setGold(100);

        ActionResult result = facade.buyMule(USER, "Conan", "Ned");

        assertThat(result.ok()).isTrue();
        assertThat(conan.getGold()).isEqualTo(70); // 100 - 30gp purchase cost
        assertThat(save.getMules()).hasSize(1);
        assertThat(save.getMules().get(0).getName()).isEqualTo("Ned");
        assertThat(save.getMules().get(0).getOwner()).isEqualTo("Conan");
    }

    @Test
    void cannotBuyASecondMule() {
        facade.rollCharacter(USER, "fighter", "Conan");
        store.get(USER).getCharacter().setGold(1000);
        facade.buyMule(USER, "Conan", "Ned");

        ActionResult result = facade.buyMule(USER, "Conan", "Fred");

        assertThat(result.ok()).isFalse();
        assertThat(store.get(USER).getMules()).hasSize(1);
    }

    @Test
    void cannotAffordAMuleWithoutEnoughGold() {
        facade.rollCharacter(USER, "fighter", "Conan");
        store.get(USER).getCharacter().setGold(5);

        ActionResult result = facade.buyMule(USER, "Conan", "Ned");

        assertThat(result.ok()).isFalse();
        assertThat(store.get(USER).getMules()).isEmpty();
    }

    @Test
    void loadAndUnloadMoveGoldBetweenThePcAndTheMule() {
        facade.rollCharacter(USER, "fighter", "Conan");
        SaveGame save = store.get(USER);
        Character conan = save.getCharacter();
        conan.setGold(500);
        facade.buyMule(USER, "Conan", "Ned");
        Mule mule = save.getMules().get(0);

        ActionResult loadResult = facade.loadMule(USER, "Conan", 200);
        assertThat(loadResult.ok()).isTrue();
        assertThat(mule.getCarriedGold()).isEqualTo(200);
        assertThat(conan.getGold()).isEqualTo(270); // 470 (after buying) - 200 loaded

        ActionResult unloadResult = facade.unloadMule(USER, "Conan", 50);
        assertThat(unloadResult.ok()).isTrue();
        assertThat(mule.getCarriedGold()).isEqualTo(150);
        assertThat(conan.getGold()).isEqualTo(320);
    }

    @Test
    void muleHandlerAssignsAFreeHandedPartyMember() {
        facade.rollCharacter(USER, "fighter", "Conan"); // sword + shield -- 0 free hands after auto-gear
        SaveGame save = store.get(USER);
        save.getCharacter().setGold(1000);
        facade.buyMule(USER, "Conan", "Ned"); // no eligible handler yet -- Conan alone, 0 free hands
        assertThat(save.getMules().get(0).getHandler()).isNull();

        // A free-handed retainer joins afterward -- not auto-assigned (that only happens at buy time),
        // so an explicit /mule handler-equivalent call is what should actually assign her.
        Retainer thief = new RetainerFactory(dice).create("Nessa", dev.freitas.delve.game.engine.CharacterClass.THIEF, 1, 9);
        save.getRetainers().add(thief);

        ActionResult result = facade.muleHandler(USER, "Nessa");

        assertThat(result.ok()).isTrue();
        assertThat(save.getMules().get(0).getHandler()).isEqualTo("Nessa");
    }

    @Test
    void muleActionsFailCleanlyWithNoMule() {
        facade.rollCharacter(USER, "fighter", "Conan");

        assertThat(facade.loadMule(USER, "Conan", 10).ok()).isFalse();
        assertThat(facade.unloadMule(USER, "Conan", 10).ok()).isFalse();
        assertThat(facade.muleHandler(USER, "Conan").ok()).isFalse();
    }
}
