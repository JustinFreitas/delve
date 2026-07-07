package dev.freitas.delve.api;

import dev.freitas.delve.api.dto.ActionResult;
import dev.freitas.delve.api.dto.StateSnapshot;
import dev.freitas.delve.config.WebProps;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Play + character endpoints. Each resolves the Discord user id from the OAuth2 principal (the
 * shared-save key) and delegates to {@link GameFacade}; the same logic the Discord commands use.
 */
@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "config.web.enabled", havingValue = "true")
public class GameController {

    private final GameFacade game;
    private final WebProps webProps;

    public GameController(GameFacade game, WebProps webProps) {
        this.game = game;
        this.webProps = webProps;
    }

    private static long userId(OAuth2User principal) {
        return Long.parseLong(principal.getName());
    }

    @GetMapping("/state")
    public StateSnapshot state(@AuthenticationPrincipal OAuth2User principal) {
        return game.state(userId(principal));
    }

    @GetMapping("/character")
    public ResponseEntity<Map<String, Object>> character(
            @AuthenticationPrincipal OAuth2User principal, @RequestParam(required = false) String pc) {
        Map<String, Object> c = game.character(userId(principal), pc);
        return c == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(c);
    }

    @PostMapping("/character/roll")
    public ActionResult roll(@AuthenticationPrincipal OAuth2User principal, @RequestBody RollRequest req) {
        return game.rollCharacter(userId(principal), req.cls(), req.name());
    }

    @PostMapping("/character/pregen")
    public ActionResult pregen(@AuthenticationPrincipal OAuth2User principal, @RequestBody PregenRequest req) {
        return game.pregen(userId(principal), req.cls(), req.level(), req.name());
    }

    @PostMapping("/delve/enter")
    public ActionResult enter(@AuthenticationPrincipal OAuth2User principal, @RequestBody(required = false) EnterRequest req) {
        return game.enter(userId(principal), req == null ? null : req.module());
    }

    @GetMapping("/delve/look")
    public ActionResult look(@AuthenticationPrincipal OAuth2User principal) {
        return game.look(userId(principal));
    }

    @PostMapping("/delve/move")
    public ActionResult move(@AuthenticationPrincipal OAuth2User principal, @RequestBody DirectionRequest req) {
        return game.move(userId(principal), req.direction());
    }

    @PostMapping("/delve/search")
    public ActionResult search(@AuthenticationPrincipal OAuth2User principal) {
        return game.search(userId(principal));
    }

    @PostMapping("/delve/rest")
    public ActionResult rest(@AuthenticationPrincipal OAuth2User principal) {
        return game.rest(userId(principal));
    }

    @PostMapping("/delve/undo")
    public ActionResult undo(@AuthenticationPrincipal OAuth2User principal) {
        long userId = userId(principal);
        if (!webProps.isAdmin(String.valueOf(userId))) {
            return ActionResult.of(false, List.of("❌ Forbidden: Only administrator accounts can use time rewind."), game.state(userId));
        }
        return game.undo(userId);
    }

    @PostMapping("/delve/open")
    public ActionResult open(@AuthenticationPrincipal OAuth2User principal, @RequestBody DirectionRequest req) {
        return game.open(userId(principal), req.direction());
    }

    @PostMapping("/delve/attack")
    public ActionResult attack(@AuthenticationPrincipal OAuth2User principal, @RequestBody(required = false) AttackRequest req) {
        return game.attack(userId(principal), req == null ? null : req.pc(), req == null ? null : req.target());
    }

    @PostMapping("/delve/flee")
    public ActionResult flee(@AuthenticationPrincipal OAuth2User principal) {
        return game.flee(userId(principal));
    }

    @PostMapping("/delve/cast")
    public ActionResult cast(@AuthenticationPrincipal OAuth2User principal, @RequestBody CastRequest req) {
        return game.cast(userId(principal), req.pc(), req.spell(), req.target());
    }

