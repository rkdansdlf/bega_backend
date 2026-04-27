package com.example.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(
            @Value("${app.flyway.auto-repair:false}") boolean autoRepair) {
        return flyway -> {
            if (autoRepair) {
                log.warn("Flyway auto-repair is enabled. Repairing schema history before migrate.");
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
