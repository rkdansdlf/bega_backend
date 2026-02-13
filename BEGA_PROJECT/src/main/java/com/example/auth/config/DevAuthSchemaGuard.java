package com.example.auth.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Dev 환경에서 Auth 관련 핵심 스키마를 시작 시점에 검증한다.
 * 누락 시 런타임 500 대신 기동 단계에서 즉시 실패시켜 원인을 명확히 드러낸다.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevAuthSchemaGuard implements ApplicationRunner {

    static final String EXPECTED_DEV_DB_PRODUCT = "postgresql";
    static final String FIND_USER_PROVIDERS_REGCLASS_SQL = "SELECT to_regclass('user_providers')";
    static final String FIND_USERS_REGCLASS_SQL = "SELECT to_regclass('users')";
    static final String CURRENT_DATABASE_SQL = "SELECT current_database()";
    static final String CURRENT_SCHEMA_SQL = "SELECT current_schema()";
    static final String SEARCH_PATH_SQL = "SHOW search_path";

    static final String CHECK_USER_PROVIDERS_EMAIL_COLUMN_SQL = """
            SELECT COUNT(*)
            FROM pg_attribute
            WHERE attrelid = to_regclass('user_providers')
              AND attname = 'email'
              AND NOT attisdropped
            """;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        validateDevDataSource();
        validateUserProvidersEmailColumn();
    }

    void validateDevDataSource() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = safe(metaData.getDatabaseProductName());
            String jdbcUrl = safe(metaData.getURL());

            if (!productName.toLowerCase().contains(EXPECTED_DEV_DB_PRODUCT)) {
                throw new IllegalStateException("""
                        [Schema Guard] Invalid dev datasource target.
                        Active profile 'dev' requires PostgreSQL primary DB.
                        detected_database: %s
                        detected_jdbc_url: %s

                        Check these env values and restart:
                        - DB_URL
                        - DB_USERNAME
                        - DB_PASSWORD
                        """.formatted(productName, jdbcUrl));
            }

            log.info("DevAuthSchemaGuard datasource target: {} ({})", productName, jdbcUrl);
        } catch (SQLException ex) {
            throw new IllegalStateException("[Schema Guard] Failed to inspect dev datasource metadata", ex);
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    void validateUserProvidersEmailColumn() {
        String userProvidersRegClass = jdbcTemplate.queryForObject(FIND_USER_PROVIDERS_REGCLASS_SQL, String.class);
        if (userProvidersRegClass == null || userProvidersRegClass.isBlank()) {
            throw missingTableException();
        }

        Integer count = jdbcTemplate.queryForObject(CHECK_USER_PROVIDERS_EMAIL_COLUMN_SQL, Integer.class);

        if (count == null || count == 0) {
            String usersRegClass = jdbcTemplate.queryForObject(FIND_USERS_REGCLASS_SQL, String.class);
            String resolvedUsersTable = (usersRegClass == null || usersRegClass.isBlank()) ? "users" : usersRegClass;
            throw new IllegalStateException("""
                    [Schema Guard] Missing column: %s.email
                    Run this SQL once on dev PostgreSQL:

                    %s
                    """.formatted(userProvidersRegClass, buildRemediationSql(userProvidersRegClass, resolvedUsersTable)));
        }

        log.info("DevAuthSchemaGuard passed: {}.email", userProvidersRegClass);
    }

    private IllegalStateException missingTableException() {
        String currentDatabase = safeQueryForString(CURRENT_DATABASE_SQL);
        String currentSchema = safeQueryForString(CURRENT_SCHEMA_SQL);
        String searchPath = safeQueryForString(SEARCH_PATH_SQL);

        return new IllegalStateException("""
                [Schema Guard] Missing table in current search_path: user_providers
                current_database: %s
                current_schema: %s
                search_path: %s

                Confirm you are connected to the same DB as spring.datasource and locate the table:

                SELECT table_schema, table_name
                FROM information_schema.tables
                WHERE table_name IN ('users', 'user_providers');
                """.formatted(currentDatabase, currentSchema, searchPath));
    }

    private String safeQueryForString(String sql) {
        try {
            String value = jdbcTemplate.queryForObject(sql, String.class);
            return value == null ? "unknown" : value;
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }

    static String buildRemediationSql(String userProvidersTable, String usersTable) {
        return """
                ALTER TABLE %s
                ADD COLUMN IF NOT EXISTS email VARCHAR(255);

                UPDATE %s up
                SET email = u.email
                FROM %s u
                WHERE u.id = up.user_id
                  AND (up.email IS NULL OR up.email = '');
                """.formatted(userProvidersTable, userProvidersTable, usersTable);
    }
}
