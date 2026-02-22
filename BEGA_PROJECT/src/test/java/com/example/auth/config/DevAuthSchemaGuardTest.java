package com.example.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DevAuthSchemaGuardTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DevAuthSchemaGuard guard;

    private void mockPostgresDataSource() throws SQLException {
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.getMetaData()).thenReturn(databaseMetaData);
        lenient().when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        lenient().when(databaseMetaData.getURL()).thenReturn("jdbc:postgresql://localhost:5432/bega_backend");
    }

    private void mockAuthCoreTablesPresent() {
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.FIND_USERS_REGCLASS_SQL, String.class))
                .thenReturn("public.users");
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.FIND_USER_PROVIDERS_REGCLASS_SQL, String.class))
                .thenReturn("public.user_providers");
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.FIND_USER_BLOCK_REGCLASS_SQL, String.class))
                .thenReturn("public.user_block");
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.FIND_USER_FOLLOW_REGCLASS_SQL, String.class))
                .thenReturn("public.user_follow");
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.FIND_PASSWORD_RESET_TOKENS_REGCLASS_SQL, String.class))
                .thenReturn("public.password_reset_tokens");
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.FIND_REFRESH_TOKENS_REGCLASS_SQL, String.class))
                .thenReturn("public.refresh_tokens");
    }

    private void mockAwardsMetadataPass() {
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.CHECK_PUBLIC_AWARDS_TABLE_SQL, Long.class))
                .thenReturn(1L);
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.CHECK_PUBLIC_AWARDS_AWARD_YEAR_COLUMN_SQL, Integer.class))
                .thenReturn(1);
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.CHECK_PUBLIC_AWARDS_POSITION_COLUMN_SQL, Integer.class))
                .thenReturn(1);
    }

    @Test
    @DisplayName("email 컬럼이 존재하면 가드를 통과한다")
    void run_passesWhenEmailColumnExists() throws SQLException {
        mockPostgresDataSource();
        mockAuthCoreTablesPresent();
        mockAwardsMetadataPass();
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.CHECK_USER_PROVIDERS_EMAIL_COLUMN_SQL, Integer.class))
                .thenReturn(1);

        assertDoesNotThrow(() -> guard.run(new DefaultApplicationArguments(new String[0])));
        verify(jdbcTemplate).queryForObject(DevAuthSchemaGuard.CHECK_USER_PROVIDERS_EMAIL_COLUMN_SQL, Integer.class);
    }

    @Test
    @DisplayName("email 컬럼이 없으면 즉시 실패하고 복구 SQL을 안내한다")
    void run_failsWhenEmailColumnMissing() throws SQLException {
        mockPostgresDataSource();
        mockAuthCoreTablesPresent();
        mockAwardsMetadataPass();
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.CHECK_USER_PROVIDERS_EMAIL_COLUMN_SQL, Integer.class))
                .thenReturn(0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.run(new DefaultApplicationArguments(new String[0])));

        assertThat(ex.getMessage()).contains("Missing column: public.user_providers.email");
        assertThat(ex.getMessage()).contains("ALTER TABLE public.user_providers");
    }

    @Test
    @DisplayName("user_providers 테이블이 없으면 DB/스키마 정보를 포함해 즉시 실패한다")
    void run_failsWhenUserProvidersTableMissing() throws SQLException {
        mockPostgresDataSource();
        mockAuthCoreTablesPresent();
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.FIND_USER_PROVIDERS_REGCLASS_SQL, String.class))
                .thenReturn(null);
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.CURRENT_DATABASE_SQL, String.class))
                .thenReturn("bega_backend");
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.CURRENT_SCHEMA_SQL, String.class))
                .thenReturn("public");
        lenient().when(jdbcTemplate.queryForObject(DevAuthSchemaGuard.SEARCH_PATH_SQL, String.class))
                .thenReturn("\"$user\", public");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.run(new DefaultApplicationArguments(new String[0])));

        assertThat(ex.getMessage()).contains("Missing table in current search_path: user_providers");
        assertThat(ex.getMessage()).contains("current_database: bega_backend");
        assertThat(ex.getMessage()).contains("information_schema.tables");
    }

    @Test
    @DisplayName("dev가 PostgreSQL이 아니면 즉시 실패한다")
    void run_failsWhenDevDatasourceIsNotPostgres() throws SQLException {
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.getMetaData()).thenReturn(databaseMetaData);
        lenient().when(databaseMetaData.getDatabaseProductName()).thenReturn("Oracle");
        lenient().when(databaseMetaData.getURL()).thenReturn("jdbc:oracle:thin:@dev_high");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.run(new DefaultApplicationArguments(new String[0])));

        assertThat(ex.getMessage()).contains("requires PostgreSQL");
        assertThat(ex.getMessage()).contains("detected_database: Oracle");
    }
}
