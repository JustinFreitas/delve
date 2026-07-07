package dev.freitas.delve.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * Boots the full context with the web interface enabled and the Discord gateway mocked, exercising the
 * REST API through Spring Security with a faked Discord OAuth principal whose name is the user id.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "config.token=test-token",
    "config.web.enabled=true",
    "config.web.admin-user-ids=999000111",
    "spring.datasource.url=jdbc:h2:mem:delve-web-test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
    "spring.security.oauth2.client.registration.discord.client-id=test-id",
    "spring.security.oauth2.client.registration.discord.client-secret=test-secret",
    "spring.security.oauth2.client.registration.discord.scope=identify",
    "spring.security.oauth2.client.registration.discord.authorization-grant-type=authorization_code",
    "spring.security.oauth2.client.registration.discord.redirect-uri={baseUrl}/login/oauth2/code/discord",
    "spring.security.oauth2.client.provider.discord.authorization-uri=https://discord.com/oauth2/authorize",
    "spring.security.oauth2.client.provider.discord.token-uri=https://discord.com/api/oauth2/token",
    "spring.security.oauth2.client.provider.discord.user-info-uri=https://discord.com/api/users/@me",
    "spring.security.oauth2.client.provider.discord.user-name-attribute=id"
})
class WebApiTest {

    @MockitoBean
    private ShardManager shardManager;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    // A Discord principal whose getName() returns the numeric user id (user-name-attribute=id).
    private static final OAuth2User DISCORD_USER = new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("id", "777000111"), "id");

    private static final OAuth2User ADMIN_USER = new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("id", "999000111"), "id");

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void unauthenticatedApiIsRejected() throws Exception {
        mvc.perform(get("/api/state")).andExpect(status().is3xxRedirection()); // -> OAuth login
    }

    @Test
    void stateForFreshAuthenticatedUser() throws Exception {
        mvc.perform(get("/api/state").with(oauth2Login().oauth2User(DISCORD_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCharacter").value(false));
    }

    @Test
    void rollCharacterPersistsAndIsReadableBack() throws Exception {
        mvc.perform(post("/api/character/roll")
                        .with(oauth2Login().oauth2User(DISCORD_USER))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cls\":\"fighter\",\"name\":\"Webfighter\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        // Read it back through the same shared save (keyed by the Discord id).
        mvc.perform(get("/api/character").with(oauth2Login().oauth2User(DISCORD_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Webfighter"))
                .andExpect(jsonPath("$.class").value("Fighter"));
    }

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/character/roll")
                        .with(oauth2Login().oauth2User(DISCORD_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cls\":\"fighter\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void undoEndpointsAndPermissionsWork() throws Exception {
        // 1. Check isAdmin returns false for DISCORD_USER (since not in admin list)
        mvc.perform(get("/api/user/admin").with(oauth2Login().oauth2User(DISCORD_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(false));

        // 2. Attempting to undo as non-admin returns forbidden error in ActionResult
        mvc.perform(post("/api/delve/undo")
                        .with(oauth2Login().oauth2User(DISCORD_USER))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.lines[0]").value("❌ Forbidden: Only administrator accounts can use time rewind."));
    }

    @Test
    void adminUndoRestoresPreviousState() throws Exception {
        // 1. Check isAdmin returns true for ADMIN_USER
        mvc.perform(get("/api/user/admin").with(oauth2Login().oauth2User(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        // 2. Roll a character as ADMIN_USER
        mvc.perform(post("/api/character/roll")
                        .with(oauth2Login().oauth2User(ADMIN_USER))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cls\":\"fighter\",\"name\":\"AdminHero\"}"))
                .andExpect(status().isOk());

        // 3. Enter dungeon
        mvc.perform(post("/api/delve/enter")
                        .with(oauth2Login().oauth2User(ADMIN_USER))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        // 4. Move East (which advances turn, creating a history snapshot)
        mvc.perform(post("/api/delve/move")
                        .with(oauth2Login().oauth2User(ADMIN_USER))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"east\"}"))
                .andExpect(status().isOk());

        // 5. Call undo to rollback the move
        mvc.perform(post("/api/delve/undo")
                        .with(oauth2Login().oauth2User(ADMIN_USER))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.lines[0]").value("🔮 **Time Rewound**: Rolled back the last action."));
    }
}
