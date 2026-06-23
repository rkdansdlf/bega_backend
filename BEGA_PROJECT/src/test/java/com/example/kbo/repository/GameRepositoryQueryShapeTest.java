package com.example.kbo.repository;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameRepositoryQueryShapeTest {

    @Test
    @DisplayName("prediction date query does not use per-row series count correlated subquery")
    void canonicalGameDateProjectionAvoidsCorrelatedSeriesCount() {
        String normalizedSql = GameRepository.CANONICAL_GAME_DATE_PROJECTION_QUERY
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertThat(normalizedSql)
                .doesNotContain("from game sg")
                .doesNotContain("select cast(count(*) as integer)")
                .doesNotContain("kbo_seasons");
        assertThat(normalizedSql)
                .contains("null as \"seriesgameno\"");
    }

    @Test
    @DisplayName("prediction range query keeps projection direct and avoids season metadata joins")
    void canonicalRangeProjectionAvoidsSeasonMetadataJoin() {
        String normalizedSql = normalize(GameRepository.CANONICAL_RANGE_PROJECTION_QUERY);

        assertThat(normalizedSql)
                .doesNotContain("with range_rows")
                .doesNotContain("kbo_seasons")
                .doesNotContain("upper(trim(")
                .contains("null as \"rawleaguetypecode\"")
                .contains("null as \"seriesgameno\"");
    }

    @Test
    @DisplayName("completed range query keeps projection direct and avoids season metadata joins")
    void canonicalCompletedRangeProjectionAvoidsSeasonMetadataJoin() {
        String normalizedSql = normalize(GameRepository.CANONICAL_COMPLETED_RANGE_PROJECTION_QUERY);

        assertThat(normalizedSql)
                .doesNotContain("with past_rows")
                .doesNotContain("kbo_seasons")
                .doesNotContain("upper(trim(")
                .contains("null as \"rawleaguetypecode\"")
                .contains("null as \"seriesgameno\"");
    }

    @Test
    @DisplayName("home scheduled window query is a direct projection with status filtering")
    void homeScheduledWindowProjectionAvoidsFullEntityAndFiltersStatuses() {
        String normalizedSql = normalize(GameRepository.HOME_SCHEDULED_WINDOW_PROJECTION_QUERY);

        assertThat(normalizedSql)
                .doesNotContain("select *")
                .doesNotContain("with ")
                .doesNotContain("kbo_seasons")
                .contains("left join game_metadata")
                .contains("upper(g.game_status) in :statuses")
                .contains("g.game_date between :startdate and :enddate");
    }

    private String normalize(String sql) {
        return sql
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
