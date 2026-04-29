package com.example.homepage;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.repository.GameMetadataRepository;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.service.LeagueStageResolver;
import com.example.kbo.validation.BaseballDataIntegrityGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomePageGameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private HomePageTeamRepository homePageTeamRepository;

    @Mock
    private GameMetadataRepository gameMetadataRepository;

    @Mock
    private DataSource stadiumDataSource;

    @Mock
    private BaseballDataIntegrityGuard baseballDataIntegrityGuard;

    private HomePageGameService homePageGameService;

    @BeforeEach
    void setUp() {
        LeagueStageResolver leagueStageResolver = new LeagueStageResolver(gameRepository);
        homePageGameService = new HomePageGameService(
                gameRepository,
                gameMetadataRepository,
                homePageTeamRepository,
                stadiumDataSource,
                leagueStageResolver,
                baseballDataIntegrityGuard
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
        assertThat(games.get(0).getGameDate()).isEqualTo("2025-10-28");
        assertThat(games.get(0).getSourceDate()).isEqualTo("2025-10-28");
    }

    @Test
    @DisplayName("kbo_seasons 매핑이 없어도 season_id가 경기 연도와 같으면 REGULAR로 본다")
    void getGamesByDate_infersRegularSeasonWhenSeasonIdMatchesGameYear() {
        LocalDate date = LocalDate.of(2026, 3, 31);
        GameEntity game = GameEntity.builder()
                .id(1L)
                .gameId("20260331HTLG0")
                .gameDate(date)
                .homeTeam("LG")
                .awayTeam("KIA")
                .seasonId(2026)
                .gameStatus("COMPLETED")
                .stadium("잠실")
                .homeScore(2)
                .awayScore(7)
                .build();

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        when(gameRepository.findByGameDate(date)).thenReturn(List.of(game));
        when(gameRepository.findLeagueTypeCodeBySeasonId(2026)).thenReturn(Optional.empty());

        List<HomePageGameDto> games = homePageGameService.getGamesByDate(date);

        assertThat(games).hasSize(1);
        assertThat(games.get(0).getLeagueType()).isEqualTo("REGULAR");
        assertThat(games.get(0).getGameInfo()).isEmpty();
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

    @Test
    @DisplayName("raw status가 SCHEDULED여도 과거 경기 점수가 있으면 종료 상태로 보정한다")
    void getGamesByDate_normalizesPastScheduledGameWithScore() {
        LocalDate date = LocalDate.now().minusDays(1);
        GameEntity game = GameEntity.builder()
                .id(1L)
                .gameId("20260403SSGKIA0")
                .gameDate(date)
                .homeTeam("SSG")
                .awayTeam("KIA")
                .gameStatus("SCHEDULED")
                .homeScore(11)
                .awayScore(6)
                .stadium("문학")
                .build();

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        when(gameRepository.findByGameDate(date)).thenReturn(List.of(game));

        List<HomePageGameDto> games = homePageGameService.getGamesByDate(date);

        assertThat(games).hasSize(1);
        assertThat(games.get(0).getGameStatus()).isEqualTo("COMPLETED");
        assertThat(games.get(0).getGameStatusKr()).isEqualTo("경기 종료");
        assertThat(games.get(0).getHomeScore()).isEqualTo(11);
        assertThat(games.get(0).getAwayScore()).isEqualTo(6);
    }

    @Test
    @DisplayName("raw status가 SCHEDULED여도 오늘 점수가 있으면 진행중 상태로 보정한다")
    void getGamesByDate_normalizesTodayScheduledGameWithScore() {
        LocalDate date = LocalDate.now();
        GameEntity game = GameEntity.builder()
                .id(1L)
                .gameId("20260404SSGKIA0")
                .gameDate(date)
                .homeTeam("SSG")
                .awayTeam("KIA")
                .gameStatus("SCHEDULED")
                .homeScore(3)
                .awayScore(1)
                .stadium("문학")
                .build();

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        when(gameRepository.findByGameDate(date)).thenReturn(List.of(game));

        List<HomePageGameDto> games = homePageGameService.getGamesByDate(date);

        assertThat(games).hasSize(1);
        assertThat(games.get(0).getGameStatus()).isEqualTo("LIVE");
        assertThat(games.get(0).getGameStatusKr()).isEqualTo("경기 진행중");
    }

    @Test
    @DisplayName("game_metadata.start_time 이 있으면 홈 일정 시간에 반영한다")
    void getGamesByDate_usesMetadataStartTime() {
        LocalDate date = LocalDate.of(2026, 4, 5);
        String gameId = "20260405LGKT0";
        GameEntity game = GameEntity.builder()
                .id(1L)
                .gameId(gameId)
                .gameDate(date)
                .homeTeam("LG")
                .awayTeam("KT")
                .gameStatus("COMPLETED")
                .homeScore(5)
                .awayScore(1)
                .stadium("잠실")
                .build();

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        when(gameRepository.findByGameDate(date)).thenReturn(List.of(game));
        when(gameMetadataRepository.findAllById(List.of(gameId))).thenReturn(List.of(
                GameMetadataEntity.builder()
                        .gameId(gameId)
                        .startTime(LocalTime.of(14, 0))
                        .build()
        ));

        List<HomePageGameDto> games = homePageGameService.getGamesByDate(date);

        assertThat(games).hasSize(1);
        assertThat(games.get(0).getTime()).isEqualTo("14:00");
    }

    @Test
    @DisplayName("예정 경기 윈도우에 행이 없으면 빈 배열을 정상 응답으로 반환한다")
    void getScheduledGamesWindow_returnsEmptyListWhenNoUpcomingGamesExist() {
        LocalDate startDate = LocalDate.of(2026, 4, 13);
        LocalDate endDate = startDate.plusDays(7);

        when(gameRepository.findScheduledGamesByDateRange(startDate, endDate, List.of(
                "SCHEDULED",
                "READY",
                "UPCOMING",
                "NOT_STARTED",
                "PRE_GAME",
                "BEFORE_GAME",
                "POSTPONED",
                "CANCELLED",
                "CANCEL"))).thenReturn(List.of());

        List<HomePageScheduledGameDto> scheduledGames = homePageGameService.getScheduledGamesWindow(startDate, endDate);

        assertThat(scheduledGames).isEmpty();
        verifyNoInteractions(baseballDataIntegrityGuard);
    }

    @Test
    @DisplayName("선택한 날짜에 경기가 없어도 이전/다음 경기일 네비게이션을 계산한다")
    void getScheduleNavigation_returnsAdjacentDatesWithoutSameDayGames() {
        LocalDate selectedDate = LocalDate.of(2026, 4, 13);
        LocalDate prevDate = LocalDate.of(2026, 4, 12);
        LocalDate nextDate = LocalDate.of(2026, 4, 14);

        when(gameRepository.findPrevGameDate(selectedDate)).thenReturn(Optional.of(prevDate));
        when(gameRepository.findNextGameDate(selectedDate)).thenReturn(Optional.of(nextDate));

        ScheduleNavigationDto navigation = homePageGameService.getScheduleNavigation(selectedDate);

        assertThat(navigation.getPrevGameDate()).isEqualTo(prevDate);
        assertThat(navigation.getNextGameDate()).isEqualTo(nextDate);
        assertThat(navigation.isHasPrev()).isTrue();
        assertThat(navigation.isHasNext()).isTrue();
        verifyNoInteractions(baseballDataIntegrityGuard);
    }
}
