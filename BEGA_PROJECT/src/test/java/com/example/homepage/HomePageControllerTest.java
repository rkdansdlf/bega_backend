package com.example.homepage;

import com.example.common.exception.GlobalExceptionHandler;
import com.example.kbo.validation.ManualBaseballDataMissingItem;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
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
    @DisplayName("무경기일 홈 일정 조회는 빈 배열을 정상 응답으로 반환한다")
    void getGamesByDateReturnsEmptyListWhenNoGamesScheduled() throws Exception {
        given(homePageGameService.getGamesByDate(eq(LocalDate.of(2026, 4, 13))))
                .willReturn(List.of());

        mockMvc.perform(get("/api/kbo/schedule")
                        .param("date", "2026-04-13"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
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
    @DisplayName("홈 일정 조회는 수동 야구 데이터 요청 계약을 그대로 노출한다")
    void getGamesByDateReturnsManualBaseballDataRequiredPayload() throws Exception {
        given(homePageGameService.getGamesByDate(eq(LocalDate.of(2026, 4, 5))))
                .willThrow(new ManualBaseballDataRequiredException(
                        new ManualBaseballDataRequest(
                                "home.schedule",
                                List.of(new ManualBaseballDataMissingItem(
                                        "game_date",
                                        "경기 날짜",
                                        "요청한 날짜의 홈 일정 row가 없습니다.",
                                        "YYYY-MM-DD")),
                                "다음 야구 데이터가 필요합니다: 날짜=2026-04-05, 경기 날짜",
                                true
                        )));

        mockMvc.perform(get("/api/kbo/schedule")
                        .param("date", "2026-04-05"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MANUAL_BASEBALL_DATA_REQUIRED"))
                .andExpect(jsonPath("$.message").value("야구 데이터 준비가 필요합니다. 운영자가 데이터를 제공하면 다시 확인할 수 있습니다."))
                .andExpect(jsonPath("$.data.scope").value("home.schedule"))
                .andExpect(jsonPath("$.data.blocking").value(true))
                .andExpect(jsonPath("$.data.missingItems[0].key").value("game_date"))
                .andExpect(jsonPath("$.data.missingItems[0].expected_format").value("YYYY-MM-DD"));
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
    @DisplayName("무경기일 일정 네비게이션은 인접 경기일을 정상 반환한다")
    void getScheduleNavigationReturnsAdjacentDatesWithoutSameDayGames() throws Exception {
        given(homePageGameService.getScheduleNavigation(eq(LocalDate.of(2026, 4, 13))))
                .willReturn(ScheduleNavigationDto.builder()
                        .prevGameDate(LocalDate.of(2026, 4, 12))
                        .nextGameDate(LocalDate.of(2026, 4, 14))
                        .hasPrev(true)
                        .hasNext(true)
                        .build());

        mockMvc.perform(get("/api/kbo/schedule/navigation")
                        .param("date", "2026-04-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prevGameDate[0]").value(2026))
                .andExpect(jsonPath("$.prevGameDate[1]").value(4))
                .andExpect(jsonPath("$.prevGameDate[2]").value(12))
                .andExpect(jsonPath("$.nextGameDate[0]").value(2026))
                .andExpect(jsonPath("$.nextGameDate[1]").value(4))
                .andExpect(jsonPath("$.nextGameDate[2]").value(14))
                .andExpect(jsonPath("$.hasPrev").value(true))
                .andExpect(jsonPath("$.hasNext").value(true));
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
