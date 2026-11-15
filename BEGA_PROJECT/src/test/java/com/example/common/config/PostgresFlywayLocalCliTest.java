package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Local PostgreSQL Flyway CLI tests")
class PostgresFlywayLocalCliTest {

    private Map<String, String> validEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("APP_ENV", "local");
        environment.put("BEGA_DISABLE_DOTENV", "true");
        environment.put("BEGA_DB_RECOVERY_MODE", "local");
        environment.put("BEGA_DB_RECOVERY_RUN_ID", "20260830a");
        environment.put(
                "POSTGRES_FLYWAY_URL",
                "jdbc:postgresql://127.0.0.1:55432/bega_v61_fresh_20260830a");
        environment.put("POSTGRES_FLYWAY_USERNAME", "bega_local_admin");
        environment.put("POSTGRES_FLYWAY_PASSWORD", "local-password");
        environment.put("POSTGRES_FLYWAY_LOCATIONS", PostgresFlywayLocalCli.LOCATION);
        return environment;
    }

    @Test
    @DisplayName("accepts only the generated fresh database for the current local run")
    void acceptsExplicitLoopbackGeneratedFreshDatabase() {
        PostgresFlywayLocalCli.Settings settings =
                PostgresFlywayLocalCli.settings(validEnvironment());

        assertThat(settings.databaseName()).isEqualTo("bega_v61_fresh_20260830a");
        assertThat(settings.runId()).isEqualTo("20260830a");
        assertThat(settings.locations()).isEqualTo("classpath:db/migration_postgresql");
    }

    @Test
    @DisplayName("rejects remote PostgreSQL JDBC endpoints")
    void rejectsRemoteJdbcUrl() {
        Map<String, String> environment = validEnvironment();
        environment.put(
                "POSTGRES_FLYWAY_URL",
                "jdbc:postgresql://prod-db.example:5432/bega_v61_fresh_20260830a");

        assertThatThrownBy(() -> PostgresFlywayLocalCli.settings(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    @DisplayName("rejects the source and arbitrary database names")
    void rejectsSourceAndArbitraryDatabaseNames() {
        for (String database : new String[] {"kbo_validate", "postgres", "bega_backend"}) {
            Map<String, String> environment = validEnvironment();
            environment.put(
                    "POSTGRES_FLYWAY_URL",
                    "jdbc:postgresql://127.0.0.1:55432/" + database);

            assertThatThrownBy(() -> PostgresFlywayLocalCli.settings(environment))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("generated fresh");
        }
    }

    @Test
    @DisplayName("rejects a generated database from another run")
    void rejectsRunIdMismatch() {
        Map<String, String> environment = validEnvironment();
        environment.put(
                "POSTGRES_FLYWAY_URL",
                "jdbc:postgresql://127.0.0.1:55432/bega_v61_fresh_20260830b");

        assertThatThrownBy(() -> PostgresFlywayLocalCli.settings(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current run");
    }

    @Test
    @DisplayName("requires dotenv loading to be disabled")
    void rejectsDotenvFallback() {
        Map<String, String> environment = validEnvironment();
        environment.remove("BEGA_DISABLE_DOTENV");

        assertThatThrownBy(() -> PostgresFlywayLocalCli.settings(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BEGA_DISABLE_DOTENV");
    }

    @Test
    @DisplayName("accepts only the authoritative PostgreSQL application migrations")
    void rejectsOtherFlywayLocations() {
        Map<String, String> environment = validEnvironment();
        environment.put("POSTGRES_FLYWAY_LOCATIONS", "classpath:db/migration");

        assertThatThrownBy(() -> PostgresFlywayLocalCli.settings(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("migration_postgresql");
    }

    @Test
    @DisplayName("requires the second migration execution to apply zero migrations")
    void secondMigrationMustBeNoOp() {
        Iterator<PostgresFlywayLocalCli.MigrationOutcome> outcomes = List.of(
                new PostgresFlywayLocalCli.MigrationOutcome(17, "987"),
                new PostgresFlywayLocalCli.MigrationOutcome(0, "987"))
                .iterator();

        PostgresFlywayLocalCli.MigrationSummary summary =
                PostgresFlywayLocalCli.migrateTwice(outcomes::next);

        assertThat(summary.firstRunApplied()).isEqualTo(17);
        assertThat(summary.secondRunApplied()).isZero();
        assertThat(summary.latestVersion()).isEqualTo("987");

        Iterator<PostgresFlywayLocalCli.MigrationOutcome> changedSecondRun = List.of(
                new PostgresFlywayLocalCli.MigrationOutcome(17, "987"),
                new PostgresFlywayLocalCli.MigrationOutcome(1, "988"))
                .iterator();
        assertThatThrownBy(() -> PostgresFlywayLocalCli.migrateTwice(changedSecondRun::next))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("second");
    }

    @Test
    @DisplayName("keeps the first run version when a no-op Flyway run reports no target version")
    void noOpSecondRunMayReportNullTargetVersion() {
        Iterator<PostgresFlywayLocalCli.MigrationOutcome> outcomes = List.of(
                new PostgresFlywayLocalCli.MigrationOutcome(140, "181"),
                new PostgresFlywayLocalCli.MigrationOutcome(0, null))
                .iterator();

        PostgresFlywayLocalCli.MigrationSummary summary =
                PostgresFlywayLocalCli.migrateTwice(outcomes::next);

        assertThat(summary.firstRunApplied()).isEqualTo(140);
        assertThat(summary.secondRunApplied()).isZero();
        assertThat(summary.latestVersion()).isEqualTo("181");
    }
}
