package com.example.cheerboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class CheerLinkedPostMigrationSqlTest {

    @Test
    @DisplayName("PostgreSQL migration defines linked cheer post columns, constraints, and indexes")
    void postgresMigrationDefinesLinkedPostSchema() throws IOException {
        String postgresSql = loadSql(
                "db/migration_postgresql/V173__add_cheer_linked_post_types.sql").toLowerCase(Locale.ROOT);

        assertThat(postgresSql)
                .contains("diary_id bigint")
                .contains("party_id bigint")
                .contains("references bega_diary(id) on delete set null")
                .contains("references parties(id) on delete set null")
                .contains("uq_cheer_post_active_diary")
                .contains("where diary_id is not null and deleted = false")
                .contains("uq_cheer_post_active_party")
                .contains("where party_id is not null and deleted = false")
                .contains("ck_cheer_post_link_type");
    }

    @Test
    @DisplayName("Oracle migration defines linked cheer post columns, constraints, and indexes")
    void oracleMigrationDefinesLinkedPostSchema() throws IOException {
        String oracleSql = loadSql(
                "db/migration/V167__add_cheer_linked_post_types.sql").toLowerCase(Locale.ROOT);

        assertThat(oracleSql)
                .contains("diary_id number(19)")
                .contains("party_id number(19)")
                .contains("on delete set null")
                .contains("uq_cheer_post_active_diary")
                .contains("when nvl(deleted, 0) = 0")
                .contains("uq_cheer_post_active_party")
                .contains("ck_cheer_post_link_type");
    }

    private String loadSql(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