    @PostMapping("/delve/prepare")
    public ActionResult prepare(@AuthenticationPrincipal OAuth2User principal, @RequestBody SpellRequest req) {
        return game.prepare(userId(principal), req.pc(), req.spell());
    }

    @PostMapping("/delve/quaff")
    public ActionResult quaff(@AuthenticationPrincipal OAuth2User principal, @RequestBody(required = false) PcRequest req) {
        return game.quaff(userId(principal), req == null ? null : req.pc());
    }

    @PostMapping("/delve/town")
    public ActionResult town(@AuthenticationPrincipal OAuth2User principal) {
        return game.town(userId(principal));
    }

    @PostMapping("/delve/hire")
    public ActionResult hire(@AuthenticationPrincipal OAuth2User principal, @RequestBody HireRequest req) {
        return game.hire(userId(principal), req.pc(), req.cls(), req.name());
    }

    @GetMapping("/delve/party")
    public ActionResult party(@AuthenticationPrincipal OAuth2User principal) {
        return game.party(userId(principal));
    }

    @PostMapping("/delve/dismiss")
    public ActionResult dismiss(@AuthenticationPrincipal OAuth2User principal, @RequestBody NameRequest req) {
        return game.dismiss(userId(principal), req.pc(), req.name());
    }

    @PostMapping("/delve/mule/buy")
    public ActionResult buyMule(@AuthenticationPrincipal OAuth2User principal, @RequestBody(required = false) MuleBuyRequest req) {
        return game.buyMule(userId(principal), req == null ? null : req.pc(), req == null ? null : req.name());
    }

    @PostMapping("/delve/mule/load")
    public ActionResult loadMule(@AuthenticationPrincipal OAuth2User principal, @RequestBody MuleGoldRequest req) {
        return game.loadMule(userId(principal), req.pc(), req.gold());
    }

    @PostMapping("/delve/mule/unload")
    public ActionResult unloadMule(@AuthenticationPrincipal OAuth2User principal, @RequestBody MuleGoldRequest req) {
        return game.unloadMule(userId(principal), req.pc(), req.gold());
    }

    @PostMapping("/delve/mule/handler")
    public ActionResult muleHandler(@AuthenticationPrincipal OAuth2User principal, @RequestBody MuleHandlerRequest req) {
        return game.muleHandler(userId(principal), req.name());
    }

    @PostMapping("/delve/bank/deposit")
    public ActionResult bankDeposit(@AuthenticationPrincipal OAuth2User principal, @RequestBody BankGoldRequest req) {
        return game.bankDeposit(userId(principal), req.pc(), req.gold());
    }

    @PostMapping("/delve/bank/withdraw")
    public ActionResult bankWithdraw(@AuthenticationPrincipal OAuth2User principal, @RequestBody BankGoldRequest req) {
        return game.bankWithdraw(userId(principal), req.pc(), req.gold());
    }

    @GetMapping("/user/admin")
    public boolean isAdmin(@AuthenticationPrincipal OAuth2User principal) {
        return webProps.isAdmin(String.valueOf(userId(principal)));
    }

    // Request bodies. `pc` (where present) names a specific PC in a multi-PC party; null/blank
    // defaults to the primary (first-rolled) PC, same as the equivalent Discord command's [pc-name].
    public record RollRequest(String cls, String name) {}

    public record PregenRequest(String cls, int level, String name) {}

    public record EnterRequest(String module) {}

    public record DirectionRequest(String direction) {}

    public record AttackRequest(String pc, Integer target) {}

    public record CastRequest(String pc, String spell, Integer target) {}

    public record SpellRequest(String pc, String spell) {}

    public record HireRequest(String pc, String cls, String name) {}

    public record NameRequest(String pc, String name) {}

    public record PcRequest(String pc) {}

    public record MuleBuyRequest(String pc, String name) {}

    public record MuleGoldRequest(String pc, int gold) {}

    public record MuleHandlerRequest(String name) {}

    public record BankGoldRequest(String pc, int gold) {}
}
