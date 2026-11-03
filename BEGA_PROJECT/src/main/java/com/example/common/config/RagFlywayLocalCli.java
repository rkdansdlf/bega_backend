package com.example.common.config;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Local-only command line boundary for the dedicated Oracle RAG Flyway set. */
public final class RagFlywayLocalCli {

    private static final Pattern LOOPBACK_JDBC_URL = Pattern.compile(
            "^jdbc:oracle:thin:@//(?:127\\.0\\.0\\.1|localhost|\\[::1\\]):[0-9]{1,5}/"
                    + "(?:LOCAL|TEST|FREEPDB1)[A-Z0-9_$#.-]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCAL_SCHEMA = Pattern.compile(
            "^(?:LOCAL|TEST)[A-Z0-9_$#]*$",
            Pattern.CASE_INSENSITIVE);

    private RagFlywayLocalCli() {
    }

    static Settings settings(Map<String, String> environment) {
        requireExact(environment, "APP_ENV", "local");
        requireExact(environment, "BEGA_DISABLE_DOTENV", "true");
        requireExact(environment, "RAG_FLYWAY_ENABLED", "true");

        String url = require(environment, "RAG_FLYWAY_URL");
        if (!LOOPBACK_JDBC_URL.matcher(url).matches()) {
            throw new IllegalStateException(
                    "RAG_FLYWAY_URL must target an explicitly local loopback Oracle service");
        }
        String username = require(environment, "RAG_FLYWAY_USERNAME");
        if (!LOCAL_SCHEMA.matcher(username).matches()) {
            throw new IllegalStateException(
                    "RAG_FLYWAY_USERNAME must use a local/test schema prefix");
        }
        String password = require(environment, "RAG_FLYWAY_PASSWORD");
        String locations = require(environment, "RAG_FLYWAY_LOCATIONS");
        if (!RagFlywayMigrationConfig.DEFAULT_LOCATION.equals(locations)) {
            throw new IllegalStateException(
                    "RAG_FLYWAY_LOCATIONS must select only the authoritative Oracle RAG migration");
        }
        String historyTable = require(environment, "RAG_FLYWAY_HISTORY_TABLE");
        if (!RagFlywayMigrationConfig.HISTORY_TABLE.equals(historyTable)) {
            throw new IllegalStateException(
                    "RAG_FLYWAY_HISTORY_TABLE must select the dedicated RAG history table");
        }
        return new Settings(url, username, password, locations, historyTable);
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

    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalStateException("RagFlywayLocalCli does not accept command arguments");
        }
        Settings settings = settings(System.getenv());
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("oracle.jdbc.OracleDriver");
        dataSource.setUrl(settings.url());
        dataSource.setUsername(settings.username());
        dataSource.setPassword(settings.password());

        new RagFlywayMigrationConfig().ragFlyway(
                dataSource,
                true,
                settings.locations(),
                settings.historyTable());
        System.out.println(
                "{\"status\":\"PASS\",\"history_table\":\""
                        + RagFlywayMigrationConfig.HISTORY_TABLE
                        + "\"}");
    }

    static final class Settings {
        private final String url;
        private final String username;
        private final String password;
        private final String locations;
        private final String historyTable;

        private Settings(
                String url,
                String username,
                String password,
                String locations,
                String historyTable) {
            this.url = url;
            this.username = username;
            this.password = password;
            this.locations = locations;
            this.historyTable = historyTable;
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

        String historyTable() {
            return historyTable;
        }
    }
}
