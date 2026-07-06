package dev.freitas.delve.api;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
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
 * DM tools and module listing. {@code roster}/{@code npc} are stateless (they never touch the
 * caller's own save); {@code export} returns the caller's current character as a stat block or JSON.
 */
@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "config.web.enabled", havingValue = "true")
public class DmController {

    private final GameFacade game;

    public DmController(GameFacade game) {
        this.game = game;
    }

    private static long userId(OAuth2User principal) {
        return Long.parseLong(principal.getName());
    }

    @GetMapping("/modules")
    public List<String> modules() {
        return game.listModules();
    }

    @PostMapping("/dm/roster")
    public List<Map<String, Object>> roster(@RequestBody RosterRequest req) {
        return game.roster(req.count(), req.level(), req.cls());
    }

    @PostMapping("/dm/npc")
    public ResponseEntity<Map<String, Object>> npc(@RequestBody NpcRequest req) {
        Map<String, Object> npc = game.npc(req.level(), req.cls(), req.name());
        return npc == null ? ResponseEntity.badRequest().build() : ResponseEntity.ok(npc);
    }

    @GetMapping("/dm/export")
    public ResponseEntity<?> export(@AuthenticationPrincipal OAuth2User principal,
            @RequestParam(defaultValue = "json") String format, @RequestParam(required = false) String pc) {
        long id = userId(principal);
        if (format.equalsIgnoreCase("text")) {
            String text = game.exportText(id, pc);
            return text == null ? ResponseEntity.noContent().build()
                    : ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(text);
        }
        Map<String, Object> json = game.exportJson(id, pc);
        return json == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(json);
    }

    public record RosterRequest(int count, int level, String cls) {}

    public record NpcRequest(String cls, int level, String name) {}
}
