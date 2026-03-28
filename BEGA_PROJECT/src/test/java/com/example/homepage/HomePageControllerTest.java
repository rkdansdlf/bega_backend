package com.example.homepage;

import com.example.common.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HomePageControllerTest {

    private MockMvc mockMvc;
    private HomePageGameService homePageGameService;
    private HomePageFacadeService homePageFacadeService;

    @BeforeEach
    void setUp() {
        homePageGameService = mock(HomePageGameService.class);
        homePageFacadeService = mock(HomePageFacadeService.class);
        HomePageController controller = new HomePageController(homePageGameService, homePageFacadeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("홈 일정 조회 실패 시 빈 배열 fallback을 반환한다")
    void getGamesByDateReturnsEmptyListFallback() throws Exception {
        given(homePageGameService.getGamesByDate(eq(LocalDate.of(2026, 3, 13))))
                .willThrow(new RuntimeException("db unavailable"));

        mockMvc.perform(get("/api/kbo/schedule")
                        .param("date", "2026-03-13"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("리그 시작일 조회 실패 시 오늘 날짜 fallback을 반환한다")
    void getLeagueStartDatesReturnsTodayFallback() throws Exception {
        String today = LocalDate.now().toString();
        given(homePageGameService.getLeagueStartDates())
                .willThrow(new RuntimeException("db unavailable"));

        mockMvc.perform(get("/api/kbo/league-start-dates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regularSeasonStart").value(today))
                .andExpect(jsonPath("$.postseasonStart").value(today))
                .andExpect(jsonPath("$.koreanSeriesStart").value(today));
    }

    @Test
    @DisplayName("일정 네비게이션 조회 실패 시 빈 네비게이션 fallback을 반환한다")
    void getScheduleNavigationReturnsEmptyFallback() throws Exception {
        given(homePageGameService.getScheduleNavigation(eq(LocalDate.of(2026, 3, 13))))
                .willThrow(new RuntimeException("db unavailable"));

        mockMvc.perform(get("/api/kbo/schedule/navigation")
                        .param("date", "2026-03-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prevGameDate").doesNotExist())
                .andExpect(jsonPath("$.nextGameDate").doesNotExist())
                .andExpect(jsonPath("$.hasPrev").value(false))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("잘못된 날짜 파라미터는 validation 에러로 표준화된다")
    void invalidDateReturnsValidationError() throws Exception {
        mockMvc.perform(get("/api/kbo/schedule")
                        .param("date", "bad-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.date").value("date 값 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("팀 순위 조회 실패 시 빈 배열 fallback을 반환한다")
    void getTeamRankingsReturnsEmptyListFallback() throws Exception {
        given(homePageGameService.getTeamRankings(eq(2026)))
                .willThrow(new RuntimeException("db unavailable"));

        mockMvc.perform(get("/api/kbo/rankings/2026"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("랭킹 스냅샷 조회는 명시 시즌 응답을 반환한다")
    void getRankingSnapshotReturnsExplicitSeasonPayload() throws Exception {
        LocalDate selectedDate = LocalDate.of(2026, 3, 13);
        given(homePageFacadeService.getRankingSnapshot(eq(selectedDate), eq(2025)))
                .willReturn(HomeRankingSnapshotDto.builder()
                        .rankingSeasonYear(2025)
                        .rankingSourceMessage("2025 시즌 순위 데이터")
                        .isOffSeason(false)
                        .rankings(List.of(HomePageTeamRankingDto.builder()
                                .rank(1)
                                .teamId("KIA")
                                .teamName("KIA 타이거즈")
                                .wins(87)
                                .losses(55)
                                .draws(2)
                                .winRate(".613")
                                .games(144)
                                .build()))
                        .build());

        mockMvc.perform(get("/api/kbo/rankings/snapshot")
                        .param("date", "2026-03-13")
                        .param("seasonYear", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rankingSeasonYear").value(2025))
                .andExpect(jsonPath("$.rankingSourceMessage").value("2025 시즌 순위 데이터"))
                .andExpect(jsonPath("$.isOffSeason").value(false))
                .andExpect(jsonPath("$.rankings[0].teamId").value("KIA"));
    }

    @Test
    @DisplayName("랭킹 스냅샷 조회 실패 시 자동 시즌 fallback을 반환한다")
    void getRankingSnapshotReturnsFallbackPayload() throws Exception {
        LocalDate selectedDate = LocalDate.of(2026, 3, 13);
        given(homePageFacadeService.getRankingSnapshot(eq(selectedDate), isNull()))
                .willThrow(new RuntimeException("db unavailable"));

        mockMvc.perform(get("/api/kbo/rankings/snapshot")
                        .param("date", "2026-03-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rankingSeasonYear").value(2025))
                .andExpect(jsonPath("$.rankingSourceMessage").value("순위 데이터를 불러오지 못했습니다."))
                .andExpect(jsonPath("$.isOffSeason").value(true))
                .andExpect(jsonPath("$.rankings").isArray());
    }
}
