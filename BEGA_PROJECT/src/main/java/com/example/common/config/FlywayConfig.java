package com.example.common.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Slf4j
@Configuration
@Lazy(false)
public class FlywayConfig {

    // Pinned by version, not file name: a rename or a copy into another folder must not disable the guard.
    private static final MigrationVersion ACCOUNT_DELETION_HASH_VERSION = MigrationVersion.fromVersion("193");

    @Bean
    @Lazy(false)
    public FlywayMigrationStrategy flywayMigrationStrategy(
            @Value("${app.flyway.auto-repair:false}") boolean autoRepair,
            Environment environment) {
        if (autoRepair && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("Flyway auto-repair is prohibited in the prod profile.");
        }
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        boolean readersReady = environment.getProperty(
                "app.flyway.account-deletion-hash-readers-ready", Boolean.class, false);
        if (production && !readersReady
                && environment.getProperty("app.auth.account-deletion.hash-new-tokens", Boolean.class, true)) {
            throw new IllegalStateException("Account deletion token hash issuance requires compatible readers "
                    + "and a compatible rollback version before enabling it.");
        }
        return flyway -> {
            if (production && !readersReady) {
                for (MigrationInfo migration : flyway.info().pending()) {
                    if (ACCOUNT_DELETION_HASH_VERSION.equals(migration.getVersion())) {
                        throw new IllegalStateException("Account deletion token migration requires compatible readers "
                                + "and a compatible rollback version before enabling it.");
                    }
                }
            }
            if (autoRepair) {
                log.warn("Flyway auto-repair is enabled. Repairing schema history before migrate.");
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
