package com.example.common.config;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Optional live-database validation for the two production Flyway histories.
 *
 * <p>This test validates checksums only. It never migrates, repairs, or cleans a database.
 * Enable it explicitly with {@code MIGRATION_LIVE_VALIDATION=true} and provide both
 * database credential sets through environment variables.</p>
 */
class FlywayLiveDatabaseValidationTest {

    @Test
    void postgresMigrationHistoryValidatesAgainstConfiguredDatabase() {
        assumeEnabled();
        validate(
                "MIGRATION_POSTGRES_URL",
                "MIGRATION_POSTGRES_USERNAME",
                "MIGRATION_POSTGRES_PASSWORD",
                "classpath:db/migration_postgresql");
    }

    @Test
    void oracleMigrationHistoryValidatesAgainstConfiguredDatabase() {
        assumeEnabled();
        validate(
                "MIGRATION_ORACLE_URL",
                "MIGRATION_ORACLE_USERNAME",
                "MIGRATION_ORACLE_PASSWORD",
                "classpath:db/migration");
    }

    private void assumeEnabled() {
        boolean enabled = Boolean.parseBoolean(
                System.getProperty(
                        "liveMigrationValidation",
                        System.getenv().getOrDefault("MIGRATION_LIVE_VALIDATION", "false")));
        assumeTrue(enabled, "Live migration validation is disabled");
    }

    private void validate(
            String urlKey,
            String usernameKey,
            String passwordKey,
            String location) {
        Flyway.configure()
                .dataSource(required(urlKey), required(usernameKey), required(passwordKey))
                .locations(location)
                .cleanDisabled(true)
                .load()
                .validate();
    }

    private String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required when live migration validation is enabled");
        }
        return value;
    }
}
