package com.example.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPropertyCompatibilityTest {

    @Test
    void applicationYmlUsesCanonicalFrontendUrlKeyWithLegacyFallback() throws IOException {
        String applicationYml = readApplicationYml();

        assertThat(applicationYml)
                .contains("url: ${APP_FRONTEND_URL:${FRONTEND_URL:http://localhost:3000}}");
    }

    @Test
    void applicationYmlUsesCanonicalAllowedOriginsKey() throws IOException {
        String applicationYml = readApplicationYml();

        assertThat(applicationYml)
                .contains("allowed-origins: ${APP_ALLOWED_ORIGINS:");
    }

    @Test
    void applicationYmlUsesCanonicalBaseballDatasourceKeysWithDevFallback() throws IOException {
        String applicationYml = readApplicationYml();

        assertThat(applicationYml)
                .contains("url: ${BASEBALL_DB_URL:${DB_URL}}")
                .contains("username: ${BASEBALL_DB_USERNAME:${DB_USERNAME}}")
                .contains("password: ${BASEBALL_DB_PASSWORD:${DB_PASSWORD}}");
    }

    @Test
    void devProfileKeepsDbKeysForPrimaryDatasource() throws IOException {
        String applicationYml = readApplicationYml();

        assertThat(applicationYml)
                .contains("# Dev profile primary datasource keys remain DB_*.")
                .contains("url: ${DB_URL}")
                .contains("username: ${DB_USERNAME}")
                .contains("password: ${DB_PASSWORD}");
    }

    @Test
    void applicationYmlDoesNotUseDevOAuth2CookieSecretFallback() throws IOException {
        String applicationYml = readApplicationYml();

        assertThat(applicationYml)
                .doesNotContain("dev-oauth2-cookie-secret");
    }

    private String readApplicationYml() throws IOException {
        return Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
    }
}
