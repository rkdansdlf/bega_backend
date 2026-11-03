package com.example.common.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/** Standalone local-only Flyway entry point for a generated fresh PostgreSQL database. */
public final class PostgresFlywayLocalCli {

    static final String LOCATION = "classpath:db/migration_postgresql";

    private static final Pattern RUN_ID = Pattern.compile("^[a-z0-9][a-z0-9_]{2,40}$");
    private static final Pattern LOOPBACK_JDBC_URL = Pattern.compile(
            "^jdbc:postgresql://(127\\.0\\.0\\.1|localhost|\\[::1\\]):([0-9]{1,5})/"
                    + "(bega_v61_fresh_([a-z0-9][a-z0-9_]{2,40}))$",
            Pattern.CASE_INSENSITIVE);

    private PostgresFlywayLocalCli() {
    }

    static Settings settings(Map<String, String> environment) {
        requireExact(environment, "APP_ENV", "local");
        requireExact(environment, "BEGA_DISABLE_DOTENV", "true");
        requireExact(environment, "BEGA_DB_RECOVERY_MODE", "local");

        String runId = require(environment, "BEGA_DB_RECOVERY_RUN_ID");
        if (!RUN_ID.matcher(runId).matches()) {
            throw new IllegalStateException("BEGA_DB_RECOVERY_RUN_ID is invalid");
        }

        String url = require(environment, "POSTGRES_FLYWAY_URL");
        Matcher urlMatcher = LOOPBACK_JDBC_URL.matcher(url);
        if (!urlMatcher.matches()) {
            if (!url.startsWith("jdbc:postgresql://127.0.0.1:")
                    && !url.startsWith("jdbc:postgresql://localhost:")
                    && !url.startsWith("jdbc:postgresql://[::1]:")) {
                throw new IllegalStateException(
                        "POSTGRES_FLYWAY_URL must target an explicit loopback PostgreSQL endpoint");
            }
            throw new IllegalStateException(
                    "POSTGRES_FLYWAY_URL must target the generated fresh database for the current run");
        }
        int port = Integer.parseInt(urlMatcher.group(2));
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("POSTGRES_FLYWAY_URL port is outside 1-65535");
        }
        assertLoopback(urlMatcher.group(1));

        String databaseName = urlMatcher.group(3);
        String databaseRunId = urlMatcher.group(4);
        if (!runId.equals(databaseRunId)) {
            throw new IllegalStateException(
                    "POSTGRES_FLYWAY_URL generated fresh database must belong to the current run");
        }

        String username = require(environment, "POSTGRES_FLYWAY_USERNAME");
        String password = require(environment, "POSTGRES_FLYWAY_PASSWORD");
        String locations = require(environment, "POSTGRES_FLYWAY_LOCATIONS");
        if (!LOCATION.equals(locations)) {
            throw new IllegalStateException(
                    "POSTGRES_FLYWAY_LOCATIONS must select only migration_postgresql");
        }
        return new Settings(url, username, password, locations, runId, databaseName);
    }

    private static void assertLoopback(String host) {
        String normalized = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        try {
            for (InetAddress address : InetAddress.getAllByName(normalized)) {
                if (!address.isLoopbackAddress()) {
                    throw new IllegalStateException(
                            "POSTGRES_FLYWAY_URL must resolve only to loopback addresses");
                }
            }
        } catch (UnknownHostException exc) {
            throw new IllegalStateException("POSTGRES_FLYWAY_URL loopback host cannot be resolved", exc);
        }
    }

    private static String require(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required");
        }
        return value.trim();
    }

    private static void requireExact(
            Map<String, String> environment,
            String key,
            String expected) {
        String actual = require(environment, key);
        if (!expected.equals(actual.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(key + " must be " + expected);
        }
    }

    static MigrationSummary migrateTwice(MigrationExecutor executor) {
        MigrationOutcome first = executor.migrate();
        MigrationOutcome second = executor.migrate();
        if (second.applied() != 0) {
            throw new IllegalStateException(
                    "second PostgreSQL Flyway execution must apply zero migrations");
        }
        String latestVersion = second.latestVersion() != null
                ? second.latestVersion()
                : first.latestVersion();
        return new MigrationSummary(first.applied(), second.applied(), latestVersion);
    }

    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalStateException(
                    "PostgresFlywayLocalCli does not accept command arguments");
        }
        Settings settings = settings(System.getenv());
        Flyway flyway = Flyway.configure()
                .dataSource(settings.url(), settings.username(), settings.password())
                .locations(settings.locations())
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .outOfOrder(false)
                .cleanDisabled(true)
                .load();

        MigrationSummary summary = migrateTwice(() -> migrationOutcome(flyway.migrate()));
        System.out.println(
                "{\"status\":\"PASS\",\"first_run_applied\":"
                        + summary.firstRunApplied()
                        + ",\"second_run_applied\":"
                        + summary.secondRunApplied()
                        + ",\"latest_version\":\""
                        + safeVersion(summary.latestVersion())
                        + "\"}");
    }

    private static MigrationOutcome migrationOutcome(MigrateResult result) {
        String latest = result.targetSchemaVersion == null
                ? null
                : result.targetSchemaVersion;
        return new MigrationOutcome(result.migrationsExecuted, latest);
    }

    private static String safeVersion(String version) {
        if (version == null) {
            return "none";
        }
        if (!version.matches("[0-9]+(?:[._][0-9]+)*")) {
            throw new IllegalStateException("Flyway latest version has an unexpected format");
        }
        return version;
    }

    @FunctionalInterface
    interface MigrationExecutor {
        MigrationOutcome migrate();
    }

    record MigrationOutcome(int applied, String latestVersion) {
        MigrationOutcome {
            if (applied < 0) {
                throw new IllegalArgumentException("applied migration count cannot be negative");
            }
        }
    }

    record MigrationSummary(int firstRunApplied, int secondRunApplied, String latestVersion) {
    }

    static final class Settings {
        private final String url;
        private final String username;
        private final String password;
        private final String locations;
        private final String runId;
        private final String databaseName;

        private Settings(
                String url,
                String username,
                String password,
                String locations,
                String runId,
                String databaseName) {
            this.url = url;
            this.username = username;
            this.password = password;
            this.locations = locations;
            this.runId = runId;
            this.databaseName = databaseName;
        }

        String url() {
            return url;
        }

        String username() {
            return username;
        }

        String password() {
            return password;
        }

        String locations() {
            return locations;
        }

        String runId() {
            return runId;
        }

        String databaseName() {
            return databaseName;
        }
    }
}
