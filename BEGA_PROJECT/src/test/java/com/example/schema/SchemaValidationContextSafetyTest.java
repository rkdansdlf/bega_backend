package com.example.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

class SchemaValidationContextSafetyTest {

    @Test
    void remoteSchemaValidationDoesNotRunMigrationsAndUsesReadOnlyConnections() {
        SpringBootTest annotation = SchemaValidationContextTest.class.getAnnotation(SpringBootTest.class);

        assertThat(annotation).isNotNull();
        assertThat(Arrays.asList(annotation.properties()))
            .contains(
                "spring.flyway.enabled=false",
                "spring.datasource.hikari.read-only=true",
                "baseball.datasource.hikari.read-only=true"
            )
            .doesNotContain(
                "spring.flyway.enabled=true",
                "spring.flyway.baseline-on-migrate=true"
            );
    }

    @Test
    void ciSchemaValidationIsSeparateAndRequiresExplicitDisposableDatabaseOptIn() {
        SpringBootTest annotation = SchemaValidationCiContextTest.class.getAnnotation(SpringBootTest.class);

        assertThat(annotation).isNotNull();
        assertThat(Arrays.asList(annotation.properties()))
            .contains(
                "spring.flyway.enabled=true",
                "spring.datasource.hikari.read-only=false"
            );
        assertThat(SchemaValidationCiContextTest.class.getAnnotation(EnabledIfEnvironmentVariable.class))
            .satisfies(condition -> {
                assertThat(condition.named()).isEqualTo("SCHEMA_VALIDATION_CI");
                assertThat(condition.matches()).isEqualTo("true");
            });
        assertThat(SchemaValidationCiContextTest.class.getAnnotation(EnabledIfSystemProperty.class))
            .satisfies(condition -> {
                assertThat(condition.named()).isEqualTo("schema.validation.ci.migrations");
                assertThat(condition.matches()).isEqualTo("true");
            });
    }
}
