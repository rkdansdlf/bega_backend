package com.example.homepage;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class HomeRankingSnapshotDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesOnlyIsOffSeason() throws Exception {
        HomeRankingSnapshotDto dto = HomeRankingSnapshotDto.builder()
                .rankingSeasonYear(2026)
                .rankingSourceMessage("2026 시즌 순위 데이터")
                .isOffSeason(true)
                .rankings(List.of())
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(dto));

        assertThat(json.get("isOffSeason").asBoolean()).isTrue();
        assertThat(json.has("offSeason")).isFalse();
    }

    @Test
    void acceptsLegacyOffSeasonAliasOnInput() throws Exception {
        HomeRankingSnapshotDto dto = objectMapper.readValue("""
                {
                  "rankingSeasonYear": 2026,
                  "rankingSourceMessage": "2026 시즌 순위 데이터",
                  "offSeason": true,
                  "rankings": []
                }
                """, HomeRankingSnapshotDto.class);

        assertThat(dto.isOffSeason()).isTrue();
    }
}
