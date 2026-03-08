package com.example.kbo.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("KboGamePostgresJpaConfig tests")
class KboGamePostgresJpaConfigTest {

    @Test
    @DisplayName("Relaxed schema resolution should prefer public when all game tables exist there")
    void resolveRelaxedSchema_prefersPublicWhenCanonicalTablesExist() {
        KboGamePostgresJpaConfig config = new KboGamePostgresJpaConfig();
        JdbcTemplate jdbcTemplate = mockJdbcTemplate(Map.of(
                "public:game", 1,
                "public:game_metadata", 1,
                "public:game_summary", 1,
                "public:game_inning_scores", 1,
                "security:game", 1,
                "security:game_metadata", 1,
                "security:game_summary", 1,
                "security:game_inning_scores", 1
        ));

        assertThat(config.resolveRelaxedSchema(jdbcTemplate, "security")).isEqualTo("public");
    }

    @Test
    @DisplayName("Relaxed schema resolution should keep active schema when public is incomplete")
    void resolveRelaxedSchema_keepsActiveSchemaWhenPublicIncomplete() {
        KboGamePostgresJpaConfig config = new KboGamePostgresJpaConfig();
        JdbcTemplate jdbcTemplate = mockJdbcTemplate(Map.of(
                "public:game", 1,
                "public:game_metadata", 1,
                "public:game_summary", 1,
                "public:game_inning_scores", 0,
                "security:game", 1,
                "security:game_metadata", 1,
                "security:game_summary", 1,
                "security:game_inning_scores", 1
        ));

        assertThat(config.resolveRelaxedSchema(jdbcTemplate, "security")).isEqualTo("security");
    }

    private JdbcTemplate mockJdbcTemplate(Map<String, Integer> tableCounts) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String schema = invocation.getArgument(2, String.class);
                    String table = invocation.getArgument(3, String.class);
                    return tableCounts.getOrDefault(schema + ":" + table, 0);
                });
        return jdbcTemplate;
    }
}
