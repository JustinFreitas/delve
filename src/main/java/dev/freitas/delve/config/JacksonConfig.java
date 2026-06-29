package dev.freitas.delve.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the {@link ObjectMapper} used to (de)serialize save games. Spring Boot 4's plain starter
 * does not autoconfigure one (Jackson autoconfig lives in a separate module), so we define it here.
 * Unknown properties are ignored so older saves keep loading as the save schema grows across
 * milestones.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
