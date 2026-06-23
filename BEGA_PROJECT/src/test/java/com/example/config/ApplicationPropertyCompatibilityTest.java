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
    void devProfileDisablesHibernateSchemaManagementAgainstSharedPostgres() throws IOException {
        String applicationYml = readApplicationYml();

        assertThat(applicationYml)
                .contains("generate-ddl: false")
                .contains("ddl-auto: none")
                .contains("\"[hibernate.default_schema]\": public")
                .contains("\"[hibernate.boot.allow_jdbc_metadata_access]\": false")
                .contains("\"[hibernate.temp.use_jdbc_metadata_defaults]\": false")
                .contains("\"[hibernate.connection.driver_class]\": org.postgresql.Driver");
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
    void applicationYmlDefinesTomcatUploadTimeoutEnvKeys() throws IOException {
        String applicationYml = readApplicationYml();

        assertThat(applicationYml)
                .contains("keep-alive-timeout: ${SERVER_TOMCAT_KEEP_ALIVE_TIMEOUT:20000}")
                .contains("max-keep-alive-requests: ${SERVER_TOMCAT_MAX_KEEP_ALIVE_REQUESTS:100}")
                .contains("connection-upload-timeout-ms: ${SERVER_TOMCAT_CONNECTION_UPLOAD_TIMEOUT_MS:60000}")
                .contains("upload-timeout-enabled: ${SERVER_TOMCAT_UPLOAD_TIMEOUT_ENABLED:true}");
    }

    @Test
    void applicationYmlEnablesResponseCompressionForJsonAndFrontendAssets() throws IOException {
        String applicationYml = readApplicationYml();

        assertThat(applicationYml)
                .contains("compression:")
                .contains("enabled: ${SERVER_COMPRESSION_ENABLED:true}")
                .contains("mime-types: text/html,text/xml,text/plain,text/css,text/javascript,application/javascript,application/json,application/xml")
                .contains("min-response-size: ${SERVER_COMPRESSION_MIN_RESPONSE_SIZE:1024}");
    }

    @Test
    void applicationYmlDefinesHomeBootstrapLatencyEnvKeys() throws IOException {
        String applicationYml = readApplicationYml();

        assertThat(applicationYml)
                .contains("section-timeout-ms: ${APP_HOME_BOOTSTRAP_SECTION_TIMEOUT_MS:2500}")
                .contains("enabled: ${APP_HOME_BOOTSTRAP_WARMUP_ENABLED:true}")
                .contains("fixed-delay-ms: ${APP_HOME_BOOTSTRAP_WARMUP_FIXED_DELAY_MS:50000}")
                .contains("initial-delay-ms: ${APP_HOME_BOOTSTRAP_WARMUP_INITIAL_DELAY_MS:5000}")
                .contains("max-attempts: ${APP_HOME_BOOTSTRAP_WARMUP_MAX_ATTEMPTS:2}")
                .contains("partial-retry-delay-ms: ${APP_HOME_BOOTSTRAP_WARMUP_PARTIAL_RETRY_DELAY_MS:500}")
                .contains("section-timeout-ms: ${APP_HOME_BOOTSTRAP_WARMUP_SECTION_TIMEOUT_MS:8000}")
                .contains("enabled: ${APP_HOME_BOOTSTRAP_WARMUP_RANKING_ENABLED:true}")
                .contains("section-timeout-ms: ${APP_HOME_WIDGETS_SECTION_TIMEOUT_MS:1200}");
    }

    @Test
    void applicationYmlDefinesBackgroundWarmupThrottleEnvKeys() throws IOException {
        String applicationYml = readApplicationYml();

        assertThat(applicationYml)
                .contains("max-games-per-run: ${APP_PREDICTION_WARMUP_MAX_GAMES_PER_RUN:0}")
                .contains("fixed-delay-ms: ${APP_LEADERBOARD_GAME_RESULT_SCHEDULER_FIXED_DELAY_MS:600000}")
                .contains("initial-delay-ms: ${APP_LEADERBOARD_GAME_RESULT_SCHEDULER_INITIAL_DELAY_MS:600000}")
                .contains("yesterday-cron: \"${APP_LEADERBOARD_GAME_RESULT_SCHEDULER_YESTERDAY_CRON:0 0 2 * * *}\"");
    }

    @Test
    void applicationYmlDefinesDevDbHotPathPrewarmEnvKeys() throws IOException {
        String applicationYml = readApplicationYml();

        assertThat(applicationYml)
                .contains("enabled: ${APP_DEV_DB_HOT_PATH_PREWARM_ENABLED:false}")
                .contains("connections: ${APP_DEV_DB_HOT_PATH_PREWARM_CONNECTIONS:1}")
                .contains("timeout-ms: ${APP_DEV_DB_HOT_PATH_PREWARM_TIMEOUT_MS:30000}")
                .contains("login-email: ${APP_DEV_DB_HOT_PATH_PREWARM_LOGIN_EMAIL:latency-prewarm@example.invalid}")
                .contains("range-start-date: ${APP_DEV_DB_HOT_PATH_PREWARM_RANGE_START_DATE:2026-06-18}")
                .contains("range-end-date: ${APP_DEV_DB_HOT_PATH_PREWARM_RANGE_END_DATE:2026-06-24}")
                .contains("season-id: ${APP_DEV_DB_HOT_PATH_PREWARM_SEASON_ID:2026}");
    }

    private String readApplicationYml() throws IOException {
        return Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
    }
}
