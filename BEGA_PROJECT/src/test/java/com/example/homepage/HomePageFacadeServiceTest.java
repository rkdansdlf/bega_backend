package com.example.homepage;

import com.example.cheerboard.service.CheerService;
import com.example.mate.service.PartyService;
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
    @DisplayName("비시즌 bootstrap은 이전 시즌 순위를 기준으로 응답한다")
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

        HomeBootstrapResponseDto response = homePageFacadeService.getBootstrap(selectedDate);

        assertThat(response.getSelectedDate()).isEqualTo("2026-03-15");
        assertThat(response.isOffSeason()).isTrue();
        assertThat(response.getRankingSeasonYear()).isEqualTo(2025);
        assertThat(response.getRankingSourceMessage()).isEqualTo("2025 시즌 순위 데이터");
        assertThat(response.getRankings()).hasSize(1);
    }

    @Test
    @DisplayName("widgets 응답은 공개 인기글과 메이트 카드를 조합한다")
    void getWidgetsAggregatesPublicHotPostsAndFeaturedMates() {
        LocalDate selectedDate = LocalDate.of(2026, 3, 15);
        when(cheerService.getHotPostsPublic(PageRequest.of(0, 3), "HYBRID"))
                .thenReturn(new PageImpl<>(List.of()));
        when(partyService.getFeaturedMateCards(LocalDate.now(), 4)).thenReturn(List.of(
                FeaturedMateCardDto.builder()
                        .id(99L)
                        .gameDate("2026-03-16")
                        .gameTime("18:30")
                        .homeTeam("LG")
                        .awayTeam("SS")
                        .currentParticipants(1)
                        .maxParticipants(4)
                        .build()));

        HomeWidgetsResponseDto response = homePageFacadeService.getWidgets(selectedDate);

        assertThat(response.getHotCheerPosts()).isEmpty();
        assertThat(response.getFeaturedMates()).hasSize(1);
        assertThat(response.getFeaturedMates().get(0).getId()).isEqualTo(99L);
    }
}
