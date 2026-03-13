package com.example.kbo.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.common.exception.GlobalExceptionHandler;
import com.example.kbo.service.TeamHistoryService;

@ExtendWith(MockitoExtension.class)
class TeamHistoryControllerTest {

    @Mock
    private TeamHistoryService historyService;

    @InjectMocks
    private TeamHistoryController teamHistoryController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(teamHistoryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("유효하지 않은 시즌은 표준 400 응답을 반환한다")
    void getSeasonTeams_rejectsInvalidSeason() throws Exception {
        mockMvc.perform(get("/api/team-history/season/1970"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_SEASON"))
                .andExpect(jsonPath("$.message").value("season은 1982부터 2030 사이여야 합니다."));
    }

    @Test
    @DisplayName("시즌 데이터가 없으면 표준 404 응답을 반환한다")
    void getSeasonTeams_returnsStandardNotFoundWhenEmpty() throws Exception {
        when(historyService.getHistoryBySeason(2025)).thenReturn(List.of());

        mockMvc.perform(get("/api/team-history/season/2025"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TEAM_HISTORY_SEASON_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("해당 시즌의 팀 역사 데이터를 찾을 수 없습니다."));
    }
}
