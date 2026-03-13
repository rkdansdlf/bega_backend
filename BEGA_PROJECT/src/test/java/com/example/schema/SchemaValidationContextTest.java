package com.example.schema;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.generate-ddl=false",
        "spring.main.lazy-initialization=true",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration_postgresql",
        "spring.flyway.baseline-on-migrate=true",
        "spring.flyway.validate-on-migrate=true",
        "kbo.schema-guard.strict=false",
        "spring.datasource.url=${DB_URL}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.datasource.data-source-properties.currentSchema=public",
        "spring.datasource.hikari.schema=public",
        "baseball.datasource.url=${BASEBALL_DB_URL:${DB_URL}}",
        "baseball.datasource.driver-class-name=org.postgresql.Driver",
        "baseball.datasource.username=${BASEBALL_DB_USERNAME:${DB_USERNAME}}",
        "baseball.datasource.password=${BASEBALL_DB_PASSWORD:${DB_PASSWORD}}",
        "org.jobrunr.background-job-server.enabled=false",
        "org.jobrunr.dashboard.enabled=false"
    }
)
@ActiveProfiles("dev")
class SchemaValidationContextTest {

    @Test
    void contextLoadsWithSchemaValidation() {
    }
}
