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
@Profile("dev & !local & !test")
@RequiredArgsConstructor
public class DevAuthSchemaGuard implements ApplicationRunner {

    static final String EXPECTED_DEV_DB_PRODUCT = "postgresql";
    static final String FIND_USER_PROVIDERS_REGCLASS_SQL = "SELECT to_regclass('user_providers')";
    static final String FIND_USERS_REGCLASS_SQL = "SELECT to_regclass('users')";
    static final String FIND_USER_BLOCK_REGCLASS_SQL = "SELECT to_regclass('user_block')";
    static final String FIND_USER_FOLLOW_REGCLASS_SQL = "SELECT to_regclass('user_follow')";
    static final String FIND_PASSWORD_RESET_TOKENS_REGCLASS_SQL = "SELECT to_regclass('password_reset_tokens')";
    static final String FIND_REFRESH_TOKENS_REGCLASS_SQL = "SELECT to_regclass('refresh_tokens')";
    static final String CHECK_PUBLIC_AWARDS_TABLE_SQL = """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name = 'awards'
            """;
    static final String CHECK_PUBLIC_AWARDS_AWARD_YEAR_COLUMN_SQL = """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'awards'
              AND column_name = 'award_year'
            """;
    static final String CHECK_PUBLIC_AWARDS_POSITION_COLUMN_SQL = """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'awards'
              AND column_name = 'position'
            """;
    static final String FIX_USER_PROVIDERS_EMAIL_SQL = """
            ALTER TABLE %s
            ADD COLUMN IF NOT EXISTS email VARCHAR(255);

            UPDATE %s up
            SET email = u.email
            FROM %s u
            WHERE u.id = up.user_id
              AND (up.email IS NULL OR up.email = '');
            """;
    static final String FIX_AWARD_YEAR_SQL = """
            DO $$
            BEGIN
                IF to_regclass('public.awards') IS NOT NULL THEN
                    IF EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'awards'
                          AND column_name = 'year'
                          AND NOT EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'awards'
                              AND column_name = 'award_year'
                          )
                    ) THEN
                        ALTER TABLE public.awards RENAME COLUMN year TO award_year;
                    END IF;

                    IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'awards'
                          AND column_name = 'award_year'
                    ) THEN
                        ALTER TABLE public.awards ADD COLUMN award_year INTEGER;
                    END IF;

                    UPDATE public.awards
                    SET award_year = COALESCE(award_year, EXTRACT(YEAR FROM CURRENT_DATE)::int)
                    WHERE award_year IS NULL;

                    ALTER TABLE public.awards ALTER COLUMN award_year SET NOT NULL;
                END IF;
            END $$;
            """;
    static final String FIX_AWARDS_POSITION_SQL = """
            DO $$
            BEGIN
                IF to_regclass('public.awards') IS NOT NULL THEN
                    IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'awards'
                          AND column_name = 'position'
                    ) THEN
                        IF EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'awards'
                              AND column_name = 'player_position'
                        ) THEN
                            ALTER TABLE public.awards RENAME COLUMN player_position TO position;
                        ELSE
                            ALTER TABLE public.awards ADD COLUMN IF NOT EXISTS position VARCHAR(50);
                        END IF;
                    END IF;
                END IF;
            END $$;
            """;
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
        validateAuthCoreTables();
        validateUserProvidersEmailColumn();
        validateAwardYearColumn();
        validateAwardsPositionColumn();
    }

    private void validateAuthCoreTables() {
        requireAuthTable("users", FIND_USERS_REGCLASS_SQL);
        requireAuthTable("user_providers", FIND_USER_PROVIDERS_REGCLASS_SQL);
        requireAuthTable("user_block", FIND_USER_BLOCK_REGCLASS_SQL);
        requireAuthTable("user_follow", FIND_USER_FOLLOW_REGCLASS_SQL);
        requireAuthTable("password_reset_tokens", FIND_PASSWORD_RESET_TOKENS_REGCLASS_SQL);
        requireAuthTable("refresh_tokens", FIND_REFRESH_TOKENS_REGCLASS_SQL);
    }

    private void requireAuthTable(String tableName, String regclassQuery) {
        String regclass = jdbcTemplate.queryForObject(regclassQuery, String.class);
        if (regclass == null || regclass.isBlank()) {
            throw missingAuthTableException(tableName);
        }
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
                    """.formatted(userProvidersRegClass, FIX_USER_PROVIDERS_EMAIL_SQL.formatted(userProvidersRegClass, userProvidersRegClass, resolvedUsersTable)));
        }

        log.info("DevAuthSchemaGuard passed: {}.email", userProvidersRegClass);
    }

    void validateAwardYearColumn() {
        Long awardsTable = jdbcTemplate.queryForObject(CHECK_PUBLIC_AWARDS_TABLE_SQL, Long.class);
        if (awardsTable == null || awardsTable == 0) {
            throw missingAwardsTableException();
        }

        Integer awardYearCount = jdbcTemplate.queryForObject(CHECK_PUBLIC_AWARDS_AWARD_YEAR_COLUMN_SQL, Integer.class);
        if (awardYearCount == null || awardYearCount == 0) {
            throw missingAwardYearColumnException();
        }

        log.info("DevAuthSchemaGuard passed: public.awards.award_year");
    }

    void validateAwardsPositionColumn() {
        Long awardsTable = jdbcTemplate.queryForObject(CHECK_PUBLIC_AWARDS_TABLE_SQL, Long.class);
        if (awardsTable == null || awardsTable == 0) {
            throw missingAwardsTableException();
        }

        Integer positionColumnCount = jdbcTemplate.queryForObject(CHECK_PUBLIC_AWARDS_POSITION_COLUMN_SQL, Integer.class);
        if (positionColumnCount == null || positionColumnCount == 0) {
            throw missingAwardsPositionColumnException();
        }

        log.info("DevAuthSchemaGuard passed: public.awards.position");
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

    private IllegalStateException missingAuthTableException(String tableName) {
        String currentDatabase = safeQueryForString(CURRENT_DATABASE_SQL);
        String currentSchema = safeQueryForString(CURRENT_SCHEMA_SQL);
        String searchPath = safeQueryForString(SEARCH_PATH_SQL);

        return new IllegalStateException("""
                [Schema Guard] Missing table in current search_path: %s
                current_database: %s
                current_schema: %s
                search_path: %s

                Confirm the following tables exist in the same DB as spring.datasource:

                SELECT table_schema, table_name
                FROM information_schema.tables
                WHERE table_name IN ('users', 'user_providers', 'user_block', 'user_follow', 'password_reset_tokens', 'refresh_tokens');
                """.formatted(tableName, currentDatabase, currentSchema, searchPath));
    }

    private IllegalStateException missingAwardsTableException() {
        String currentDatabase = safeQueryForString(CURRENT_DATABASE_SQL);
        String currentSchema = safeQueryForString(CURRENT_SCHEMA_SQL);
        String searchPath = safeQueryForString(SEARCH_PATH_SQL);

        return new IllegalStateException("""
                [Schema Guard] Missing table in current search_path: public.awards
                current_database: %s
                current_schema: %s
                search_path: %s

                Confirm the following exist in the same DB as spring.datasource:

                SELECT table_schema, table_name
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'awards';
                """.formatted(currentDatabase, currentSchema, searchPath));
    }

    private IllegalStateException missingAwardYearColumnException() {
        return new IllegalStateException("""
                [Schema Guard] Missing column: public.awards.award_year
                Run this SQL once on dev PostgreSQL:

                %s
                """.formatted(FIX_AWARD_YEAR_SQL));
    }

    private IllegalStateException missingAwardsPositionColumnException() {
        return new IllegalStateException("""
                [Schema Guard] Missing column: public.awards.position
                Run this SQL once on dev PostgreSQL:

                %s
                """.formatted(FIX_AWARDS_POSITION_SQL));
    }

    private String safeQueryForString(String sql) {
        try {
            String value = jdbcTemplate.queryForObject(sql, String.class);
            return value == null ? "unknown" : value;
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }

}
