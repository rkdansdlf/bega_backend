package com.example.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Slf4j
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(
            @Value("${app.flyway.auto-repair:false}") boolean autoRepair,
            Environment environment) {
        if (autoRepair && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("Flyway auto-repair is prohibited in the prod profile.");
        }
        return flyway -> {
            if (autoRepair) {
                log.warn("Flyway auto-repair is enabled. Repairing schema history before migrate.");
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
