package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FlywayHistoricalPostgresMigrationTest {

    @Test
    @DisplayName("historical PostgreSQL V164 migration remains available for existing databases")
    void directSeatViewUploadMigrationRemainsAvailable() throws IOException {
        String sql = loadSql("db/migration_postgresql/V164__support_direct_seat_view_uploads.sql");

        assertThat(sql)
                .contains("ALTER COLUMN diary_id DROP NOT NULL")
                .contains("ADD COLUMN IF NOT EXISTS stadium")
                .contains("idx_seat_view_photo_direct");
    }

    @Test
    @DisplayName("historical PostgreSQL V165 migration remains available for existing databases")
    void directSeatViewSourceTypeMigrationRemainsAvailable() throws IOException {
        String sql = loadSql("db/migration_postgresql/V165__allow_direct_seat_view_source_type.sql");

        assertThat(sql)
                .contains("DROP CONSTRAINT")
                .contains("seat_view_photo_source_type_check")
                .contains("SEATMAP_UPLOAD");
    }

    private String loadSql(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
