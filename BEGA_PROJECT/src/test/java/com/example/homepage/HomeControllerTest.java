package com.example.homepage;

import com.example.cheerboard.dto.PostSummaryRes;
import com.example.common.exception.GlobalExceptionHandler;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HomeControllerTest {

    private MockMvc mockMvc;
    private HomePageFacadeService homePageFacadeService;

    @BeforeEach
    void setUp() {
        homePageFacadeService = mock(HomePageFacadeService.class);
        HomeController controller = new HomeController(homePageFacadeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("홈 bootstrap 응답은 핵심 일정과 순위를 함께 반환한다")
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
                        .rankingSeasonYear(2025)
                        .rankingSourceMessage("2025 시즌 순위 데이터")
                        .isOffSeason(true)
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
                        .build());

        mockMvc.perform(get("/api/home/bootstrap").param("date", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedDate").value("2026-03-15"))
                .andExpect(jsonPath("$.navigation.prevGameDate").value("2026-03-14"))
                .andExpect(jsonPath("$.games[0].gameId").value("20260315LGSS0"))
                .andExpect(jsonPath("$.scheduledGamesWindow[0].sourceDate").value("2026-03-16"))
                .andExpect(jsonPath("$.scheduledGamesWindow[0].leagueBadge").value("정규시즌"))
                .andExpect(jsonPath("$.rankingSeasonYear").value(2025))
                .andExpect(jsonPath("$.rankingSourceMessage").value("2025 시즌 순위 데이터"))
                .andExpect(jsonPath("$.isOffSeason").value(true))
                .andExpect(jsonPath("$.rankings[0].teamId").value("LG"));
    }

    @Test
    @DisplayName("홈 widgets 응답은 인기글 3개와 메이트 카드 4개 이하를 반환한다")
    void getWidgetsReturnsAggregatedWidgets() throws Exception {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        given(homePageFacadeService.getWidgets(eq(selectedDate)))
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
                                .gameDate("2026-03-16")
                                .gameTime("18:30")
                                .homeTeam("LG")
                                .awayTeam("SS")
                                .currentParticipants(2)
                                .maxParticipants(4)
                                .ticketPrice(15000)
                                .status(Party.PartyStatus.PENDING)
                                .build()))
                        .build());

        mockMvc.perform(get("/api/home/widgets").param("date", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotCheerPosts[0].id").value(11))
                .andExpect(jsonPath("$.featuredMates[0].id").value(101))
                .andExpect(jsonPath("$.featuredMates[0].status").value("PENDING"));
    }
}
