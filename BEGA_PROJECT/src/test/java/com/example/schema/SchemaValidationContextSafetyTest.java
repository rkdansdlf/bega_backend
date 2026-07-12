package com.example.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
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
}
