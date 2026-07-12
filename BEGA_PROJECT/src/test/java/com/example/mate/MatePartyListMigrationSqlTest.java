package com.example.mate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class MatePartyListMigrationSqlTest {

    @Test
    @DisplayName("PostgreSQL mate party list migration defines created-at pagination indexes")
    void postgresMatePartyListCreatedIndexesAreDefined() throws IOException {
        String sql = loadSql("db/migration_postgresql/V171__add_mate_party_list_created_indexes.sql")
                .toLowerCase();

        assertThat(sql)
                .contains("idx_parties_created_id")
                .contains("on parties (createdat desc, id desc)")
                .contains("idx_parties_team_created_id")
                .contains("on parties (teamid, createdat desc, id desc)")
                .contains("idx_parties_gamedate_created_id")
                .contains("on parties (gamedate, createdat desc, id desc)");
    }

    @Test
    @DisplayName("PostgreSQL mate party list migration defines participant-count sort indexes")
    void postgresMatePartyListParticipantSortIndexesAreDefined() throws IOException {
        String sql = loadSql("db/migration_postgresql/V172__add_mate_party_list_participant_sort_indexes.sql")
                .toLowerCase();

        assertThat(sql)
                .contains("idx_parties_current_id")
                .contains("on parties (currentparticipants desc, id desc)")
                .contains("idx_parties_team_current_id")
                .contains("on parties (teamid, currentparticipants desc, id desc)")
                .contains("idx_parties_date_current_id")
                .contains("on parties (gamedate, currentparticipants desc, id desc)")
                .contains("idx_parties_status_current_id")
                .contains("on parties (status, currentparticipants desc, id desc)");
    }

    @Test
    @DisplayName("Oracle mate party list migration defines created-at pagination indexes")
    void oracleMatePartyListCreatedIndexesAreDefined() throws IOException {
        String sql = loadSql("db/migration/V165__add_mate_party_list_created_indexes.sql")
                .toLowerCase();

        assertThat(sql)
                .contains("idx_parties_created_id")
                .contains("on parties(createdat desc, id desc)")
                .contains("idx_parties_team_created_id")
                .contains("on parties(teamid, createdat desc, id desc)")
                .contains("idx_parties_gamedate_created_id")
                .contains("on parties(gamedate, createdat desc, id desc)");
    }

    @Test
    @DisplayName("Oracle mate party list migration defines participant-count sort indexes")
    void oracleMatePartyListParticipantSortIndexesAreDefined() throws IOException {
        String sql = loadSql("db/migration/V166__add_mate_party_list_participant_sort_indexes.sql")
                .toLowerCase();

        assertThat(sql)
                .contains("idx_parties_current_id")
                .contains("on parties(currentparticipants desc, id desc)")
                .contains("idx_parties_team_current_id")
                .contains("on parties(teamid, currentparticipants desc, id desc)")
                .contains("idx_parties_date_current_id")
                .contains("on parties(gamedate, currentparticipants desc, id desc)")
                .contains("idx_parties_status_current_id")
                .contains("on parties(status, currentparticipants desc, id desc)");
    }

    private String loadSql(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
