package com.example.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalPostgresProfileConfigurationTest {

    @Test
    void localPostgresProfileUsesDedicatedCredentialsAndPostgresMigrations() throws IOException {
        String profile = readProfile();

        assertThat(profile)
                .contains("on-profile: local-postgres")
                .contains("url: ${LOCAL_POSTGRES_PRIMARY_URL:?LOCAL_POSTGRES_PRIMARY_URL is required for local-postgres}")
                .contains("url: ${LOCAL_POSTGRES_BASEBALL_URL:?LOCAL_POSTGRES_BASEBALL_URL is required for local-postgres}")
                .contains("locations: classpath:db/migration_postgresql/")
                .contains("enabled: true")
                .contains("targetServerType: primary");
    }

    @Test
    void localPostgresProfileDoesNotFallbackToSharedOrProductionDbVariables() throws IOException {
        String profile = readProfile();

        assertThat(profile)
                .doesNotContain("${DB_URL}")
                .doesNotContain("${BASEBALL_DB_URL}")
                .doesNotContain("${SPRING_DATASOURCE_URL}")
                .doesNotContain("${BASEBALL_DB_USERNAME}")
                .doesNotContain("${BASEBALL_DB_PASSWORD}");
    }

    @Test
    void localPostgresProfileKeepsSchemaOwnershipExplicit() throws IOException {
        String profile = readProfile();

        assertThat(profile)
                .contains("ddl-auto: none")
                .contains("strict: true")
                .contains("baseball:")
                .contains("  flyway:\n    enabled: false")
                .contains("repair-existing: false")
                .contains("monitoring:")
                .contains("enabled: ${LOCAL_AI_COACH_MONITORING_ENABLED:false}");
    }

    private String readProfile() throws IOException {
        return Files.readString(
                Path.of("src/main/resources/application-local-postgres.yml"),
                StandardCharsets.UTF_8);
    }
}
