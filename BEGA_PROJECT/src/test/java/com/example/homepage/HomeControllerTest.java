package com.example.homepage;

import com.example.cheerboard.dto.PostSummaryRes;
import com.example.common.exception.GlobalExceptionHandler;
import com.example.kbo.validation.ManualBaseballDataMissingItem;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
import com.example.mate.entity.Party;
import java.time.Instant;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HomeControllerTest {

    private MockMvc mockMvc;
    private HomePageFacadeService homePageFacadeService;
    private HomePageGameService homePageGameService;

    @BeforeEach
    void setUp() {
        homePageFacadeService = mock(HomePageFacadeService.class);
        homePageGameService = mock(HomePageGameService.class);
        HomeController controller = new HomeController(homePageFacadeService, homePageGameService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("홈 bootstrap 응답은 핵심 일정 데이터를 반환한다")
    void getBootstrapReturnsAggregatedPayload() throws Exception {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        given(homePageFacadeService.getBootstrap(eq(selectedDate)))
                .willReturn(HomeBootstrapResponseDto.builder()
                        .selectedDate("2026-03-15")
                        .leagueStartDates(LeagueStartDatesDto.builder()
                                .regularSeasonStart("2026-03-22")
                                .postseasonStart("2026-10-06")
                                .koreanSeriesStart("2026-10-26")
                                .build())
                        .navigation(HomeScheduleNavigationDto.builder()
                                .prevGameDate("2026-03-14")
                                .nextGameDate("2026-03-16")
                                .hasPrev(true)
                                .hasNext(true)
                                .build())
                        .games(List.of(HomePageGameDto.builder()
                                .gameId("20260315LGSS0")
                                .homeTeam("LG")
                                .awayTeam("SS")
                                .leagueType("REGULAR")
                                .time("18:30")
                                .build()))
                        .scheduledGamesWindow(List.of(HomePageScheduledGameDto.builder()
                                .gameId("20260316LGSS0")
                                .homeTeam("LG")
                                .awayTeam("SS")
                                .leagueType("REGULAR")
                                .sourceDate("2026-03-16")
                                .leagueBadge("정규시즌")
                                .time("18:30")
                                .build()))
                        .build());

        mockMvc.perform(get("/api/home/bootstrap").param("date", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedDate").value("2026-03-15"))
                .andExpect(jsonPath("$.navigation.prevGameDate").value("2026-03-14"))
                .andExpect(jsonPath("$.games[0].gameId").value("20260315LGSS0"))
                .andExpect(jsonPath("$.scheduledGamesWindow[0].sourceDate").value("2026-03-16"))
                .andExpect(jsonPath("$.scheduledGamesWindow[0].leagueBadge").value("정규시즌"));
    }

    @Test
    @DisplayName("홈 bootstrap은 수동 야구 데이터 요청 계약을 409로 반환한다")
    void getBootstrapReturnsManualBaseballDataRequiredPayload() throws Exception {
        LocalDate selectedDate = LocalDate.of(2026, 4, 5);
        given(homePageFacadeService.getBootstrap(eq(selectedDate)))
                .willThrow(new ManualBaseballDataRequiredException(
                        new ManualBaseballDataRequest(
                                "home.schedule",
                                List.of(new ManualBaseballDataMissingItem(
                                        "season_league_context",
                                        "시즌/리그 구분",
                                        "시즌 연도 또는 리그 단계가 경기 날짜와 맞지 않아 홈 일정을 구성할 수 없습니다.",
                                        "season_id, season_year, league_type_code")),
                                "다음 야구 데이터가 필요합니다: 날짜=2026-04-05, 시즌/리그 구분",
                                true
                        )));

        mockMvc.perform(get("/api/home/bootstrap").param("date", "2026-04-05"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MANUAL_BASEBALL_DATA_REQUIRED"))
                .andExpect(jsonPath("$.data.scope").value("home.schedule"))
                .andExpect(jsonPath("$.data.blocking").value(true))
                .andExpect(jsonPath("$.data.missingItems[0].key").value("season_league_context"));
    }

    @Test
    @DisplayName("홈 scoped navigation 응답은 /api/home/navigation에서 반환된다")
    void getNavigationReturnsScopedNavigation() throws Exception {
        LocalDate selectedDate = LocalDate.of(2026, 4, 27);
        given(homePageGameService.getScopedNavigation(eq(selectedDate), eq("regular"), isNull()))
                .willReturn(HomeScopedNavigationDto.builder()
                        .resolvedDate("2026-04-28")
                        .prevGameDate("2026-04-26")
                        .nextGameDate("2026-04-28")
                        .hasPrev(true)
                        .hasNext(true)
                        .build());

        mockMvc.perform(get("/api/home/navigation")
                        .param("date", "2026-04-27")
                        .param("scope", "regular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedDate").value("2026-04-28"))
                .andExpect(jsonPath("$.prevGameDate").value("2026-04-26"))
                .andExpect(jsonPath("$.nextGameDate").value("2026-04-28"))
                .andExpect(jsonPath("$.hasPrev").value(true))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @DisplayName("홈 widgets 응답은 인기글, 메이트 카드, 랭킹 스냅샷을 함께 반환한다")
    void getWidgetsReturnsAggregatedWidgets() throws Exception {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        given(homePageFacadeService.getWidgets(eq(selectedDate), eq(2024)))
                .willReturn(HomeWidgetsResponseDto.builder()
                        .hotCheerPosts(List.of(
                                PostSummaryRes.of(
                                        11L,
                                        "LG",
                                        "LG 트윈스",
                                        "LG",
                                        "#c30452",
                                        "오늘 경기 기대됩니다.",
                                        "홍길동",
                                        "@hong",
                                        null,
                                        "LG",
                                        Instant.parse("2026-03-15T03:00:00Z"),
                                        4,
                                        12,
                                        1,
                                        false,
                                        30,
                                        true,
                                        false,
                                        false,
                                        0,
                                        false,
                                        "NORMAL",
                                        List.of())))
                        .featuredMates(List.of(FeaturedMateCardDto.builder()
                                .id(101L)
                                .hostId(1001L)
                                .teamId("LG")
                                .gameDate("2026-03-16")
                                .gameTime("18:30")
                                .stadium("잠실야구장")
                                .section("1루 내야")
                                .description("같이 응원해요")
                                .homeTeam("LG")
                                .awayTeam("SS")
                                .currentParticipants(2)
                                .maxParticipants(4)
                                .ticketPrice(15000)
                                .status(Party.PartyStatus.PENDING)
                                .build()))
                        .rankingSnapshot(HomeRankingSnapshotDto.builder()
                                .rankingSeasonYear(2024)
                                .rankingSourceMessage("2024 시즌 순위 데이터")
                                .isOffSeason(false)
                                .rankings(List.of(HomePageTeamRankingDto.builder()
                                        .rank(1)
                                        .teamId("LG")
                                        .teamName("LG 트윈스")
                                        .wins(80)
                                        .losses(50)
                                        .draws(2)
                                        .winRate("0.615")
                                        .games(132)
                                        .gamesBehind(0.0)
                                        .build()))
                                .build())
                        .build());

        mockMvc.perform(get("/api/home/widgets").param("date", "2026-03-15").param("seasonYear", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotCheerPosts[0].id").value(11))
                .andExpect(jsonPath("$.featuredMates[0].id").value(101))
                .andExpect(jsonPath("$.featuredMates[0].teamId").value("LG"))
                .andExpect(jsonPath("$.featuredMates[0].stadium").value("잠실야구장"))
                .andExpect(jsonPath("$.featuredMates[0].status").value("PENDING"))
                .andExpect(jsonPath("$.rankingSnapshot.rankingSeasonYear").value(2024))
                .andExpect(jsonPath("$.rankingSnapshot.rankingSourceMessage").value("2024 시즌 순위 데이터"))
                .andExpect(jsonPath("$.rankingSnapshot.isOffSeason").value(false))
                .andExpect(jsonPath("$.rankingSnapshot.rankings[0].teamId").value("LG"));
    }

    @Test
    @DisplayName("홈 widgets 실패 fallback 응답도 빈 랭킹 스냅샷을 포함한다")
    void getWidgetsReturnsFallbackRankingSnapshotWhenServiceFails() throws Exception {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        given(homePageFacadeService.getWidgets(eq(selectedDate), eq(2024)))
                .willThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/home/widgets").param("date", "2026-03-15").param("seasonYear", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotCheerPosts").isArray())
                .andExpect(jsonPath("$.featuredMates").isArray())
                .andExpect(jsonPath("$.rankingSnapshot.rankingSeasonYear").value(2024))
                .andExpect(jsonPath("$.rankingSnapshot.rankingSourceMessage").value("순위 데이터를 불러오지 못했습니다."))
                .andExpect(jsonPath("$.rankingSnapshot.isOffSeason").value(false))
                .andExpect(jsonPath("$.rankingSnapshot.rankings").isArray());
    }

    @Test
    @DisplayName("홈 widgets 응답의 ranking snapshot이 누락되어도 계약 형태로 보정한다")
    void getWidgetsNormalizesMissingRankingSnapshot() throws Exception {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        given(homePageFacadeService.getWidgets(eq(selectedDate), isNull()))
                .willReturn(HomeWidgetsResponseDto.builder()
                        .hotCheerPosts(List.of())
                        .featuredMates(List.of())
                        .rankingSnapshot(null)
                        .build());

        mockMvc.perform(get("/api/home/widgets").param("date", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotCheerPosts").isArray())
                .andExpect(jsonPath("$.featuredMates").isArray())
                .andExpect(jsonPath("$.rankingSnapshot.rankingSeasonYear").value(2025))
                .andExpect(jsonPath("$.rankingSnapshot.rankingSourceMessage").value("순위 데이터를 불러오지 못했습니다."))
                .andExpect(jsonPath("$.rankingSnapshot.isOffSeason").value(true))
                .andExpect(jsonPath("$.rankingSnapshot.rankings").isArray());
    }

    @Test
    @DisplayName("홈 widgets 자동 시즌 fallback은 비시즌이면 이전 시즌 라벨을 유지한다")
    void getWidgetsReturnsPreviousSeasonLabelDuringOffSeasonFallback() throws Exception {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        given(homePageFacadeService.getWidgets(eq(selectedDate), isNull()))
                .willThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/home/widgets").param("date", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotCheerPosts").isArray())
                .andExpect(jsonPath("$.featuredMates").isArray())
                .andExpect(jsonPath("$.rankingSnapshot.rankingSeasonYear").value(2025))
                .andExpect(jsonPath("$.rankingSnapshot.rankingSourceMessage").value("순위 데이터를 불러오지 못했습니다."))
                .andExpect(jsonPath("$.rankingSnapshot.isOffSeason").value(true))
                .andExpect(jsonPath("$.rankingSnapshot.rankings").isArray());
    }
}
