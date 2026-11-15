package com.example.common.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlywayConfigTest {

    @Test
    @DisplayName("auto-repair가 꺼져 있으면 migrate만 수행한다")
    void autoRepairDisabledSkipsRepair() {
        Flyway flyway = mock(Flyway.class);
        FlywayConfig config = new FlywayConfig();
        MigrationInfoService info = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(info);
        when(info.pending()).thenReturn(new MigrationInfo[0]);

        config.flywayMigrationStrategy(false, new MockEnvironment().withProperty("spring.profiles.active", "prod")
                        .withProperty("app.auth.account-deletion.hash-new-tokens", "false"))
                .migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }

    @Test
    @DisplayName("개발 환경에서 auto-repair가 켜져 있으면 repair 후 migrate를 수행한다")
    void autoRepairEnabledOutsideProductionRepairsBeforeMigrate() {
        Flyway flyway = mock(Flyway.class);
        FlywayConfig config = new FlywayConfig();

        config.flywayMigrationStrategy(true, new MockEnvironment().withProperty("spring.profiles.active", "dev"))
                .migrate(flyway);

        verify(flyway).repair();
        verify(flyway).migrate();
    }

    @Test
    @DisplayName("운영 환경은 명시적으로 요청된 auto-repair도 거부한다")
    void productionRejectsAutoRepair() {
        Flyway flyway = mock(Flyway.class);
        FlywayConfig config = new FlywayConfig();

        assertThrows(IllegalStateException.class,
                () -> config.flywayMigrationStrategy(
                                true,
                                new MockEnvironment().withProperty("spring.profiles.active", "prod"))
                        .migrate(flyway));

        verify(flyway, never()).repair();
        verify(flyway, never()).migrate();
    }

    @Test
    void productionBlocksPendingRecoveryTokenMigrationUntilReadersAreReady() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo migration = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(info);
        when(info.pending()).thenReturn(new MigrationInfo[] {migration});
        when(migration.getVersion()).thenReturn(MigrationVersion.fromVersion("193"));

        assertThrows(IllegalStateException.class, () -> new FlywayConfig()
                .flywayMigrationStrategy(false, new MockEnvironment().withProperty("spring.profiles.active", "prod")
                        .withProperty("app.auth.account-deletion.hash-new-tokens", "false"))
                .migrate(flyway));
        verify(flyway, never()).migrate();
    }

    @Test
    void productionBlocksHashIssuanceBeforeReaderReadinessConfirmation() {
        Flyway flyway = mock(Flyway.class);
        assertThrows(IllegalStateException.class, () -> new FlywayConfig()
                .flywayMigrationStrategy(false, new MockEnvironment().withProperty("spring.profiles.active", "prod"))
                .migrate(flyway));
        verify(flyway, never()).migrate();
    }

    @Test
    void productionStartupStillRejectsUnsafeWriterWhenFlywayIsDisabled() {
        var runner = new ApplicationContextRunner().withUserConfiguration(FlywayConfig.class)
                .withInitializer(context -> context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor()))
                .withPropertyValues("spring.profiles.active=prod", "spring.flyway.enabled=false",
                        "app.flyway.account-deletion-hash-readers-ready=false");
        runner.withPropertyValues("app.auth.account-deletion.hash-new-tokens=true")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("Account deletion token hash issuance requires compatible readers "
                                + "and a compatible rollback version before enabling it."));
        runner.withPropertyValues("app.auth.account-deletion.hash-new-tokens=false")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasNotFailed());
    }

    @Test
    void productionPermitsMigrationAfterExplicitReaderReadinessConfirmation() {
        Flyway flyway = mock(Flyway.class);
        var environment = new MockEnvironment().withProperty("spring.profiles.active", "prod")
                .withProperty("app.flyway.account-deletion-hash-readers-ready", "true");

        new FlywayConfig().flywayMigrationStrategy(false, environment).migrate(flyway);

        verify(flyway).migrate();
    }

    @Test
    void targetBeforeV193AllowsFirstStageAndReadinessAllowsSecondStage(@TempDir Path migrations) throws Exception {
        Files.writeString(migrations.resolve("V192__synthetic_baseline.sql"), "create table synthetic_marker(id int);");
        Files.writeString(migrations.resolve("V193__hash_legacy_account_deletion_recovery_tokens.sql"),
                "insert into synthetic_marker(id) values (1);");
        String url = "jdbc:h2:mem:flyway_guard_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        String location = "filesystem:" + migrations.toAbsolutePath();
        var firstStage = new MockEnvironment().withProperty("spring.profiles.active", "prod")
                .withProperty("app.auth.account-deletion.hash-new-tokens", "false");
        Flyway unrestricted = Flyway.configure().dataSource(url, "sa", "").locations(location).load();
        assertThrows(IllegalStateException.class,
                () -> new FlywayConfig().flywayMigrationStrategy(false, firstStage).migrate(unrestricted));

        Flyway capped = Flyway.configure().dataSource(url, "sa", "").locations(location).target("192").load();
        new FlywayConfig().flywayMigrationStrategy(false, firstStage).migrate(capped);
        var secondStage = new MockEnvironment().withProperty("spring.profiles.active", "prod")
                .withProperty("app.flyway.account-deletion-hash-readers-ready", "true");
        new FlywayConfig().flywayMigrationStrategy(false, secondStage).migrate(unrestricted);
        org.junit.jupiter.api.Assertions.assertEquals("193", unrestricted.info().current().getVersion().getVersion());
    }

    @Test
    void productionBlocksRecoveryTokenMigrationEvenIfItsFileWasRenamed() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo migration = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(info);
        when(info.pending()).thenReturn(new MigrationInfo[] {migration});
        when(migration.getVersion()).thenReturn(MigrationVersion.fromVersion("193"));

        assertThrows(IllegalStateException.class, () -> new FlywayConfig()
                .flywayMigrationStrategy(false, new MockEnvironment().withProperty("spring.profiles.active", "prod")
                        .withProperty("app.auth.account-deletion.hash-new-tokens", "false"))
                .migrate(flyway));
        verify(flyway, never()).migrate();
    }

    @Test
    void userRetryColumnIsCreatedAtOrBeforeFirstStageTarget() throws Exception {
        Path dir = Path.of("src/main/resources/db/migration_postgresql");
        List<MigrationVersion> carriers = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.startsWith("V") && name.contains("__")
                        && Files.readString(file).contains("content_anonymization_retry_at")) {
                    carriers.add(MigrationVersion.fromVersion(name.substring(1, name.indexOf("__")).replace('_', '.')));
                }
            }
        }
        assertFalse(carriers.isEmpty(), "no migration creates users.content_anonymization_retry_at");
        MigrationVersion first = Collections.min(carriers);
        assertTrue(first.compareTo(MigrationVersion.fromVersion("192")) <= 0,
                "users.content_anonymization_retry_at first appears in " + first
                        + ", after the target-192 first stage; UserEntity maps it under ddl-auto: validate");
    }
}
