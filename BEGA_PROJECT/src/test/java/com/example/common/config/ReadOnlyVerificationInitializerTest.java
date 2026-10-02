package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import com.example.common.readonly.ReadOnlyVerificationInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class ReadOnlyVerificationInitializerTest {

    @Test
    void explicitLocalProfilePassesBeforeAnyBeanIsCreated() {
        AtomicInteger created = new AtomicInteger();
        DataSource dataSource = mock(DataSource.class);
        try (GenericApplicationContext context = context(ReadOnlyVerificationTestEnvironment.create(), created, dataSource)) {
            assertThatCode(() -> new ReadOnlyVerificationInitializer().initialize(context)).doesNotThrowAnyException();
            assertThat(created).hasValue(0);
            verifyNoInteractions(dataSource);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "prod", "worker", "local", "dev-adb" })
    void incompatibleProfilesFailBeforeDatasourceConstruction(String profile) {
        MockEnvironment environment = ReadOnlyVerificationTestEnvironment.create();
        environment.addActiveProfile(profile);
        assertRejectedBeforeConstruction(environment);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jobrunr.job-scheduler.enabled", "jobrunr.background-job-server.enabled", "jobrunr.dashboard.enabled",
            "spring.flyway.enabled", "baseball.flyway.enabled", "rag.flyway.enabled", "app.flyway.auto-repair",
            "spring.jpa.generate-ddl", "app.dev-data.enabled", "app.dev-data.repair-existing",
            "app.dev-db.hot-path-prewarm.enabled", "app.mail.enabled", "spring.mail.test-connection",
            "app.realtime.outbox.enabled", "app.ai-ingest.enabled", "media.cleanup.enabled", "payment.payout.enabled",
            "app.home.bootstrap.warmup.enabled", "app.prediction.warmup.enabled",
            "app.leaderboard.game-result-scheduler.enabled", "app.cheer.post-sync.scheduler.enabled",
            "app.ai.coach-auto-brief.monitoring.enabled", "app.prediction.ranking-settlement-scheduler.enabled",
            "app.client-error-monitoring.alerts.enabled", "app.worker.enabled",
            "JOBRUNR_BACKGROUND_JOB_SERVER_ENABLED", "APP_MAIL_ENABLED", "AI_INGEST_ENABLED"
    })
    void mutationFlagsAndLegacyEnvironmentAliasesFailBeforeConstruction(String key) {
        assertRejectedBeforeConstruction(ReadOnlyVerificationTestEnvironment.create().withProperty(key, "true"));
    }

    @ParameterizedTest
    @CsvSource({
            "spring.jpa.hibernate.ddl-auto,none",
            "spring.jpa.hibernate.ddl-auto,update",
            "baseball.jpa.hibernate.ddl-auto,none",
            "baseball.jpa.hibernate.ddl-auto,create-drop",
            "spring.jpa.properties[hibernate.hbm2ddl.auto],update",
            "spring.jpa.properties[jakarta.persistence.schema-generation.database.action],none",
            "spring.jpa.properties[jakarta.persistence.schema-generation.database.action],create",
            "spring.jpa.properties[jakarta.persistence.schema-generation.scripts.action],create",
            "spring.sql.init.mode,always",
            "jobrunr.database.skip-create,false",
            "spring.datasource.hikari.read-only,false",
            "baseball.datasource.hikari.read-only,false",
            "spring.datasource.data-source-properties.options,-c default_transaction_read_only=off",
            "spring.datasource.hikari.connection-init-sql,SELECT 1",
            "spring.datasource.hikari.jdbc-url,jdbc:postgresql://remote.invalid:5432/fixture",
            "spring.datasource.data-source-properties.socketFactory,untrusted.SocketFactory",
            "spring.data.redis.url,redis://remote.invalid:6379",
            "spring.data.redis.cluster.nodes[0],remote.invalid:6379",
            "spring.data.redis.host,remote.invalid",
            "spring.mail.host,remote.invalid",
            "ai.service-url,http://remote.invalid:8001",
            "oci.s3.endpoint,http://remote.invalid:9000",
            "toss.payment.confirm-url,https://remote.invalid/confirm",
            "spring.security.oauth2.client.provider.google.issuer-uri,https://remote.invalid",
            "server.address,0.0.0.0",
            "spring.config.import,optional:file:./.env[.properties]"
    })
    void unsafeConfigurationFailsBeforeConstruction(String key, String value) {
        assertRejectedBeforeConstruction(ReadOnlyVerificationTestEnvironment.create().withProperty(key, value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://remote.invalid:5432/fixture",
            "jdbc:postgresql://127.0.0.1.remote.invalid:5432/fixture",
            "jdbc:postgresql://127.0.0.1:5432,remote.invalid:5432/fixture",
            "jdbc:postgresql://127.0.0.1:5432/fixture?socketFactory=untrusted.Factory",
            "jdbc:postgresql://user@127.0.0.1:5432/fixture",
            "jdbc:h2:mem:fixture",
            "jdbc:oracle:thin:@fixture"
    })
    void unsafeOperatorDatabaseUrlsAreRejectedWithoutConnecting(String url) {
        MockEnvironment environment = ReadOnlyVerificationTestEnvironment.create();
        environment.withProperty("READONLY_PRIMARY_DB_URL", url);
        assertRejectedBeforeConstruction(environment);
    }

    @ParameterizedTest
    @ValueSource(strings = { "READONLY_PRIMARY_DB_PASSWORD", "READONLY_BASEBALL_DB_URL", "READONLY_AI_INTERNAL_TOKEN" })
    void missingOperatorInputsCannotFallBackToNormalEnvironment(String input) {
        MockEnvironment environment = ReadOnlyVerificationTestEnvironment.create().withProperty(input, "");
        environment.withProperty("DB_URL", "jdbc:postgresql://remote.invalid:5432/fixture");
        environment.withProperty("AI_INTERNAL_TOKEN", "unrelated-normal-environment-fixture");
        assertRejectedBeforeConstruction(environment);
    }

    @Test
    void importedDotEnvIsRejectedEvenIfConfigImportWasSubsequentlyCleared() {
        MockEnvironment environment = ReadOnlyVerificationTestEnvironment.create();
        environment.getPropertySources().addLast(new MapPropertySource("Config resource 'file [.env]'", Map.of()));
        assertRejectedBeforeConstruction(environment);
    }

    @Test
    void errorsDoNotEchoInvalidPropertyValuesOrRetainTheirCause() {
        MockEnvironment environment = ReadOnlyVerificationTestEnvironment.create()
                .withProperty("READONLY_PRIMARY_DB_URL", "invalid-sensitive-fixture-value");
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.setEnvironment(environment);
            assertThatThrownBy(() -> new ReadOnlyVerificationInitializer().initialize(context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("READONLY_VERIFICATION_CONFIGURATION_REJECTED")
                    .hasMessageNotContaining("invalid-sensitive-fixture-value")
                    .hasNoCause();
        }
    }

    @Test
    void normalProfileDoesNotApplyReadonlyRestrictions() {
        MockEnvironment environment = new MockEnvironment().withProperty("jobrunr.background-job-server.enabled", "true");
        environment.setActiveProfiles("prod");
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.setEnvironment(environment);
            assertThatCode(() -> new ReadOnlyVerificationInitializer().initialize(context)).doesNotThrowAnyException();
        }
    }

    @Test
    void laterInitializerCannotRemoveReadonlyProfileBeforeBeanCreation() {
        AtomicInteger created = new AtomicInteger();
        DataSource dataSource = mock(DataSource.class);
        MockEnvironment environment = ReadOnlyVerificationTestEnvironment.create();
        try (GenericApplicationContext context = context(environment, created, dataSource)) {
            new ReadOnlyVerificationInitializer().initialize(context);
            environment.setActiveProfiles("dev");
            assertThatThrownBy(context::refresh).isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("READONLY_VERIFICATION_CONFIGURATION_REJECTED");
            assertThat(created).hasValue(0);
            verifyNoInteractions(dataSource);
        }
    }

    @Test
    void laterInitializerCannotEnableWorkersBeforeBeanCreation() {
        AtomicInteger created = new AtomicInteger();
        DataSource dataSource = mock(DataSource.class);
        MockEnvironment environment = ReadOnlyVerificationTestEnvironment.create();
        try (GenericApplicationContext context = context(environment, created, dataSource)) {
            new ReadOnlyVerificationInitializer().initialize(context);
            environment.withProperty("jobrunr.background-job-server.enabled", "true");
            assertThatThrownBy(context::refresh).isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("READONLY_VERIFICATION_CONFIGURATION_REJECTED");
            assertThat(created).hasValue(0);
            verifyNoInteractions(dataSource);
        }
    }

    @Test
    void whitespaceCannotMakeShortFallbackMaterialPassTheEarlyGuard() {
        assertRejectedBeforeConstruction(ReadOnlyVerificationTestEnvironment.create()
                .withProperty("READONLY_AI_INTERNAL_TOKEN", "              short-fixture-token               "));
    }

    @Test
    void bootDiscoversGuardAndConfigDataIsAvailableBeforeGuardExecution() {
        assertThat(new SpringApplication(Object.class).getInitializers())
                .anyMatch(ReadOnlyVerificationInitializer.class::isInstance);
        MockEnvironment environment = ReadOnlyVerificationTestEnvironment.create();
        environment.withProperty("spring.config.location", "file:" + ReadOnlyVerificationTestEnvironment.PROFILE_RESOURCE);
        environment.withProperty("spring.config.import", "");
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        assertThat(environment.getPropertySources().stream()
                .map(source -> source.getName()).filter(name -> name.startsWith("Config resource")))
                .isNotEmpty();
        assertRejectedBeforeConstruction(environment.withProperty("jobrunr.job-scheduler.enabled", "true"));
    }

    private static void assertRejectedBeforeConstruction(MockEnvironment environment) {
        AtomicInteger created = new AtomicInteger();
        DataSource dataSource = mock(DataSource.class);
        try (GenericApplicationContext context = context(environment, created, dataSource)) {
            assertThatThrownBy(() -> {
                new ReadOnlyVerificationInitializer().initialize(context);
                context.refresh();
            }).isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("READONLY_VERIFICATION_CONFIGURATION_REJECTED");
            assertThat(created).hasValue(0);
            verifyNoInteractions(dataSource);
        }
    }

    private static GenericApplicationContext context(MockEnvironment environment, AtomicInteger created, DataSource dataSource) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);
        context.registerBean("datasourceProbe", DataSource.class, () -> {
            created.incrementAndGet();
            return dataSource;
        });
        return context;
    }
}
