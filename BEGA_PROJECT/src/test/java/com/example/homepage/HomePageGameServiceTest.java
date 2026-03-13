package com.example.homepage;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.service.LeagueStageResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomePageGameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private HomePageTeamRepository homePageTeamRepository;

    @Mock
    private DataSource stadiumDataSource;

    private HomePageGameService homePageGameService;

    @BeforeEach
    void setUp() {
        LeagueStageResolver leagueStageResolver = new LeagueStageResolver(gameRepository);
        homePageGameService = new HomePageGameService(
                gameRepository,
                homePageTeamRepository,
                stadiumDataSource,
                leagueStageResolver
        );
    }

    @Test
    @DisplayName("league_type_code=5 인 경우 KOREAN_SERIES로 매핑된다")
    void getGamesByDate_koreanSeriesFromSeasonType() {
        LocalDate date = LocalDate.of(2025, 10, 28);
        GameEntity game = GameEntity.builder()
                .id(1L)
                .gameId("20251028SSGLG0")
                .gameDate(date)
                .homeTeam("SSG")
                .awayTeam("LG")
                .seasonId(9005)
                .gameStatus("SCHEDULED")
                .stadium("문학")
                .build();

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        when(gameRepository.findByGameDate(date)).thenReturn(List.of(game));
        when(gameRepository.findLeagueTypeCodeBySeasonId(9005)).thenReturn(Optional.of(5));

        List<HomePageGameDto> games = homePageGameService.getGamesByDate(date);

        assertThat(games).hasSize(1);
        assertThat(games.get(0).getLeagueType()).isEqualTo("KOREAN_SERIES");
        assertThat(games.get(0).getGameInfo()).isEqualTo("한국시리즈");
    }

    @Test
    @DisplayName("season_id가 한국시리즈로 잘못 연결돼도 경기 날짜로 포스트시즌 라운드를 교정한다")
    void getGamesByDate_infersPostseasonFromConfiguredStartDates() {
        LocalDate date = LocalDate.of(2025, 10, 18);
        GameEntity game = GameEntity.builder()
                .id(1L)
                .gameId("20251018SSHH0")
                .gameDate(date)
                .homeTeam("SS")
                .awayTeam("HH")
                .seasonId(264)
                .gameStatus("SCHEDULED")
                .stadium("대구")
                .build();

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        when(gameRepository.findByGameDate(date)).thenReturn(List.of(game));
        when(gameRepository.findLeagueTypeCodeBySeasonId(264)).thenReturn(Optional.of(5));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(2, 2025))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 6)));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(3, 2025))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 9)));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(4, 2025))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 18)));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(5, 2025))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 26)));

        List<HomePageGameDto> games = homePageGameService.getGamesByDate(date);

        assertThat(games).hasSize(1);
        assertThat(games.get(0).getLeagueType()).isEqualTo("POSTSEASON");
        assertThat(games.get(0).getGameInfo()).isEqualTo("포스트시즌");
    }
}
