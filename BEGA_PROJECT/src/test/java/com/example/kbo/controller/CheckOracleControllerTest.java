package com.example.kbo.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckOracleControllerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CheckOracleController controller;

    @Test
    @DisplayName("games-range should not leak raw database errors")
    void getGamesInRange_hidesRawDatabaseError() {
        when(jdbcTemplate.queryForList(anyString(), eq("2026-03-01"), eq("2026-03-07")))
                .thenThrow(new RuntimeException("ORA-00942: table or view does not exist"));

        Map<String, Object> result = controller.getGamesInRange("2026-03-01", "2026-03-07");

        assertThat(result).containsEntry("error", "QUERY_FAILED");
        assertThat(result).doesNotContainValue("ORA-00942: table or view does not exist");
    }

    @Test
    @DisplayName("counts should not leak raw database errors")
    void getCounts_hidesRawDatabaseErrors() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new RuntimeException("ORA-00942: table or view does not exist"));

        Map<String, Object> result = controller.getCounts();

        assertThat(result).containsEntry("game_error", "QUERY_FAILED");
        assertThat(result).doesNotContainValue("ORA-00942: table or view does not exist");
    }
}
