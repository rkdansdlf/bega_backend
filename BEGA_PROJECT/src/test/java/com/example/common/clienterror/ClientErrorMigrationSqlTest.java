package com.example.common.clienterror;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ClientErrorMigrationSqlTest {

    @Test
    @DisplayName("PostgreSQL migration defines client error monitoring tables and text columns")
    void postgresMigrationDefinesMonitoringSchema() throws IOException {
        String sql = loadSql("db/migration_postgresql/V121__create_client_error_monitoring_tables.sql");

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS client_error_events")
                .contains("CREATE TABLE IF NOT EXISTS client_error_feedback")
                .contains("CREATE TABLE IF NOT EXISTS client_error_alert_notifications")
                .contains("stack_trace TEXT NULL")
                .contains("component_stack TEXT NULL")
                .contains("comment TEXT NOT NULL")
                .contains("feedback_count INTEGER NOT NULL DEFAULT 0")
                .contains("delivery_status VARCHAR(20) NOT NULL");
    }

    @Test
    @DisplayName("Oracle migration defines client error monitoring tables and clob columns")
    void oracleMigrationDefinesMonitoringSchema() throws IOException {
        String sql = loadSql("db/migration/V121__create_client_error_monitoring_tables.sql");

        assertThat(sql)
                .contains("CREATE TABLE client_error_events")
                .contains("CREATE TABLE client_error_feedback")
                .contains("CREATE TABLE client_error_alert_notifications")
                .contains("stack_trace CLOB NULL")
                .contains("component_stack CLOB NULL")
                .contains("\"COMMENT\" CLOB NOT NULL")
                .contains("feedback_count NUMBER(10) DEFAULT 0 NOT NULL")
                .contains("delivery_status VARCHAR2(20 CHAR) NOT NULL");
    }

    @Test
    @DisplayName("PostgreSQL client error feedback rename migration maps comment -> feedback_comment safely")
    void postgresClientErrorFeedbackRenameMigrationIsSafe() throws IOException {
        String sql = loadSql("db/migration_postgresql/V146__rename_client_error_feedback_comment_column.sql").toLowerCase();

        assertThat(sql)
                .contains("rename column comment to feedback_comment")
                .contains("and column_name = 'comment'")
                .contains("and column_name = 'feedback_comment'")
                .contains("to_regclass('public.client_error_feedback') is not null");
    }

    @Test
    @DisplayName("Oracle client error feedback rename migration maps COMMENT -> feedback_comment safely")
    void oracleClientErrorFeedbackRenameMigrationIsSafe() throws IOException {
        String sql = loadSql("db/migration/V142__rename_client_error_feedback_comment_column.sql").toLowerCase();

        assertThat(sql)
                .contains("rename column \"comment\" to feedback_comment")
                .contains("upper(column_name) = 'comment'")
                .contains("upper(column_name) = 'feedback_comment'")
                .contains("v_has_comment > 0 and v_has_feedback_comment = 0");
    }

    @Test
    @DisplayName("PostgreSQL client error admin filter migration defines source/status occurred indexes")
    void postgresClientErrorAdminFilterIndexesAreDefined() throws IOException {
        String sql = loadSql("db/migration_postgresql/V170__add_client_error_admin_filter_indexes.sql").toLowerCase();

        assertThat(sql)
                .contains("idx_client_error_events_source_occurred")
                .contains("on client_error_events (source, occurred_at desc)")
                .contains("idx_client_error_events_status_occurred")
                .contains("on client_error_events (status_group, occurred_at desc)")
                .contains("idx_client_error_events_occurred_id")
                .contains("on client_error_events (occurred_at desc, id desc)");
    }

    @Test
    @DisplayName("Oracle client error admin filter migration defines source/status occurred indexes")
    void oracleClientErrorAdminFilterIndexesAreDefined() throws IOException {
        String sql = loadSql("db/migration/V164__add_client_error_admin_filter_indexes.sql").toLowerCase();

        assertThat(sql)
                .contains("idx_client_error_events_source_occurred")
                .contains("on client_error_events(source, occurred_at desc)")
                .contains("idx_client_error_events_status_occurred")
                .contains("on client_error_events(status_group, occurred_at desc)")
                .contains("idx_client_error_events_occurred_id")
                .contains("on client_error_events(occurred_at desc, id desc)");
    }

    private String loadSql(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
