package com.example.homepage;

import com.example.cheerboard.service.CheerService;
import com.example.kbo.validation.ManualBaseballDataMissingItem;
import com.example.kbo.validation.ManualBaseballDataOverrideService;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
import com.example.homepage.port.FeaturedMateQuery;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomePageFacadeServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private HomePageGameService homePageGameService;

    @Mock
    private CheerService cheerService;

    @Mock
    private FeaturedMateQuery featuredMateQuery;

    private HomePageFacadeService homePageFacadeService;

    @BeforeEach
    void setUp() {
        homePageFacadeService = new HomePageFacadeService(
                homePageGameService,
                cheerService,
                featuredMateQuery,
                Duration.ofSeconds(6),
                FIXED_CLOCK);
    }

    @Test
    @DisplayName("bootstrap cache key는 선택 날짜와 서버 기준 오늘을 함께 포함한다")
    void buildBootstrapCacheKeyIncludesSelectedDateAndToday() {
        String cacheKey = homePageFacadeService.buildBootstrapCacheKey(LocalDate.of(2026, 3, 15));

        assertThat(cacheKey).isEqualTo("2026-03-15:today:2026-03-01");
    }

    @Test
    @DisplayName("bootstrap은 운영자가 수동 데이터 필요 날짜로 지정하면 DB 섹션 작업 없이 fallback을 반환한다")
    void getBootstrapShouldFailFastForConfiguredManualDataDate() {
        LocalDate selectedDate = LocalDate.of(2026, 6, 18);
        HomePageFacadeService service = new HomePageFacadeService(
                homePageGameService,
                cheerService,
                featuredMateQuery,
                null,
                null,
                Duration.ofSeconds(6),
                Duration.ofSeconds(6),
                FIXED_CLOCK,
                null,
                null,
                new ManualBaseballDataOverrideService(Set.of(selectedDate)));

        HomeBootstrapResponseDto response = service.getBootstrap(selectedDate);

        assertThat(response.getSelectedDate()).isEqualTo("2026-06-18");
        assertThat(response.getLeagueStartDates().getRegularSeasonStart()).isEqualTo("2026-06-18");
        assertThat(response.getGames()).isEmpty();
        assertThat(response.getScheduledGamesWindow()).isEmpty();
        assertThat(response.getLoadState().getIsFallback()).isTrue();
        assertThat(response.getLoadState().getTimedOut()).isFalse();
        assertThat(response.getLoadState().getTimedOutSections()).isEmpty();
        assertThat(response.getLoadState().getFailedSections())
                .containsExactly("navigation", "games", "scheduledGamesWindow");
        assertThat(response.getLoadState().getFailureReason()).isEqualTo("manual-data-required");
        assertThat(response.getLoadState().getManualDataRequest()).isNotNull();
        assertThat(response.getLoadState().getManualDataRequest().scope()).isEqualTo("home.schedule");
        verify(homePageGameService, never()).getLeagueStartDates();
        verify(homePageGameService, never()).getScheduleNavigation(any(LocalDate.class));
        verify(homePageGameService, never()).getGamesByDateAllowingPartialManualData(any(LocalDate.class));
        verify(homePageGameService, never()).getScheduledGamesWindow(any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("bootstrap은 핵심 일정 데이터를 집계해 응답한다")
    void getBootstrapUsesPreviousSeasonRankingsDuringOffSeason() {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        LeagueStartDatesDto leagueStartDates = LeagueStartDatesDto.builder()
                .regularSeasonStart("2026-03-22")
                .postseasonStart("2026-10-06")
                .koreanSeriesStart("2026-10-26")
                .build();

        when(homePageGameService.getLeagueStartDates()).thenReturn(leagueStartDates);
        when(homePageGameService.getScheduleNavigation(selectedDate)).thenReturn(ScheduleNavigationDto.builder()
                .hasPrev(true)
                .hasNext(true)
                .build());
        when(homePageGameService.getGamesByDateAllowingPartialManualData(selectedDate))
                .thenReturn(HomePageGamesResult.empty());
        when(homePageGameService.getScheduledGamesWindow(eq(selectedDate), eq(selectedDate.plusDays(7))))
                .thenReturn(List.of());
        HomeBootstrapResponseDto response = homePageFacadeService.getBootstrap(selectedDate);

        assertThat(response.getSelectedDate()).isEqualTo("2026-03-15");
        assertThat(response.getLeagueStartDates().getRegularSeasonStart()).isEqualTo("2026-03-22");
        assertThat(response.getGames()).isEmpty();
        assertThat(response.getScheduledGamesWindow()).isEmpty();
        assertThat(response.getLoadState().getIsFallback()).isFalse();
        assertThat(response.getLoadState().getTimedOut()).isFalse();
        assertThat(response.getLoadState().getTimedOutSections()).isEmpty();
        assertThat(response.getLoadState().getFailedSections()).isEmpty();
    }

    @Test
    @DisplayName("bootstrap은 섹션별 결과 counter를 기록한다")
    void getBootstrapRecordsSectionOutcomeCounters() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HomePageFacadeService instrumentedService = new HomePageFacadeService(
                homePageGameService,
                cheerService,
                featuredMateQuery,
                null,
                null,
                Duration.ofSeconds(6),
                Duration.ofSeconds(6),
                FIXED_CLOCK,
                null,
                meterRegistry);
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        LeagueStartDatesDto leagueStartDates = LeagueStartDatesDto.builder()
                .regularSeasonStart("2026-03-22")
                .postseasonStart("2026-10-06")
                .koreanSeriesStart("2026-10-26")
                .build();

        when(homePageGameService.getLeagueStartDates()).thenReturn(leagueStartDates);
        when(homePageGameService.getScheduleNavigation(selectedDate)).thenReturn(ScheduleNavigationDto.builder()
                .hasPrev(true)
                .hasNext(true)
                .build());
        when(homePageGameService.getGamesByDateAllowingPartialManualData(selectedDate))
                .thenReturn(HomePageGamesResult.empty());
        when(homePageGameService.getScheduledGamesWindow(eq(selectedDate), eq(selectedDate.plusDays(7))))
                .thenReturn(List.of());

        HomeBootstrapResponseDto response = instrumentedService.getBootstrap(selectedDate);

        assertThat(response.getLoadState().getIsFallback()).isFalse();
        assertThat(meterRegistry.get("home_bootstrap_section_events_total")
                .tag("section", "leaguestartdates")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("home_bootstrap_section_events_total")
                .tag("section", "navigation")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("home_bootstrap_section_events_total")
                .tag("section", "games")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("home_bootstrap_section_events_total")
                .tag("section", "scheduledgameswindow")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("home section bulkhead는 active/limit gauge를 등록한다")
    void homeSectionBulkheadRegistersActiveAndLimitGauges() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HomePageFacadeService instrumentedService = new HomePageFacadeService(
                homePageGameService,
                cheerService,
                featuredMateQuery,
                null,
                null,
                Duration.ofSeconds(6),
                Duration.ofSeconds(6),
                FIXED_CLOCK,
                null,
                meterRegistry);
        ReflectionTestUtils.setField(instrumentedService, "sectionMaxConcurrency", 3);

        Integer result = ReflectionTestUtils.invokeMethod(
                instrumentedService,
                "runWithSectionPermit",
                (java.util.function.Supplier<Integer>) () -> {
                    assertThat(meterRegistry.get("home_section_bulkhead_active")
                            .tag("bulkhead", "home_sections")
                            .gauge()
                            .value()).isEqualTo(1.0);
                    assertThat(meterRegistry.get("home_section_bulkhead_limit")
                            .tag("bulkhead", "home_sections")
                            .gauge()
                            .value()).isEqualTo(3.0);
                    return 42;
                });

        assertThat(result).isEqualTo(42);
        assertThat(meterRegistry.get("home_section_bulkhead_active")
                .tag("bulkhead", "home_sections")
                .gauge()
                .value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("bootstrap은 무경기일에도 빈 경기 목록과 예정 경기 윈도를 함께 반환한다")
    void getBootstrapReturnsEmptyGamesForNoGameDay() {
        LocalDate selectedDate = LocalDate.of(2026, 4, 13);
        LeagueStartDatesDto leagueStartDates = LeagueStartDatesDto.builder()
                .regularSeasonStart("2026-03-28")
                .postseasonStart("2026-10-06")
                .koreanSeriesStart("2026-10-26")
                .build();

        when(homePageGameService.getLeagueStartDates()).thenReturn(leagueStartDates);
        when(homePageGameService.getScheduleNavigation(selectedDate)).thenReturn(ScheduleNavigationDto.builder()
                .prevGameDate(LocalDate.of(2026, 4, 12))
                .nextGameDate(LocalDate.of(2026, 4, 14))
                .hasPrev(true)
                .hasNext(true)
                .build());
        when(homePageGameService.getGamesByDateAllowingPartialManualData(selectedDate))
                .thenReturn(HomePageGamesResult.empty());
        when(homePageGameService.getScheduledGamesWindow(eq(selectedDate), eq(selectedDate.plusDays(7))))
                .thenReturn(List.of(HomePageScheduledGameDto.builder()
                        .gameId("20260414LGKT0")
                        .homeTeam("LG")
                        .awayTeam("KT")
                        .leagueType("REGULAR")
                        .sourceDate("2026-04-14")
                        .leagueBadge("정규시즌")
                        .time("18:30")
                        .build()));

        HomeBootstrapResponseDto response = homePageFacadeService.getBootstrap(selectedDate);

        assertThat(response.getSelectedDate()).isEqualTo("2026-04-13");
        assertThat(response.getNavigation().getPrevGameDate()).isEqualTo("2026-04-12");
        assertThat(response.getNavigation().getNextGameDate()).isEqualTo("2026-04-14");
        assertThat(response.getGames()).isEmpty();
        assertThat(response.getScheduledGamesWindow()).hasSize(1);
        assertThat(response.getScheduledGamesWindow().get(0).getSourceDate()).isEqualTo("2026-04-14");
    }

    @Test
    @DisplayName("bootstrap은 수동 야구 데이터가 필요한 섹션을 fallback으로 접고 전체 409를 피한다")
    void getBootstrapTreatsManualBaseballDataRequiredSectionAsFallback() {
        LocalDate selectedDate = LocalDate.of(2026, 6, 17);
        LeagueStartDatesDto leagueStartDates = LeagueStartDatesDto.builder()
                .regularSeasonStart("2026-03-28")
                .postseasonStart("2026-10-06")
                .koreanSeriesStart("2026-10-26")
                .build();
        ManualBaseballDataRequiredException manualDataRequired =
                new ManualBaseballDataRequiredException(new ManualBaseballDataRequest(
                        "home.schedule",
                        List.of(new ManualBaseballDataMissingItem(
                                "final_score",
                                "최종 점수",
                                "과거 경기의 최종 점수가 비어 있습니다.",
                                "home_score, away_score")),
                        "다음 야구 데이터가 필요합니다: 경기 ID=20260617HHNC0",
                        true));

        when(homePageGameService.getLeagueStartDates()).thenReturn(leagueStartDates);
        when(homePageGameService.getScheduleNavigation(selectedDate)).thenReturn(ScheduleNavigationDto.builder()
                .hasPrev(false)
                .hasNext(false)
                .build());
        when(homePageGameService.getGamesByDateAllowingPartialManualData(selectedDate)).thenThrow(manualDataRequired);
        when(homePageGameService.getScheduledGamesWindow(eq(selectedDate), eq(selectedDate.plusDays(7))))
                .thenThrow(manualDataRequired);

        HomeBootstrapResponseDto response = homePageFacadeService.getBootstrap(selectedDate);

        assertThat(response.getSelectedDate()).isEqualTo("2026-06-17");
        assertThat(response.getGames()).isEmpty();
        assertThat(response.getScheduledGamesWindow()).isEmpty();
        assertThat(response.getLoadState().getIsFallback()).isTrue();
        assertThat(response.getLoadState().getTimedOut()).isFalse();
        assertThat(response.getLoadState().getTimedOutSections()).isEmpty();
        assertThat(response.getLoadState().getFailedSections())
                .containsExactly("games", "scheduledGamesWindow");
        assertThat(response.getLoadState().getFailureReason()).isEqualTo("manual-data-required");
        assertThat(response.getLoadState().getManualDataRequest()).isNotNull();
        assertThat(response.getLoadState().getManualDataRequest().scope()).isEqualTo("home.schedule");
    }

    @Test
    @DisplayName("bootstrap은 일부 경기만 수동 데이터가 필요해도 유효한 완료 경기를 보존한다")
    void getBootstrapKeepsValidGamesWhenGamesSectionHasPartialManualData() {
        LocalDate selectedDate = LocalDate.of(2026, 6, 26);
        LeagueStartDatesDto leagueStartDates = LeagueStartDatesDto.builder()
                .regularSeasonStart("2026-03-28")
                .postseasonStart("2026-10-06")
                .koreanSeriesStart("2026-10-26")
                .build();
        ManualBaseballDataRequest manualDataRequest = new ManualBaseballDataRequest(
                "home.schedule",
                List.of(new ManualBaseballDataMissingItem(
                        "final_score",
                        "최종 점수",
                        "과거 경기의 최종 점수가 비어 있습니다.",
                        "home_score, away_score")),
                "다음 야구 데이터가 필요합니다: 경기 ID=20260626HHSK0",
                true);
        HomePageGameDto completedGame = HomePageGameDto.builder()
                .gameId("20260626LGKT0")
                .gameDate("2026-06-26")
                .leagueType("REGULAR")
                .gameStatus("COMPLETED")
                .homeScore(5)
                .awayScore(2)
                .build();

        when(homePageGameService.getLeagueStartDates()).thenReturn(leagueStartDates);
        when(homePageGameService.getScheduleNavigation(selectedDate)).thenReturn(ScheduleNavigationDto.builder()
                .prevGameDate(LocalDate.of(2026, 6, 25))
                .nextGameDate(LocalDate.of(2026, 6, 27))
                .hasPrev(true)
                .hasNext(true)
                .build());
        when(homePageGameService.getGamesByDateAllowingPartialManualData(selectedDate))
                .thenReturn(new HomePageGamesResult(List.of(completedGame), manualDataRequest));
        when(homePageGameService.getScheduledGamesWindow(eq(selectedDate), eq(selectedDate.plusDays(7))))
                .thenReturn(List.of());

        HomeBootstrapResponseDto response = homePageFacadeService.getBootstrap(selectedDate);

        assertThat(response.getGames()).containsExactly(completedGame);
        assertThat(response.getLoadState().getIsFallback()).isFalse();
        assertThat(response.getLoadState().getFailedSections()).isEmpty();
        assertThat(response.getLoadState().getFailureReason()).isEqualTo("manual-data-required");
        assertThat(response.getLoadState().getManualDataRequest()).isSameAs(manualDataRequest);
    }

    @Test
    @DisplayName("bootstrap은 반복 수동 데이터 실패에서 핵심 경기 섹션 DB 로더를 재호출하지 않는다")
    void getBootstrapNegativeCachesManualBaseballDataRequiredSections() {
        LocalDate selectedDate = LocalDate.of(2026, 6, 18);
        LeagueStartDatesDto leagueStartDates = LeagueStartDatesDto.builder()
                .regularSeasonStart("2026-03-28")
                .postseasonStart("2026-10-06")
                .koreanSeriesStart("2026-10-26")
                .build();
        ManualBaseballDataRequiredException manualDataRequired =
                new ManualBaseballDataRequiredException(new ManualBaseballDataRequest(
                        "home.schedule",
                        List.of(new ManualBaseballDataMissingItem(
                                "season_league_context",
                                "시즌/리그 컨텍스트",
                                "경기의 시즌/리그 컨텍스트가 비어 있습니다.",
                                "season_id, league_type")),
                        "다음 야구 데이터가 필요합니다: 경기 ID=20260618HHNC0",
                        true));

        when(homePageGameService.getLeagueStartDates()).thenReturn(leagueStartDates);
        when(homePageGameService.getScheduleNavigation(selectedDate)).thenReturn(ScheduleNavigationDto.builder()
                .hasPrev(false)
                .hasNext(false)
                .build());
        when(homePageGameService.getGamesByDateAllowingPartialManualData(selectedDate)).thenThrow(manualDataRequired);
        when(homePageGameService.getScheduledGamesWindow(eq(selectedDate), eq(selectedDate.plusDays(7))))
                .thenThrow(manualDataRequired);

        HomeBootstrapResponseDto firstResponse = homePageFacadeService.getBootstrap(selectedDate);
        HomeBootstrapResponseDto secondResponse = homePageFacadeService.getBootstrap(selectedDate);

        assertThat(firstResponse.getLoadState().getFailedSections())
                .containsExactly("games", "scheduledGamesWindow");
        assertThat(secondResponse.getLoadState().getFailedSections())
                .containsExactly("games", "scheduledGamesWindow");
        verify(homePageGameService, times(1)).getGamesByDateAllowingPartialManualData(selectedDate);
        verify(homePageGameService, times(1))
                .getScheduledGamesWindow(selectedDate, selectedDate.plusDays(7));
    }

    @Test
    @DisplayName("widgets 응답은 공개 인기글, 메이트 카드, 자동 랭킹 스냅샷을 조합한다")
    void getWidgetsAggregatesPublicHotPostsAndFeaturedMates() {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        when(cheerService.getHotPostsPublic(PageRequest.of(0, 3), "HYBRID"))
                .thenReturn(new PageImpl<>(List.of()));
        when(homePageGameService.getLeagueStartDates()).thenReturn(LeagueStartDatesDto.builder()
                .regularSeasonStart("2026-03-22")
                .postseasonStart("2026-10-06")
                .koreanSeriesStart("2026-10-26")
                .build());
        when(homePageGameService.getTeamRankings(2025)).thenReturn(List.of(
                HomePageTeamRankingDto.builder()
                        .rank(1)
                        .teamId("LG")
                        .teamName("LG 트윈스")
                        .wins(80)
                        .losses(50)
                        .draws(2)
                        .winRate("0.615")
                        .games(132)
                        .gamesBehind(0.0)
                        .build()));
        when(featuredMateQuery.getFeaturedMateCards(selectedDate, 4)).thenReturn(List.of(
                FeaturedMateCardDto.builder()
                        .id(99L)
                        .teamId("LG")
                        .gameDate("2026-03-16")
                        .gameTime("18:30")
                        .stadium("잠실야구장")
                        .section("1루 내야")
                        .homeTeam("LG")
                        .awayTeam("SS")
                        .currentParticipants(1)
                        .maxParticipants(4)
                        .build()));

        HomeWidgetsResponseDto response = homePageFacadeService.getWidgets(selectedDate, null);

        assertThat(response.getHotCheerPosts()).isEmpty();
        assertThat(response.getFeaturedMates()).hasSize(1);
        assertThat(response.getFeaturedMates().get(0).getId()).isEqualTo(99L);
        assertThat(response.getRankingSnapshot().getRankingSeasonYear()).isEqualTo(2025);
        assertThat(response.getRankingSnapshot().getRankingSourceMessage()).isEqualTo("2025 시즌 순위 데이터");
        assertThat(response.getRankingSnapshot().isOffSeason()).isTrue();
        assertThat(response.getRankingSnapshot().getRankings()).hasSize(1);
        assertThat(homePageFacadeService.isUncacheableWidgetsResponse(response)).isFalse();
        verify(featuredMateQuery).getFeaturedMateCards(selectedDate, 4);
    }

    @Test
    @DisplayName("widgets cache service가 있으면 facade는 cache를 통해 조회한다")
    void getWidgetsDelegatesToWidgetsCacheService() {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        HomeWidgetsCacheService widgetsCacheService = mock(HomeWidgetsCacheService.class);
        HomePageFacadeService cachedService = new HomePageFacadeService(
                homePageGameService,
                cheerService,
                featuredMateQuery,
                null,
                null,
                widgetsCacheService,
                Duration.ofSeconds(6),
                Duration.ofSeconds(6),
                FIXED_CLOCK,
                null,
                null,
                ManualBaseballDataOverrideService.disabled());
        HomeWidgetsResponseDto cachedWidgets = HomeWidgetsResponseDto.builder()
                .hotCheerPosts(List.of())
                .featuredMates(List.of())
                .rankingSnapshot(HomeRankingSnapshotDto.builder()
                        .rankingSeasonYear(2025)
                        .rankingSourceMessage("2025 시즌 순위 데이터")
                        .isOffSeason(true)
                        .rankings(List.of())
                        .build())
                .build();
        when(widgetsCacheService.getOrLoad(eq(selectedDate), isNull(), any(), any()))
                .thenReturn(cachedWidgets);

        HomeWidgetsResponseDto response = cachedService.getWidgets(selectedDate, null);

        assertThat(response).isSameAs(cachedWidgets);
        verify(widgetsCacheService).getOrLoad(eq(selectedDate), isNull(), any(), any());
        verify(cheerService, never()).getHotPostsPublic(any(), any());
        verify(featuredMateQuery, never()).getFeaturedMateCards(any(), any(Integer.class));
        verify(homePageGameService, never()).getTeamRankings(any(Integer.class));
    }

    @Test
    @DisplayName("widgets 섹션 bulkhead는 내부 loader 동시 실행 수를 제한한다")
    void getWidgetsLimitsConcurrentSectionLoaders() {
        ReflectionTestUtils.setField(homePageFacadeService, "sectionMaxConcurrency", 1);
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        AtomicInteger activeLoaders = new AtomicInteger();
        AtomicInteger maxActiveLoaders = new AtomicInteger();
        List<HomePageTeamRankingDto> rankings = List.of(HomePageTeamRankingDto.builder()
                .rank(1)
                .teamId("LG")
                .teamName("LG 트윈스")
                .wins(80)
                .losses(50)
                .draws(2)
                .winRate("0.615")
                .games(132)
                .gamesBehind(0.0)
                .build());

        when(cheerService.getHotPostsPublic(PageRequest.of(0, 3), "HYBRID"))
                .thenAnswer(invocation -> trackedSectionResult(new PageImpl<>(List.of()), activeLoaders, maxActiveLoaders));
        when(featuredMateQuery.getFeaturedMateCards(selectedDate, 4))
                .thenAnswer(invocation -> trackedSectionResult(List.of(), activeLoaders, maxActiveLoaders));
        when(homePageGameService.getTeamRankings(2024))
                .thenAnswer(invocation -> trackedSectionResult(rankings, activeLoaders, maxActiveLoaders));

        HomeWidgetsResponseDto response = homePageFacadeService.getWidgets(selectedDate, 2024);

        assertThat(response.getRankingSnapshot().getRankings()).hasSize(1);
        assertThat(maxActiveLoaders).hasValue(1);
    }

    @Test
    @DisplayName("widgets는 seasonYear가 있으면 정확한 시즌 랭킹만 조회한다")
    void getWidgetsUsesExactSeasonRankingSnapshotWhenSeasonYearProvided() {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        when(cheerService.getHotPostsPublic(PageRequest.of(0, 3), "HYBRID"))
                .thenReturn(new PageImpl<>(List.of()));
        when(featuredMateQuery.getFeaturedMateCards(selectedDate, 4)).thenReturn(List.of());
        when(homePageGameService.getTeamRankings(2024)).thenReturn(List.of(
                HomePageTeamRankingDto.builder()
                        .rank(1)
                        .teamId("LG")
                        .teamName("LG 트윈스")
                        .wins(80)
                        .losses(50)
                        .draws(2)
                        .winRate("0.615")
                        .games(132)
                        .gamesBehind(0.0)
                        .build()));

        HomeWidgetsResponseDto response = homePageFacadeService.getWidgets(selectedDate, 2024);

        assertThat(response.getRankingSnapshot().getRankingSeasonYear()).isEqualTo(2024);
        assertThat(response.getRankingSnapshot().getRankingSourceMessage()).isEqualTo("2024 시즌 순위 데이터");
        assertThat(response.getRankingSnapshot().isOffSeason()).isFalse();
        assertThat(response.getRankingSnapshot().getRankings()).hasSize(1);
    }

    @Test
    @DisplayName("랭킹 스냅샷 cache service가 있으면 facade는 cache를 통해 조회한다")
    void getRankingSnapshotDelegatesToRankingSnapshotCacheService() {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        HomeRankingSnapshotCacheService rankingSnapshotCacheService = mock(HomeRankingSnapshotCacheService.class);
        HomePageFacadeService cachedService = new HomePageFacadeService(
                homePageGameService,
                cheerService,
                featuredMateQuery,
                null,
                rankingSnapshotCacheService,
                Duration.ofSeconds(6),
                Duration.ofSeconds(6),
                FIXED_CLOCK,
                null,
                null);
        HomeRankingSnapshotDto cachedSnapshot = HomeRankingSnapshotDto.builder()
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
                .build();
        when(rankingSnapshotCacheService.getOrLoad(eq(selectedDate), eq(2024), any()))
                .thenReturn(cachedSnapshot);

        HomeRankingSnapshotDto response = cachedService.getRankingSnapshot(selectedDate, 2024);

        assertThat(response).isSameAs(cachedSnapshot);
        verify(rankingSnapshotCacheService).getOrLoad(eq(selectedDate), eq(2024), any());
        verify(homePageGameService, never()).getTeamRankings(2024);
    }

    @Test
    @DisplayName("widgets 자동 랭킹 fallback은 비시즌이면 이전 시즌 라벨을 유지한다")
    void getWidgetsKeepsPreviousSeasonLabelWhenAutoRankingFallbackOccursDuringOffSeason() {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        when(cheerService.getHotPostsPublic(PageRequest.of(0, 3), "HYBRID"))
                .thenReturn(new PageImpl<>(List.of()));
        when(featuredMateQuery.getFeaturedMateCards(selectedDate, 4)).thenReturn(List.of());
        when(homePageGameService.getLeagueStartDates()).thenReturn(LeagueStartDatesDto.builder()
                .regularSeasonStart("2026-03-22")
                .postseasonStart("2026-10-06")
                .koreanSeriesStart("2026-10-26")
                .build());
        when(homePageGameService.getTeamRankings(2025)).thenThrow(new IllegalStateException("boom"));

        HomeWidgetsResponseDto response = homePageFacadeService.getWidgets(selectedDate, null);

        assertThat(response.getRankingSnapshot().getRankingSeasonYear()).isEqualTo(2025);
        assertThat(response.getRankingSnapshot().getRankingSourceMessage()).isEqualTo("순위 데이터를 불러오지 못했습니다.");
        assertThat(response.getRankingSnapshot().isOffSeason()).isTrue();
        assertThat(response.getRankingSnapshot().getRankings()).isEmpty();
        assertThat(homePageFacadeService.isUncacheableWidgetsResponse(response)).isTrue();
    }

    @Test
    @DisplayName("widgets는 랭킹 스냅샷이 지연되면 fallback으로 빠르게 응답한다")
    void getWidgetsFallsBackWhenRankingSnapshotTimesOut() {
        HomePageFacadeService timeoutAwareService =
                new HomePageFacadeService(homePageGameService, cheerService, featuredMateQuery, Duration.ofMillis(80), FIXED_CLOCK);
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        when(cheerService.getHotPostsPublic(PageRequest.of(0, 3), "HYBRID"))
                .thenReturn(new PageImpl<>(List.of()));
        when(featuredMateQuery.getFeaturedMateCards(selectedDate, 4)).thenReturn(List.of());
        when(homePageGameService.getLeagueStartDates()).thenReturn(LeagueStartDatesDto.builder()
                .regularSeasonStart("2026-03-22")
                .postseasonStart("2026-10-06")
                .koreanSeriesStart("2026-10-26")
                .build());
        when(homePageGameService.getTeamRankings(2025)).thenAnswer(invocation -> {
            Thread.sleep(200);
            return List.of(HomePageTeamRankingDto.builder()
                    .rank(1)
                    .teamId("LG")
                    .teamName("LG 트윈스")
                    .wins(80)
                    .losses(50)
                    .draws(2)
                    .winRate("0.615")
                    .games(132)
                    .gamesBehind(0.0)
                    .build());
        });

        long startedAt = System.nanoTime();
        HomeWidgetsResponseDto response = timeoutAwareService.getWidgets(selectedDate, null);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertThat(response.getHotCheerPosts()).isEmpty();
        assertThat(response.getFeaturedMates()).isEmpty();
        assertThat(response.getRankingSnapshot().getRankingSeasonYear()).isEqualTo(2025);
        assertThat(response.getRankingSnapshot().getRankingSourceMessage()).isEqualTo("순위 데이터를 불러오지 못했습니다.");
        assertThat(response.getRankingSnapshot().isOffSeason()).isTrue();
        assertThat(response.getRankingSnapshot().getRankings()).isEmpty();
        assertThat(timeoutAwareService.isUncacheableWidgetsResponse(response)).isTrue();
        assertThat(elapsedMs).isLessThan(250L);
    }

    @Test
    @DisplayName("widgets는 메이트 카드가 지연되면 해당 섹션만 fallback으로 빠르게 응답한다")
    void getWidgetsFallsBackWhenFeaturedMatesTimeOut() {
        HomePageFacadeService timeoutAwareService =
                new HomePageFacadeService(homePageGameService, cheerService, featuredMateQuery, Duration.ofMillis(80), FIXED_CLOCK);
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        when(cheerService.getHotPostsPublic(PageRequest.of(0, 3), "HYBRID"))
                .thenReturn(new PageImpl<>(List.of()));
        when(featuredMateQuery.getFeaturedMateCards(selectedDate, 4)).thenAnswer(invocation -> {
            Thread.sleep(200);
            return List.of(FeaturedMateCardDto.builder()
                    .id(99L)
                    .teamId("LG")
                    .gameDate("2026-03-16")
                    .gameTime("18:30")
                    .stadium("잠실야구장")
                    .section("1루 내야")
                    .homeTeam("LG")
                    .awayTeam("SS")
                    .currentParticipants(1)
                    .maxParticipants(4)
                    .build());
        });
        when(homePageGameService.getLeagueStartDates()).thenReturn(LeagueStartDatesDto.builder()
                .regularSeasonStart("2026-03-22")
                .postseasonStart("2026-10-06")
                .koreanSeriesStart("2026-10-26")
                .build());
        when(homePageGameService.getTeamRankings(2025)).thenReturn(List.of());
        when(homePageGameService.getTeamRankings(2024)).thenReturn(List.of());

        long startedAt = System.nanoTime();
        HomeWidgetsResponseDto response = timeoutAwareService.getWidgets(selectedDate, null);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertThat(response.getHotCheerPosts()).isEmpty();
        assertThat(response.getFeaturedMates()).isEmpty();
        assertThat(response.getRankingSnapshot().getRankingSeasonYear()).isEqualTo(2025);
        assertThat(response.getRankingSnapshot().getRankingSourceMessage()).isEqualTo("현재 시즌과 전시즌(전년도) 데이터가 없습니다.");
        assertThat(response.getRankingSnapshot().isOffSeason()).isTrue();
        assertThat(response.getRankingSnapshot().getRankings()).isEmpty();
        assertThat(elapsedMs).isLessThan(250L);
    }

    @Test
    @DisplayName("bootstrap은 특정 섹션이 timeout되어도 기본값으로 응답한다")
    void getBootstrapFallsBackWhenSectionTimesOut() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HomePageFacadeService timeoutAwareService = new HomePageFacadeService(
                homePageGameService,
                cheerService,
                featuredMateQuery,
                null,
                null,
                Duration.ofMillis(20),
                Duration.ofMillis(20),
                FIXED_CLOCK,
                null,
                meterRegistry);
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);

        when(homePageGameService.getLeagueStartDates()).thenAnswer(invocation -> {
            Thread.sleep(80);
            return LeagueStartDatesDto.builder()
                    .regularSeasonStart("2026-03-22")
                    .postseasonStart("2026-10-06")
                    .koreanSeriesStart("2026-10-26")
                    .build();
        });
        when(homePageGameService.getScheduleNavigation(selectedDate)).thenReturn(ScheduleNavigationDto.builder()
                .prevGameDate(LocalDate.of(2026, 3, 14))
                .nextGameDate(LocalDate.of(2026, 3, 16))
                .hasPrev(true)
                .hasNext(true)
                .build());
        when(homePageGameService.getGamesByDateAllowingPartialManualData(selectedDate))
                .thenReturn(HomePageGamesResult.empty());
        when(homePageGameService.getScheduledGamesWindow(eq(selectedDate), eq(selectedDate.plusDays(7))))
                .thenReturn(List.of());

        long startedAt = System.nanoTime();
        HomeBootstrapResponseDto response = timeoutAwareService.getBootstrap(selectedDate);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertThat(response.getLeagueStartDates().getRegularSeasonStart()).isEqualTo("2026-03-15");
        assertThat(response.getNavigation().isHasPrev()).isTrue();
        assertThat(response.getNavigation().isHasNext()).isTrue();
        assertThat(response.getGames()).isEmpty();
        assertThat(response.getScheduledGamesWindow()).isEmpty();
        assertThat(response.getLoadState().getIsFallback()).isTrue();
        assertThat(response.getLoadState().getTimedOut()).isTrue();
        assertThat(response.getLoadState().getTimedOutSections()).containsExactly("leagueStartDates");
        assertThat(response.getLoadState().getFailedSections()).containsExactly("leagueStartDates");
        assertThat(meterRegistry.get("home_bootstrap_section_events_total")
                .tag("section", "leaguestartdates")
                .tag("result", "timeout")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(elapsedMs).isLessThan(250L);
    }

    @Test
    @DisplayName("bootstrap은 timeout된 섹션 task를 interrupt해 DB 작업 누수를 줄인다")
    void getBootstrapInterruptsTimedOutSectionTask() throws Exception {
        HomePageFacadeService timeoutAwareService =
                new HomePageFacadeService(homePageGameService, cheerService, featuredMateQuery, Duration.ofMillis(20), FIXED_CLOCK);
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        CountDownLatch sectionStarted = new CountDownLatch(1);
        CountDownLatch sectionFinished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        when(homePageGameService.getLeagueStartDates()).thenAnswer(invocation -> {
            sectionStarted.countDown();
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException ex) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            } finally {
                sectionFinished.countDown();
            }
            return LeagueStartDatesDto.builder()
                    .regularSeasonStart("2026-03-22")
                    .postseasonStart("2026-10-06")
                    .koreanSeriesStart("2026-10-26")
                    .build();
        });
        when(homePageGameService.getScheduleNavigation(selectedDate)).thenReturn(ScheduleNavigationDto.builder()
                .hasPrev(false)
                .hasNext(false)
                .build());
        when(homePageGameService.getGamesByDateAllowingPartialManualData(selectedDate))
                .thenReturn(HomePageGamesResult.empty());
        when(homePageGameService.getScheduledGamesWindow(eq(selectedDate), eq(selectedDate.plusDays(7))))
                .thenReturn(List.of());

        HomeBootstrapResponseDto response = timeoutAwareService.getBootstrap(selectedDate);

        assertThat(response.getLoadState().getTimedOutSections()).containsExactly("leagueStartDates");
        assertThat(sectionStarted.await(100, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(sectionFinished.await(500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(interrupted).isTrue();
    }

    @Test
    @DisplayName("bootstrap은 이전 대기 섹션이 timeout되어도 이미 완료된 다음 섹션을 timeout 처리하지 않는다")
    void getBootstrapUsesCompletedLaterSectionsAfterEarlierSectionTimeout() {
        HomePageFacadeService timeoutAwareService =
                new HomePageFacadeService(homePageGameService, cheerService, featuredMateQuery, Duration.ofMillis(40), FIXED_CLOCK);
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);

        when(homePageGameService.getLeagueStartDates()).thenReturn(LeagueStartDatesDto.builder()
                .regularSeasonStart("2026-03-22")
                .postseasonStart("2026-10-06")
                .koreanSeriesStart("2026-10-26")
                .build());
        when(homePageGameService.getScheduleNavigation(selectedDate)).thenAnswer(invocation -> {
            Thread.sleep(100);
            return ScheduleNavigationDto.builder()
                    .prevGameDate(LocalDate.of(2026, 3, 14))
                    .nextGameDate(LocalDate.of(2026, 3, 16))
                    .hasPrev(true)
                    .hasNext(true)
                    .build();
        });
        when(homePageGameService.getGamesByDateAllowingPartialManualData(selectedDate))
                .thenReturn(HomePageGamesResult.success(List.of(HomePageGameDto.builder()
                .gameId("20260315LGSS0")
                .gameDate("2026-03-15")
                .leagueType("REGULAR")
                .homeTeam("LG")
                .awayTeam("SS")
                .build())));
        when(homePageGameService.getScheduledGamesWindow(eq(selectedDate), eq(selectedDate.plusDays(7))))
                .thenReturn(List.of(HomePageScheduledGameDto.builder()
                        .gameId("20260316LGSS0")
                        .sourceDate("2026-03-16")
                        .leagueBadge("정규시즌")
                        .time("18:30")
                        .homeTeam("LG")
                        .awayTeam("SS")
                        .build()));

        HomeBootstrapResponseDto response = timeoutAwareService.getBootstrap(selectedDate);

        assertThat(response.getNavigation().isHasPrev()).isFalse();
        assertThat(response.getGames()).hasSize(1);
        assertThat(response.getScheduledGamesWindow()).hasSize(1);
        assertThat(response.getLeagueStartDates().getRegularSeasonStart()).isEqualTo("2026-03-22");
        assertThat(response.getLoadState().getIsFallback()).isTrue();
        assertThat(response.getLoadState().getTimedOut()).isTrue();
        assertThat(response.getLoadState().getTimedOutSections()).containsExactly("navigation");
        assertThat(response.getLoadState().getFailedSections()).containsExactly("navigation");
    }

    @Test
    @DisplayName("bootstrap은 특정 섹션이 실패하면 섹션 fallback metadata를 함께 반환한다")
    void getBootstrapMarksFailedSectionsWhenSectionThrows() {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);

        when(homePageGameService.getLeagueStartDates()).thenReturn(LeagueStartDatesDto.builder()
                .regularSeasonStart("2026-03-22")
                .postseasonStart("2026-10-06")
                .koreanSeriesStart("2026-10-26")
                .build());
        when(homePageGameService.getScheduleNavigation(selectedDate)).thenReturn(ScheduleNavigationDto.builder()
                .prevGameDate(LocalDate.of(2026, 3, 14))
                .nextGameDate(LocalDate.of(2026, 3, 16))
                .hasPrev(true)
                .hasNext(true)
                .build());
        when(homePageGameService.getGamesByDateAllowingPartialManualData(selectedDate))
                .thenThrow(new IllegalStateException("games unavailable"));
        when(homePageGameService.getScheduledGamesWindow(eq(selectedDate), eq(selectedDate.plusDays(7))))
                .thenReturn(List.of());

        HomeBootstrapResponseDto response = homePageFacadeService.getBootstrap(selectedDate);

        assertThat(response.getGames()).isEmpty();
        assertThat(response.getLoadState().getIsFallback()).isTrue();
        assertThat(response.getLoadState().getTimedOut()).isFalse();
        assertThat(response.getLoadState().getTimedOutSections()).isEmpty();
        assertThat(response.getLoadState().getFailedSections()).containsExactly("games");
        assertThat(response.getLoadState().getFailureReason()).isEqualTo("request-failed");
        assertThat(response.getLoadState().getManualDataRequest()).isNull();
    }

    @Test
    @DisplayName("bootstrap은 과거 날짜 조회 시 예정 경기 윈도를 오늘부터 조회한다")
    void getBootstrapUsesTodayForScheduledWindowWhenSelectedDateIsPast() {
        Clock fixedMayClock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        HomePageFacadeService mayService = new HomePageFacadeService(
                homePageGameService,
                cheerService,
                featuredMateQuery,
                Duration.ofSeconds(6),
                fixedMayClock);
        LocalDate selectedDate = LocalDate.of(2026, 5, 13);
        LocalDate today = LocalDate.of(2026, 5, 15);
        LeagueStartDatesDto leagueStartDates = LeagueStartDatesDto.builder()
                .regularSeasonStart("2026-03-21")
                .postseasonStart("2026-10-06")
                .koreanSeriesStart("2026-10-26")
                .build();

        when(homePageGameService.getLeagueStartDates()).thenReturn(leagueStartDates);
        when(homePageGameService.getScheduleNavigation(selectedDate)).thenReturn(ScheduleNavigationDto.builder()
                .prevGameDate(LocalDate.of(2026, 5, 12))
                .nextGameDate(LocalDate.of(2026, 5, 14))
                .hasPrev(true)
                .hasNext(true)
                .build());
        when(homePageGameService.getGamesByDateAllowingPartialManualData(selectedDate))
                .thenReturn(HomePageGamesResult.success(List.of(HomePageGameDto.builder()
                .gameId("20260513LGSK0")
                .gameDate("2026-05-13")
                .leagueType("REGULAR")
                .gameStatus("COMPLETED")
                .homeScore(4)
                .awayScore(2)
                .build())));
        when(homePageGameService.getScheduledGamesWindow(eq(today), eq(today.plusDays(7))))
                .thenReturn(List.of());

        HomeBootstrapResponseDto response = mayService.getBootstrap(selectedDate);

        assertThat(response.getSelectedDate()).isEqualTo("2026-05-13");
        assertThat(response.getGames()).hasSize(1);
        verify(homePageGameService).getScheduledGamesWindow(today, today.plusDays(7));
    }

    private static <T> T trackedSectionResult(
            T value,
            AtomicInteger activeLoaders,
            AtomicInteger maxActiveLoaders) {
        int current = activeLoaders.incrementAndGet();
        maxActiveLoaders.updateAndGet(previous -> Math.max(previous, current));
        try {
            TimeUnit.MILLISECONDS.sleep(40);
            return value;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return value;
        } finally {
            activeLoaders.decrementAndGet();
        }
    }
}
