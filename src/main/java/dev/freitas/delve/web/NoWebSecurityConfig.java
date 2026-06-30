package dev.freitas.delve.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * When the web interface is disabled (the default), Spring Security is still on the classpath (it
 * arrives with oauth2-client). This permit-all chain keeps the bot-only deployment clean — no
 * generated login page or password, no secured endpoints — since there are no web endpoints anyway.
 */
@Configuration
@ConditionalOnProperty(name = "config.web.enabled", havingValue = "false", matchIfMissing = true)
public class NoWebSecurityConfig {

    @Bean
    SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
