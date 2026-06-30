package dev.freitas.delve.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Web-interface security, active only when {@code config.web.enabled=true}. Logs users in with Discord
 * OAuth2 (so the session's principal name is the Discord user id — the shared-save key), protects the
 * REST API and app, issues a JS-readable CSRF cookie, and installs {@link ApiGuardFilter} for the
 * allowlist + rate limit. TLS is expected to terminate at a reverse proxy (set
 * {@code server.forward-headers-strategy=framework}).
 */
@Configuration
@ConditionalOnProperty(name = "config.web.enabled", havingValue = "true")
public class SecurityConfig {

    @Bean
    SecurityFilterChain webSecurityFilterChain(HttpSecurity http, ApiGuardFilter apiGuard) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/style.css", "/app.js", "/favicon.ico", "/error",
                                "/login/**", "/oauth2/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2Login(login -> login.defaultSuccessUrl("/", true))
                .logout(logout -> logout.logoutSuccessUrl("/"))
                // JS reads XSRF-TOKEN and echoes it as the X-XSRF-TOKEN header on POSTs.
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .addFilterAfter(apiGuard, AuthorizationFilter.class);
        return http.build();
    }
}
