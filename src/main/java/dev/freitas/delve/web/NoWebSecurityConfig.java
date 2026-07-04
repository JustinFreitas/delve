package dev.freitas.delve.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * When the web interface is disabled but the app is still a servlet web app (e.g. tests), Spring
 * Security is on the classpath (it arrives with oauth2-client). This permit-all chain keeps that case
 * clean — no generated login page or secured endpoints. It is gated to servlet web applications: the
 * true headless deployment runs with {@code web-application-type=none}, where there is no servlet
 * stack (and no {@code HttpSecurity}) at all, so no security chain is needed or buildable.
 */
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnProperty(name = "config.web.enabled", havingValue = "false", matchIfMissing = true)
public class NoWebSecurityConfig {

    @Bean
    SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
