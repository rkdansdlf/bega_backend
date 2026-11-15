package com.example.common.readonly;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertySource;

/** Validates resolved ConfigData before datasource binding, configuration beans, or startup hooks. */
public final class ReadOnlyVerificationInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    private static final String REJECTION = "READONLY_VERIFICATION_CONFIGURATION_REJECTED";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
    private static final List<String> DISABLED = List.of(
            "spring.flyway.enabled", "baseball.flyway.enabled", "rag.flyway.enabled", "app.flyway.auto-repair",
            "spring.jpa.generate-ddl", "app.dev-data.enabled", "app.dev-data.repair-existing",
            "app.dev-db.hot-path-prewarm.enabled", "jobrunr.job-scheduler.enabled",
            "jobrunr.background-job-server.enabled", "jobrunr.dashboard.enabled",
            "app.mail.enabled", "spring.mail.test-connection", "management.health.mail.enabled",
            "app.realtime.outbox.enabled", "app.ai-ingest.enabled", "app.client-error-monitoring.alerts.enabled",
            "app.home.bootstrap.warmup.enabled", "app.home.bootstrap.warmup.ranking.enabled",
            "app.prediction.warmup.enabled", "app.prediction.warmup.detail.enabled",
            "app.prediction.warmup.vote-status.enabled", "app.prediction.ranking-settlement-scheduler.enabled",
            "app.leaderboard.game-result-scheduler.enabled", "app.cheer.post-sync.scheduler.enabled",
            "app.ai.coach-auto-brief.monitoring.enabled", "media.cleanup.enabled", "payment.payout.enabled",
            "app.allow-preview-origins");
    private static final List<String> OPTIONAL_DISABLED = List.of(
            "app.worker.enabled", "app.workers.enabled", "worker.enabled", "spring.liquibase.enabled");
    private static final List<String> DISABLED_ALIASES = List.of(
            "JOBRUNR_JOB_SCHEDULER_ENABLED", "JOBRUNR_BACKGROUND_JOB_SERVER_ENABLED", "JOBRUNR_DASHBOARD_ENABLED",
            "RAG_FLYWAY_ENABLED", "BASEBALL_FLYWAY_ENABLED", "APP_FLYWAY_AUTO_REPAIR",
            "APP_DEV_DB_HOT_PATH_PREWARM_ENABLED", "APP_MAIL_ENABLED", "APP_REALTIME_OUTBOX_ENABLED",
            "AI_INGEST_ENABLED", "MEDIA_CLEANUP_ENABLED", "PAYMENT_PAYOUT_ENABLED",
            "APP_HOME_BOOTSTRAP_WARMUP_ENABLED", "APP_HOME_BOOTSTRAP_WARMUP_RANKING_ENABLED",
            "APP_PREDICTION_WARMUP_ENABLED", "APP_PREDICTION_WARMUP_DETAIL_ENABLED",
            "APP_PREDICTION_WARMUP_VOTE_STATUS_ENABLED", "APP_LEADERBOARD_GAME_RESULT_SCHEDULER_ENABLED",
            "APP_CHEER_POST_SYNC_SCHEDULER_ENABLED", "APP_CLIENT_ERROR_MONITORING_ALERTS_ENABLED");
    private static final Map<String, String> INPUTS = Map.ofEntries(
            Map.entry("spring.datasource.url", "READONLY_PRIMARY_DB_URL"),
            Map.entry("spring.datasource.username", "READONLY_PRIMARY_DB_USERNAME"),
            Map.entry("spring.datasource.password", "READONLY_PRIMARY_DB_PASSWORD"),
            Map.entry("baseball.datasource.url", "READONLY_BASEBALL_DB_URL"),
            Map.entry("baseball.datasource.username", "READONLY_BASEBALL_DB_USERNAME"),
            Map.entry("baseball.datasource.password", "READONLY_BASEBALL_DB_PASSWORD"),
            Map.entry("spring.jwt.secret", "READONLY_JWT_SECRET"),
            Map.entry("app.oauth2.cookie-secret", "READONLY_OAUTH2_COOKIE_SECRET"),
            Map.entry("app.auth.refresh-token-pepper", "READONLY_REFRESH_TOKEN_PEPPER"),
            Map.entry("ai.internal-token", "READONLY_AI_INTERNAL_TOKEN"),
            Map.entry("oci.s3.access-key", "READONLY_S3_ACCESS_KEY"),
            Map.entry("oci.s3.secret-key", "READONLY_S3_SECRET_KEY"),
            Map.entry("oci.s3.bucket", "READONLY_S3_BUCKET"));
    private static final List<String> HTTP_TARGETS = List.of(
            "ai.service-url", "oci.s3.endpoint", "app.frontend.url", "app.backend.url",
            "toss.payment.confirm-url", "toss.payment.cancel-url", "toss.payout.base-url");

    @Override
    public int getOrder() {
        // Boot has already processed ConfigData before it invokes any context initializer.
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        validate(environment);
        if (environment.acceptsProfiles(Profiles.of(ReadOnlyVerificationPolicy.PROFILE))) {
            // Recheck settings changed by a later initializer before ordinary beans are instantiated.
            context.addBeanFactoryPostProcessor(beanFactory -> {
                if (!environment.acceptsProfiles(Profiles.of(ReadOnlyVerificationPolicy.PROFILE))) {
                    throw rejected("readonly profile removed during initialization");
                }
                validate(environment);
            });
        }
    }

    private void validate(ConfigurableEnvironment environment) {
        if (!environment.acceptsProfiles(Profiles.of(ReadOnlyVerificationPolicy.PROFILE))) {
            return;
        }
        try {
            validateResolvedConfiguration(environment);
        } catch (RuntimeException exception) {
            // Binding and URI failures can contain credentials or complete connection strings.
            // Preserve only our fixed diagnostic, never the original exception or its cause.
            if (exception instanceof IllegalStateException && exception.getMessage() != null
                    && exception.getMessage().startsWith(REJECTION)) {
                throw exception;
            }
            throw rejected("invalid or unresolved configuration");
        }
    }

    private void validateResolvedConfiguration(ConfigurableEnvironment environment) {
        if (Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> !Set.of("dev", ReadOnlyVerificationPolicy.PROFILE).contains(profile))) {
            throw rejected("incompatible profile");
        }
        for (PropertySource<?> source : environment.getPropertySources()) {
            rejectDotEnv(source);
        }
        Binder binder = Binder.get(environment);
        if (!binder.bind("spring.config.import", Bindable.listOf(String.class)).orElse(List.of()).stream()
                .allMatch(String::isBlank)) {
            throw rejected("config imports must be disabled at launch");
        }
        for (String key : DISABLED) {
            require(binder, key, "false");
        }
        for (String key : OPTIONAL_DISABLED) {
            String value = value(binder, key);
            if (value != null && !"false".equalsIgnoreCase(value)) {
                throw rejected("worker or migration flag");
            }
        }
        for (String alias : DISABLED_ALIASES) {
            String value = environment.getProperty(alias);
            if (value != null && !"false".equalsIgnoreCase(value)) {
                throw rejected("conflicting environment flag");
            }
        }
        require(binder, "jobrunr.database.skip-create", "true");
        require(binder, "spring.sql.init.mode", "never");
        require(binder, "spring.jpa.hibernate.ddl-auto", "validate");
        require(binder, "baseball.jpa.hibernate.ddl-auto", "validate");
        require(binder, "kbo.schema-guard.strict", "true");
        require(binder, "app.realtime.transport", "local");
        validateJpaProperties(binder, "spring.jpa.properties");
        validateJpaProperties(binder, "baseball.jpa.properties");
        for (Map.Entry<String, String> input : INPUTS.entrySet()) {
            requireInput(environment, binder, input.getKey(), input.getValue());
        }
        for (String key : List.of("spring.jwt.secret", "app.oauth2.cookie-secret",
                "app.auth.refresh-token-pepper", "ai.internal-token")) {
            if (value(binder, key).trim().length() < 32) {
                throw rejected("explicit verification authentication material is required");
            }
        }
        for (String prefix : List.of("spring.datasource", "baseball.datasource")) {
            validateDatasource(binder, prefix);
        }
        validateExclusions(binder);
        for (String key : HTTP_TARGETS) {
            requireLocalHttp(value(binder, key));
        }
        for (String host : List.of("server.address", "spring.data.redis.host", "spring.mail.host")) {
            requireLocalHost(value(binder, host));
        }
        for (String prefix : List.of("spring.data.redis.cluster", "spring.data.redis.sentinel")) {
            if (binder.bind(prefix, Bindable.mapOf(String.class, String.class)).isBound()) {
                throw rejected("redis topology override");
            }
        }
        for (String key : List.of("spring.data.redis.url", "spring.mail.jndi-name",
                "toss.payment.secret-key", "toss.payout.api-secret", "toss.payout.encryption-public-key-path")) {
            requireAbsentOrBlank(binder, key);
        }
        for (String origin : binder.bind("app.allowed-origins", Bindable.listOf(String.class)).orElse(List.of())) {
            requireLocalHttp(origin);
        }
        validateOAuth(binder, environment);
        validateLegacyTargets(environment);
    }

    private void validateDatasource(Binder binder, String prefix) {
        requireLocalJdbc(value(binder, prefix + ".url"));
        require(binder, prefix + ".driver-class-name", "org.postgresql.Driver");
        require(binder, prefix + ".hikari.read-only", "true");
        for (String suffix : List.of(".jndi-name", ".hikari.jdbc-url", ".hikari.data-source-class-name",
                ".hikari.connection-init-sql", ".hikari.connection-test-query", ".hikari.data-source-j-n-d-i")) {
            requireAbsentOrBlank(binder, prefix + suffix);
        }
        if (binder.bind(prefix + ".hikari.data-source-properties", Bindable.mapOf(String.class, String.class)).isBound()) {
            throw rejected("datasource property override");
        }
        Map<String, String> properties = binder.bind(prefix + ".data-source-properties",
                Bindable.mapOf(String.class, String.class)).orElse(Map.of());
        if (properties.keySet().stream().map(ReadOnlyVerificationInitializer::normalized)
                .anyMatch(key -> !Set.of("currentschema", "targetservertype", "options").contains(key))) {
            throw rejected("unapproved JDBC option");
        }
        if (!"-c default_transaction_read_only=on".equals(properties.get("options"))) {
            throw rejected("readonly database session option is required");
        }
    }

    private void validateJpaProperties(Binder binder, String prefix) {
        Map<String, String> properties = binder.bind(prefix, Bindable.mapOf(String.class, String.class)).orElse(Map.of());
        properties.forEach((key, setting) -> {
            String name = normalized(key);
            if ((name.equals("hibernatehbm2ddlauto") && !"validate".equals(setting))
                    || name.endsWith("schemagenerationdatabaseaction")
                    || (name.endsWith("schemagenerationscriptsaction") && !"none".equals(setting))
                    || name.startsWith("hibernatehbm2ddlimport")
                    || name.equals("hibernatehbm2ddldatabaseaction")
                    || name.endsWith("jdbcurl") || name.equals("hibernateconnectionurl")) {
                throw rejected("JPA validation or schema generation override");
            }
        });
    }

    private void validateExclusions(Binder binder) {
        List<String> exclusions = binder.bind("spring.autoconfigure.exclude", Bindable.listOf(String.class))
                .orElse(List.of());
        List<String> jobRunrConfigurations = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates().stream().filter(name -> name.startsWith("org.jobrunr.")).toList();
        if (jobRunrConfigurations.isEmpty() || !exclusions.containsAll(jobRunrConfigurations)
                || !exclusions.contains("io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration")) {
            throw rejected("all installed JobRunr auto-configurations must be excluded");
        }
    }

    private void validateOAuth(Binder binder, ConfigurableEnvironment environment) {
        for (String provider : List.of("google", "kakao", "naver")) {
            String registration = "spring.security.oauth2.client.registration." + provider;
            String inputPrefix = "READONLY_" + provider.toUpperCase(Locale.ROOT);
            requireInput(environment, binder, registration + ".client-id", inputPrefix + "_CLIENT_ID");
            requireInput(environment, binder, registration + ".client-secret", inputPrefix + "_CLIENT_SECRET");
            requireLocalHttp(value(binder, registration + ".redirect-uri"));
            String config = "spring.security.oauth2.client.provider." + provider;
            for (String endpoint : List.of("authorization-uri", "token-uri", "user-info-uri")) {
                requireLocalHttp(value(binder, config + "." + endpoint));
            }
            requireAbsentOrBlank(binder, config + ".issuer-uri");
            String jwks = value(binder, config + ".jwk-set-uri");
            if (jwks != null && !jwks.isBlank()) {
                requireLocalHttp(jwks);
            }
        }
    }

    private void validateLegacyTargets(ConfigurableEnvironment environment) {
        for (String key : List.of("DB_URL", "BASEBALL_DB_URL")) {
            if (environment.containsProperty(key)) {
                requireLocalJdbc(environment.getProperty(key));
            }
        }
        for (String key : List.of("AI_SERVICE_URL", "OCI_S3_ENDPOINT", "APP_FRONTEND_URL", "APP_BACKEND_URL")) {
            if (environment.containsProperty(key)) {
                requireLocalHttp(environment.getProperty(key));
            }
        }
        for (String key : List.of("REDIS_HOST", "MAIL_HOST")) {
            if (environment.containsProperty(key)) {
                requireLocalHost(environment.getProperty(key));
            }
        }
    }

    private void requireInput(ConfigurableEnvironment environment, Binder binder, String key, String input) {
        String supplied = environment.getProperty(input);
        if (supplied == null || supplied.isBlank() || supplied.contains("${")
                || !supplied.equals(value(binder, key))) {
            throw rejected("missing or inconsistent operator input: " + input);
        }
    }

    private void rejectDotEnv(PropertySource<?> source) {
        if (source.getName().toLowerCase(Locale.ROOT).contains(".env")) {
            throw rejected("dotenv property source");
        }
        if (source instanceof CompositePropertySource composite) {
            composite.getPropertySources().forEach(this::rejectDotEnv);
        }
    }

    private static String value(Binder binder, String key) {
        return binder.bind(key, String.class).orElse(null);
    }

    private void require(Binder binder, String key, String expected) {
        if (!expected.equals(value(binder, key))) {
            throw rejected("required setting: " + key);
        }
    }

    private void requireAbsentOrBlank(Binder binder, String key) {
        String setting = value(binder, key);
        if (setting != null && !setting.isBlank()) {
            throw rejected("forbidden override: " + key);
        }
    }

    private void requireLocalJdbc(String setting) {
        if (setting == null || !setting.startsWith("jdbc:postgresql://")) {
            throw rejected("explicit local PostgreSQL URL is required");
        }
        URI uri = URI.create(setting.substring(5));
        requireLocalHost(uri.getHost());
        if (uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getFragment() != null
                || uri.getPort() < 1 || uri.getPort() > 65535
                || uri.getPath() == null || !uri.getPath().matches("/[A-Za-z0-9_-]+")) {
            throw rejected("unapproved JDBC URL form");
        }
    }

    private void requireLocalHttp(String setting) {
        if (setting == null || setting.isBlank()) {
            throw rejected("explicit local HTTP target is required");
        }
        URI uri = URI.create(setting);
        requireLocalHost(uri.getHost());
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getUserInfo() != null
                || uri.getRawQuery() != null || uri.getFragment() != null) {
            throw rejected("unapproved HTTP target");
        }
    }

    private void requireLocalHost(String host) {
        if (host == null || !LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw rejected("nonlocal target");
        }
    }

    private static String normalized(String key) {
        return key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static IllegalStateException rejected(String reason) {
        return new IllegalStateException(REJECTION + ": " + reason);
    }
}
