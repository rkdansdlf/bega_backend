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
                .contains("url: ${APP_FRONTEND_URL:${FRONTEND_URL:http://localhost:5176}}");
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

    @Test
    void applicationYmlProvidesLoopbackOAuth2CookieSecretFallbackForDevAndLocal() throws IOException {
        String applicationYml = readApplicationYml();

        assertThat(applicationYml)
                .contains("cookie-secret: ${OAUTH2_COOKIE_SECRET:local-runtime-cookie-signing-secret}");
    }

    @Test
    void applicationYmlDefinesAiProxyBodyLimitEnvKeys() throws IOException {
        String applicationYml = readApplicationYml();

        assertThat(applicationYml)
                .contains("max-chat-json-bytes: ${APP_AI_PROXY_MAX_CHAT_JSON_BYTES:12288}")
                .contains("max-coach-json-bytes: ${APP_AI_PROXY_MAX_COACH_JSON_BYTES:65536}")
                .contains("max-voice-upload-bytes: ${APP_AI_PROXY_MAX_VOICE_UPLOAD_BYTES:10485760}")
                .contains("max-voice-request-bytes: ${APP_AI_PROXY_MAX_VOICE_REQUEST_BYTES:10551296}")
                .contains("max-admin-json-bytes: ${APP_AI_PROXY_MAX_ADMIN_JSON_BYTES:262144}")
                .contains("max-chat-persistence-json-bytes: ${APP_AI_PROXY_MAX_CHAT_PERSISTENCE_JSON_BYTES:131072}");
    }

    @Test
    void applicationYmlDefinesHomeBootstrapLatencyEnvKeys() throws IOException {
        String applicationYml = readApplicationYml();

        assertThat(applicationYml)
                .contains("section-timeout-ms: ${APP_HOME_BOOTSTRAP_SECTION_TIMEOUT_MS:2500}")
                .contains("enabled: ${APP_HOME_BOOTSTRAP_WARMUP_ENABLED:true}")
                .contains("fixed-delay-ms: ${APP_HOME_BOOTSTRAP_WARMUP_FIXED_DELAY_MS:50000}");
    }

    private String readApplicationYml() throws IOException {
        return Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
    }
}
