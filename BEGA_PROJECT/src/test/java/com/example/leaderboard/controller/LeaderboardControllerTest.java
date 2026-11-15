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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

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
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(leaderboardController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
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
    @DisplayName("공개 리더보드는 page와 size 범위를 벗어난 요청을 거부한다")
    void getLeaderboard_rejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/leaderboard").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leaderboard").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leaderboard").param("size", "101"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(leaderboardService, powerupService, achievementService);
    }

    @Test
    @DisplayName("공개 피드 limit과 내 점수 히스토리 페이지 범위를 거부한다")
    void boundedFeeds_rejectInvalidLimitsAndPagination() throws Exception {
        mockMvc.perform(get("/api/leaderboard/hot-streaks").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leaderboard/hot-streaks").param("limit", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leaderboard/recent-scores").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leaderboard/recent-scores").param("limit", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leaderboard/me/history").principal(authenticatedUser("1"))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leaderboard/me/history").principal(authenticatedUser("1"))
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leaderboard/me/history").principal(authenticatedUser("1"))
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leaderboard/achievements/recent").principal(authenticatedUser("1"))
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leaderboard/achievements/recent").principal(authenticatedUser("1"))
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leaderboard/achievements/rare").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leaderboard/achievements/rare").param("limit", "101"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(leaderboardService, powerupService, achievementService);
    }

    @Test
    @DisplayName("리더보드 네 조회 경로는 limit/size 1과 100을 허용한다")
    void boundedFeeds_acceptBoundaryLimits() throws Exception {
        for (String limit : new String[] { "1", "100" }) {
            mockMvc.perform(get("/api/leaderboard/hot-streaks").param("limit", limit))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/leaderboard/recent-scores").param("limit", limit))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/leaderboard/me/history").principal(authenticatedUser("1"))
                            .param("size", limit))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/leaderboard/achievements/recent").principal(authenticatedUser("1"))
                            .param("limit", limit))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("경기 ID가 없거나 공백인 파워업 사용 요청은 서비스에 전달하지 않는다")
    void usePowerup_requiresGameId() throws Exception {
        mockMvc.perform(post("/api/leaderboard/powerups/{type}/use", "MAGIC_BAT")
                        .principal(authenticatedUser("1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/leaderboard/powerups/{type}/use", "MAGIC_BAT")
                        .principal(authenticatedUser("1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(powerupService);
    }

    @Test
    @DisplayName("경기 ID가 있는 파워업 사용 요청은 선택 경기와 함께 서비스에 전달한다")
    void usePowerup_passesSelectedGameId() throws Exception {
        when(powerupService.usePowerup(1L, "MAGIC_BAT", "20260607HHLT0")).thenReturn(2);

        mockMvc.perform(post("/api/leaderboard/powerups/{type}/use", "MAGIC_BAT")
                        .principal(authenticatedUser("1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"20260607HHLT0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.remainingCount").value(2));

        org.mockito.Mockito.verify(powerupService).usePowerup(1L, "MAGIC_BAT", "20260607HHLT0");
    }

    private Principal authenticatedUser(String userId) {
        return () -> userId;
    }
}
