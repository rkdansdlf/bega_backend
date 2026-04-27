package com.example.homepage;

import com.example.cheerboard.service.CheerService;
import com.example.mate.service.PartyService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomePageFacadeServiceTest {

    @Mock
    private HomePageGameService homePageGameService;

    @Mock
    private CheerService cheerService;

    @Mock
    private PartyService partyService;

    private HomePageFacadeService homePageFacadeService;

    @BeforeEach
    void setUp() {
        homePageFacadeService = new HomePageFacadeService(homePageGameService, cheerService, partyService);
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
        when(homePageGameService.getGamesByDate(selectedDate)).thenReturn(List.of());
        when(homePageGameService.getScheduledGamesWindow(eq(selectedDate), eq(selectedDate.plusDays(7))))
                .thenReturn(List.of());
        HomeBootstrapResponseDto response = homePageFacadeService.getBootstrap(selectedDate);

        assertThat(response.getSelectedDate()).isEqualTo("2026-03-15");
        assertThat(response.getLeagueStartDates().getRegularSeasonStart()).isEqualTo("2026-03-22");
        assertThat(response.getGames()).isEmpty();
        assertThat(response.getScheduledGamesWindow()).isEmpty();
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
        when(homePageGameService.getGamesByDate(selectedDate)).thenReturn(List.of());
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
        when(partyService.getFeaturedMateCards(selectedDate, 4)).thenReturn(List.of(
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
        verify(partyService).getFeaturedMateCards(selectedDate, 4);
    }

    @Test
    @DisplayName("widgets는 seasonYear가 있으면 정확한 시즌 랭킹만 조회한다")
    void getWidgetsUsesExactSeasonRankingSnapshotWhenSeasonYearProvided() {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        when(cheerService.getHotPostsPublic(PageRequest.of(0, 3), "HYBRID"))
                .thenReturn(new PageImpl<>(List.of()));
        when(partyService.getFeaturedMateCards(selectedDate, 4)).thenReturn(List.of());
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
    @DisplayName("widgets 자동 랭킹 fallback은 비시즌이면 이전 시즌 라벨을 유지한다")
    void getWidgetsKeepsPreviousSeasonLabelWhenAutoRankingFallbackOccursDuringOffSeason() {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        when(cheerService.getHotPostsPublic(PageRequest.of(0, 3), "HYBRID"))
                .thenReturn(new PageImpl<>(List.of()));
        when(partyService.getFeaturedMateCards(selectedDate, 4)).thenReturn(List.of());
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
    }

    @Test
    @DisplayName("bootstrap은 특정 섹션이 timeout되어도 기본값으로 응답한다")
    void getBootstrapFallsBackWhenSectionTimesOut() throws Exception {
        HomePageFacadeService timeoutAwareService =
                new HomePageFacadeService(homePageGameService, cheerService, partyService, Duration.ofMillis(20));
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
        when(homePageGameService.getGamesByDate(selectedDate)).thenReturn(List.of());
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
        assertThat(elapsedMs).isLessThan(250L);
    }
}
