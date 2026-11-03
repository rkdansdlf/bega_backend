package com.example.schema;

import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.generate-ddl=false",
        "spring.main.lazy-initialization=true",
        "spring.flyway.enabled=true",
        "kbo.schema-guard.strict=false",
        "app.oauth2.cookie-secret=test-oauth2-cookie-secret",
        "spring.datasource.url=${DB_URL}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.datasource.data-source-properties.currentSchema=public",
        "spring.datasource.hikari.schema=public",
        "spring.datasource.hikari.read-only=false",
        "baseball.datasource.url=${BASEBALL_DB_URL:${DB_URL}}",
        "baseball.datasource.driver-class-name=org.postgresql.Driver",
        "baseball.datasource.username=${BASEBALL_DB_USERNAME:${DB_USERNAME}}",
        "baseball.datasource.password=${BASEBALL_DB_PASSWORD:${DB_PASSWORD}}",
        "baseball.datasource.hikari.read-only=true",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false"
    }
)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SCHEMA_VALIDATION_CI", matches = "true")
@EnabledIfSystemProperty(named = "schema.validation.ci.migrations", matches = "true")
class SchemaValidationCiContextTest {

    @MockitoBean
    private JobScheduler jobScheduler;

    @Test
    void migratesDisposablePostgresThenLoadsWithSchemaValidation() {
    }
}
