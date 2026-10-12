package com.example.mate;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PartyApplicationTicketImageUrlMigrationSqlTest {

    @Test
    void oracleMigrationAddsClobWithoutChangingOrDroppingTheLegacyColumn() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V177__add_party_application_ticket_image_url_extended.sql"))
                .toUpperCase();

        assertThat(sql).contains("ALTER TABLE PARTY_APPLICATIONS", "TICKET_IMAGE_URL_EXTENDED CLOB");
        assertThat(sql).doesNotContain("DROP COLUMN", "MODIFY TICKET_IMAGE_URL ");
    }

    @Test
    void postgresMigrationAddsTextWithoutChangingOrDroppingTheLegacyColumn() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration_postgresql/V185__add_party_application_ticket_image_url_extended.sql"))
                .toUpperCase();

        assertThat(sql).contains("ALTER TABLE PARTY_APPLICATIONS", "ADD COLUMN IF NOT EXISTS TICKET_IMAGE_URL_EXTENDED TEXT");
        assertThat(sql).doesNotContain("DROP COLUMN", "ALTER COLUMN TICKET_IMAGE_URL ");
    }
}
