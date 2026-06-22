package com.example.homepage;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.MatchRangeProjection;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomePageGameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private HomePageTeamRepository homePageTeamRepository;

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
                homePageTeamRepository,
                stadiumDataSource,
                leagueStageResolver,
                baseballDataIntegrityGuard
        );
    }

    @Test
    @DisplayName("홈 일정 캐시 키는 기존 날짜별 경기 캐시와 충돌하지 않도록 prefix를 포함한다")
    void homeScheduleCacheKeysUseScopedPrefixes() {
        LocalDate selectedDate = LocalDate.of(2026, 4, 13);
        LocalDate endDate = LocalDate.of(2026, 4, 20);

        assertThat(homePageGameService.buildScheduledGamesWindowCacheKey(selectedDate, endDate))
                .isEqualTo("scheduledWindow:2026-04-13:2026-04-20");
        assertThat(homePageGameService.buildScheduleNavigationCacheKey(selectedDate))
                .isEqualTo("navigation:2026-04-13");
        assertThat(homePageGameService.buildScopedNavigationCacheKey(selectedDate, "SCHEDULED", 2026))
                .isEqualTo("scopedNavigation:2026-04-13:scheduled:2026");
    }

    @Test
    @DisplayName("리그 시작일은 운영자 제공 kbo_seasons 시작일을 우선 사용한다")
    void getLeagueStartDates_prefersConfiguredSeasonStartDates() {
        int seasonYear = LocalDate.now().getYear();

        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(0, seasonYear))
                .thenReturn(Optional.of(LocalDate.of(seasonYear, 3, 28)));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(2, seasonYear))
                .thenReturn(Optional.of(LocalDate.of(seasonYear, 10, 6)));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(5, seasonYear))
                .thenReturn(Optional.of(LocalDate.of(seasonYear, 10, 26)));

        LeagueStartDatesDto startDates = homePageGameService.getLeagueStartDates();

        assertThat(startDates.getRegularSeasonStart()).isEqualTo(seasonYear + "-03-28");
        assertThat(startDates.getPostseasonStart()).isEqualTo(seasonYear + "-10-06");
        assertThat(startDates.getKoreanSeriesStart()).isEqualTo(seasonYear + "-10-26");
        verify(gameRepository, never()).findFirstRegularSeasonDate(seasonYear);
        verify(gameRepository, never()).findFirstPostseasonDate(seasonYear);
        verify(gameRepository, never()).findFirstKoreanSeriesDate(seasonYear);
    }

    @Test
    @DisplayName("팀 순위 조회는 fast season range 쿼리를 우선 사용한다")
    void getTeamRankingsUsesFastSeasonRangeQuery() {
        when(gameRepository.findTeamRankingsBySeasonFast(
                2026,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1)))
                .thenReturn(List.<Object[]>of(new Object[] {
                        1,
                        "LG",
                        "LG",
                        80,
                        50,
                        2,
                        "0.615",
                        132,
                        0.0
                }));

        List<HomePageTeamRankingDto> rankings = homePageGameService.getTeamRankings(2026);

        assertThat(rankings).hasSize(1);
        assertThat(rankings.get(0).getRank()).isEqualTo(1);
        assertThat(rankings.get(0).getTeamId()).isEqualTo("LG");
        assertThat(rankings.get(0).getTeamName()).isEqualTo("LG");
        assertThat(rankings.get(0).getWins()).isEqualTo(80);
        assertThat(rankings.get(0).getLosses()).isEqualTo(50);
        assertThat(rankings.get(0).getDraws()).isEqualTo(2);
        assertThat(rankings.get(0).getWinRate()).isEqualTo("0.615");
        assertThat(rankings.get(0).getGames()).isEqualTo(132);
        assertThat(rankings.get(0).getGamesBehind()).isEqualTo(0.0);
        verify(gameRepository, never()).findTeamRankingsBySeasonFallback(2026);
    }

    @Test
    @DisplayName("fast 팀 순위 결과가 비어 있으면 legacy fallback 쿼리를 사용한다")
    void getTeamRankingsFallsBackToLegacyWhenFastQueryIsEmpty() {
        when(gameRepository.findTeamRankingsBySeasonFast(
                2025,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1)))
                .thenReturn(List.of());
        when(gameRepository.findTeamRankingsBySeasonFallback(2025))
                .thenReturn(List.<Object[]>of(new Object[] {
                        2,
                        "KT",
                        "KT",
                        75,
                        57,
                        1,
                        "0.568",
                        133,
                        4.5
                }));

        List<HomePageTeamRankingDto> rankings = homePageGameService.getTeamRankings(2025);

        assertThat(rankings).hasSize(1);
        assertThat(rankings.get(0).getRank()).isEqualTo(2);
        assertThat(rankings.get(0).getTeamId()).isEqualTo("KT");
        assertThat(rankings.get(0).getGamesBehind()).isEqualTo(4.5);
        verify(gameRepository).findTeamRankingsBySeasonFallback(2025);
    }

    @Test
    @DisplayName("league_type_code=5 인 경우 KOREAN_SERIES로 매핑된다")
    void getGamesByDate_koreanSeriesFromSeasonType() {
        LocalDate date = LocalDate.of(2025, 10, 28);
        MatchRangeProjection game = projection(
                "20251028SSGLG0",
                date,
                "문학",
                "SSG",
                "LG",
                null,
                null,
                9005,
                null,
                "SCHEDULED",
                null);

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        when(gameRepository.findCanonicalRangeProjectionByGameDate(eq(date), anyList())).thenReturn(List.of(game));
        when(gameRepository.findLeagueTypeCodeBySeasonId(9005)).thenReturn(Optional.of(5));

        List<HomePageGameDto> games = homePageGameService.getGamesByDate(date);

        assertThat(games).hasSize(1);
        assertThat(games.get(0).getLeagueType()).isEqualTo("KOREAN_SERIES");
        assertThat(games.get(0).getGameInfo()).isEqualTo("한국시리즈");
        assertThat(games.get(0).getGameDate()).isEqualTo("2025-10-28");
        assertThat(games.get(0).getSourceDate()).isEqualTo("2025-10-28");
    }

    @Test
    @DisplayName("홈 날짜 조회는 projection query와 start_time을 사용하고 full GameEntity 조회를 피한다")
    void getGamesByDateUsesProjectionQueryWithoutFullEntityLoad() {
        LocalDate date = LocalDate.of(2026, 4, 5);
        MatchRangeProjection projection = projection(
                "20260405LGKT0",
                date,
                "잠실",
                "LG",
                "KT",
                5,
                1,
                2026,
                0,
                "COMPLETED",
                LocalTime.of(14, 0));
        GameEntity legacyEntity = GameEntity.builder()
                .id(1L)
                .gameId("20260405LGKT0")
                .gameDate(date)
                .homeTeam("LG")
                .awayTeam("KT")
                .gameStatus("COMPLETED")
                .homeScore(5)
                .awayScore(1)
                .stadium("잠실")
                .build();

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        lenient().when(gameRepository.findCanonicalRangeProjectionByGameDate(eq(date), anyList()))
                .thenReturn(List.of(projection));
        lenient().when(gameRepository.findByGameDate(date)).thenReturn(List.of(legacyEntity));

        List<HomePageGameDto> games = homePageGameService.getGamesByDate(date);

        assertThat(games).hasSize(1);
        assertThat(games.get(0).getGameId()).isEqualTo("20260405LGKT0");
        assertThat(games.get(0).getLeagueType()).isEqualTo("REGULAR");
        assertThat(games.get(0).getTime()).isEqualTo("14:00");
        verify(gameRepository).findCanonicalRangeProjectionByGameDate(eq(date), anyList());
        verify(gameRepository, never()).findByGameDate(date);
    }

    @Test
    @DisplayName("kbo_seasons 매핑이 없어도 season_id가 경기 연도와 같으면 REGULAR로 본다")
    void getGamesByDate_infersRegularSeasonWhenSeasonIdMatchesGameYear() {
        LocalDate date = LocalDate.of(2026, 3, 31);
        MatchRangeProjection game = projection(
                "20260331HTLG0",
                date,
                "잠실",
                "LG",
                "KIA",
                2,
                7,
                2026,
                null,
                "COMPLETED",
                null);

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        when(gameRepository.findCanonicalRangeProjectionByGameDate(eq(date), anyList())).thenReturn(List.of(game));
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
        MatchRangeProjection game = projection(
                "20251018SSHH0",
                date,
                "대구",
                "SS",
                "HH",
                null,
                null,
                264,
                null,
                "SCHEDULED",
                null);

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        when(gameRepository.findCanonicalRangeProjectionByGameDate(eq(date), anyList())).thenReturn(List.of(game));
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
        MatchRangeProjection game = projection(
                "20260403SSGKIA0",
                date,
                "문학",
                "SSG",
                "KIA",
                11,
                6,
                null,
                null,
                "SCHEDULED",
                null);

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        when(gameRepository.findCanonicalRangeProjectionByGameDate(eq(date), anyList())).thenReturn(List.of(game));

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
        MatchRangeProjection game = projection(
                "20260404SSGKIA0",
                date,
                "문학",
                "SSG",
                "KIA",
                3,
                1,
                null,
                null,
                "SCHEDULED",
                null);

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        when(gameRepository.findCanonicalRangeProjectionByGameDate(eq(date), anyList())).thenReturn(List.of(game));

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
        MatchRangeProjection game = projection(
                gameId,
                date,
                "잠실",
                "LG",
                "KT",
                5,
                1,
                2026,
                0,
                "COMPLETED",
                LocalTime.of(14, 0));

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        when(gameRepository.findCanonicalRangeProjectionByGameDate(eq(date), anyList())).thenReturn(List.of(game));

        List<HomePageGameDto> games = homePageGameService.getGamesByDate(date);

        assertThat(games).hasSize(1);
        assertThat(games.get(0).getTime()).isEqualTo("14:00");
    }

    @Test
    @DisplayName("예정 경기 윈도우에 행이 없으면 빈 배열을 정상 응답으로 반환한다")
    void getScheduledGamesWindow_returnsEmptyListWhenNoUpcomingGamesExist() {
        LocalDate startDate = LocalDate.of(2026, 4, 13);
        LocalDate endDate = startDate.plusDays(7);

        when(gameRepository.findScheduledWindowProjectionByDateRange(
                eq(startDate),
                eq(endDate),
                anyCollection(),
                anyList())).thenReturn(List.of());

        List<HomePageScheduledGameDto> scheduledGames = homePageGameService.getScheduledGamesWindow(startDate, endDate);

        assertThat(scheduledGames).isEmpty();
        verifyNoInteractions(baseballDataIntegrityGuard);
    }

    @Test
    @DisplayName("예정 경기 윈도우는 projection query를 사용하고 full GameEntity 조회를 피한다")
    void getScheduledGamesWindowUsesProjectionQueryWithoutFullEntityLoad() {
        LocalDate startDate = LocalDate.of(2026, 4, 13);
        LocalDate endDate = startDate.plusDays(7);
        MatchRangeProjection projection = projection(
                "20260414NCKT0",
                LocalDate.of(2026, 4, 14),
                "창원",
                "NC",
                "KT",
                null,
                null,
                2026,
                0,
                "SCHEDULED",
                LocalTime.of(18, 30));
        GameEntity legacyEntity = GameEntity.builder()
                .id(1L)
                .gameId("20260414NCKT0")
                .gameDate(LocalDate.of(2026, 4, 14))
                .homeTeam("NC")
                .awayTeam("KT")
                .gameStatus("SCHEDULED")
                .stadium("창원")
                .build();

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        lenient().when(gameRepository.findScheduledWindowProjectionByDateRange(
                        eq(startDate),
                        eq(endDate),
                        anyCollection(),
                        anyList()))
                .thenReturn(List.of(projection));
        lenient().when(gameRepository.findScheduledGamesByDateRange(
                        eq(startDate),
                        eq(endDate),
                        anyCollection()))
                .thenReturn(List.of(legacyEntity));

        List<HomePageScheduledGameDto> scheduledGames = homePageGameService.getScheduledGamesWindow(startDate, endDate);

        assertThat(scheduledGames).hasSize(1);
        assertThat(scheduledGames.get(0).getGameId()).isEqualTo("20260414NCKT0");
        assertThat(scheduledGames.get(0).getSourceDate()).isEqualTo("2026-04-14");
        assertThat(scheduledGames.get(0).getTime()).isEqualTo("18:30");
        verify(gameRepository).findScheduledWindowProjectionByDateRange(
                eq(startDate),
                eq(endDate),
                anyCollection(),
                anyList());
        verify(gameRepository, never()).findScheduledGamesByDateRange(
                eq(startDate),
                eq(endDate),
                anyCollection());
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

    private MatchRangeProjection projection(
            String gameId,
            LocalDate gameDate,
            String stadium,
            String homeTeam,
            String awayTeam,
            Integer homeScore,
            Integer awayScore,
            Integer seasonId,
            Integer rawLeagueTypeCode,
            String gameStatus,
            LocalTime startTime) {
        return new TestMatchRangeProjection(
                gameId,
                gameDate,
                stadium,
                homeTeam,
                awayTeam,
                homeScore,
                awayScore,
                false,
                null,
                null,
                seasonId,
                rawLeagueTypeCode,
                null,
                gameStatus,
                startTime);
    }

    private record TestMatchRangeProjection(
            String gameId,
            LocalDate gameDate,
            String stadium,
            String homeTeam,
            String awayTeam,
            Integer homeScore,
            Integer awayScore,
            Boolean isDummy,
            String homePitcher,
            String awayPitcher,
            Integer seasonId,
            Integer rawLeagueTypeCode,
            Integer seriesGameNo,
            String gameStatus,
            LocalTime startTime) implements MatchRangeProjection {

        @Override
        public String getGameId() {
            return gameId;
        }

        @Override
        public LocalDate getGameDate() {
            return gameDate;
        }

        @Override
        public String getStadium() {
            return stadium;
        }

        @Override
        public String getHomeTeam() {
            return homeTeam;
        }

        @Override
        public String getAwayTeam() {
            return awayTeam;
        }

        @Override
        public Integer getHomeScore() {
            return homeScore;
        }

        @Override
        public Integer getAwayScore() {
            return awayScore;
        }

        @Override
        public Boolean getIsDummy() {
            return isDummy;
        }

        @Override
        public String getHomePitcher() {
            return homePitcher;
        }

        @Override
        public String getAwayPitcher() {
            return awayPitcher;
        }

        @Override
        public Integer getSeasonId() {
            return seasonId;
        }

        @Override
        public Integer getRawLeagueTypeCode() {
            return rawLeagueTypeCode;
        }

        @Override
        public Integer getSeriesGameNo() {
            return seriesGameNo;
        }

        @Override
        public String getGameStatus() {
            return gameStatus;
        }

        @Override
        public LocalTime getStartTime() {
            return startTime;
        }
    }
}
