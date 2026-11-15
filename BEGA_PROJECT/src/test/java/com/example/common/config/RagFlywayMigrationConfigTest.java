package com.example.common.config;

import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RagFlywayMigrationConfig tests")
class RagFlywayMigrationConfigTest {

    private DataSource h2DataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:rag-flyway-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private boolean historyTableExists(DataSource dataSource) {
        Integer count = new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = ?",
                Integer.class,
                RagFlywayMigrationConfig.HISTORY_TABLE.toUpperCase());
        return count != null && count > 0;
    }

    @Test
    @DisplayName("disabled executor does not touch the RAG schema")
    void disabledDoesNotTouchSchema() {
        DataSource dataSource = h2DataSource();

        Flyway flyway = new RagFlywayMigrationConfig().ragFlyway(
                dataSource,
                false,
                "classpath:db/migration_rag_oracle_test/");

        assertThat(flyway).isNotNull();
        assertThat(historyTableExists(dataSource)).isFalse();
    }

    @Test
    @DisplayName("RAG migrations use a dedicated Flyway history table")
    void usesDedicatedHistoryTable() {
        assertThat(RagFlywayMigrationConfig.HISTORY_TABLE)
                .isEqualTo("flyway_rag_schema_history")
                .isNotEqualTo("flyway_schema_history")
                .isNotEqualTo(BaseballFlywayMigrationConfig.HISTORY_TABLE);
        assertThat(RagFlywayMigrationConfig.DEFAULT_LOCATION)
                .isEqualTo("classpath:db/migration_rag_oracle/");
    }

    @Test
    @DisplayName("configured history table cannot drift to a shared Flyway history")
    void rejectsNonCanonicalHistoryTable() {
        DataSource dataSource = h2DataSource();

        assertThatThrownBy(() -> new RagFlywayMigrationConfig().ragFlyway(
                dataSource,
                false,
                "classpath:db/migration_rag_oracle_test/",
                "flyway_schema_history"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dedicated RAG Flyway history table");
    }

    @Test
    @DisplayName("enabled executor applies only the configured RAG location")
    void enabledAppliesConfiguredLocation() {
        DataSource dataSource = h2DataSource();

        new RagFlywayMigrationConfig().ragFlyway(
                dataSource,
                true,
                "classpath:db/migration_rag_oracle_test/");

        assertThat(historyTableExists(dataSource)).isTrue();
        Integer probe = new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE UPPER(table_name) = 'RAG_FLYWAY_WIRING_PROBE'",
                Integer.class);
        assertThat(probe).isEqualTo(1);
    }
}
