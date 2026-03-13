package com.example.kbo.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

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
import com.example.kbo.service.TeamFranchiseService;
import com.example.kbo.service.TeamHistoryService;

@ExtendWith(MockitoExtension.class)
class TeamFranchiseControllerTest {

    @Mock
    private TeamFranchiseService franchiseService;

    @Mock
    private TeamHistoryService historyService;

    @InjectMocks
    private TeamFranchiseController teamFranchiseController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(teamFranchiseController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("존재하지 않는 프랜차이즈는 표준 404 응답을 반환한다")
    void getFranchiseById_returnsStandardNotFoundWhenMissing() throws Exception {
        when(franchiseService.getFranchiseById(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/franchises/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FRANCHISE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("프랜차이즈 정보를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("빈 검색어는 표준 400 응답을 반환한다")
    void searchFranchises_rejectsBlankKeyword() throws Exception {
        mockMvc.perform(get("/api/franchises/search")
                        .param("keyword", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FRANCHISE_SEARCH_KEYWORD_REQUIRED"))
                .andExpect(jsonPath("$.message").value("검색어를 입력해주세요."));
    }
}
