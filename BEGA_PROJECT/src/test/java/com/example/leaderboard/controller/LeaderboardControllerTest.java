package com.example.leaderboard.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.common.exception.GlobalExceptionHandler;
import com.example.leaderboard.service.AchievementService;
import com.example.leaderboard.service.LeaderboardService;
import com.example.leaderboard.service.PowerupService;

@ExtendWith(MockitoExtension.class)
class LeaderboardControllerTest {

    @Mock
    private LeaderboardService leaderboardService;

    @Mock
    private PowerupService powerupService;

    @Mock
    private AchievementService achievementService;

    @InjectMocks
    private LeaderboardController leaderboardController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(leaderboardController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("인증 없이 내 통계를 조회하면 표준 401 응답을 반환한다")
    void getMyStats_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/leaderboard/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));

        verifyNoInteractions(leaderboardService, powerupService, achievementService);
    }

    @Test
    @DisplayName("파워업 사용 실패는 표준 400 응답을 반환한다")
    void usePowerup_returnsStandardErrorWhenServiceRejects() throws Exception {
        when(powerupService.usePowerup(1L, "MAGIC_BAT", null)).thenReturn(null);

        mockMvc.perform(post("/api/leaderboard/powerups/{type}/use", "MAGIC_BAT")
                        .principal(authenticatedUser("1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("POWERUP_USE_FAILED"))
                .andExpect(jsonPath("$.message").value("파워업 사용에 실패했습니다. 인벤토리를 확인해주세요."));
    }

    private Principal authenticatedUser(String userId) {
        return () -> userId;
    }
}
