package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.auth.repository.UserRepository;
import com.example.common.config.CacheConfig;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.repository.GameDetailHeaderProjection;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.GameInningScoreRepository;
import com.example.kbo.repository.GameMetadataRepository;
import com.example.kbo.repository.MatchRangeProjection;
import com.example.kbo.repository.GameSummaryRepository;
import com.example.kbo.service.LeagueStageResolver;
import com.example.kbo.validation.BaseballDataIntegrityGuard;
import com.example.kbo.validation.ManualBaseballDataOverrideService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

abstract class PredictionServiceTestFixture {

    @Mock
    protected GameRepository gameRepository;

    @Mock
    protected PredictionRepository predictionRepository;

    @Mock
    protected GameMetadataRepository gameMetadataRepository;

    @Mock
    protected GameInningScoreRepository gameInningScoreRepository;

    @Mock
    protected GameSummaryRepository gameSummaryRepository;

    @Mock
    protected VoteFinalResultRepository voteFinalResultRepository;

    @Mock
    protected UserRepository userRepository;

    protected BaseballDataIntegrityGuard baseballDataIntegrityGuard;

    protected CacheManager cacheManager;

    protected PlatformTransactionManager transactionManager;

    protected PredictionService predictionService;

    @BeforeEach
    void setUp() {
        LeagueStageResolver leagueStageResolver = new LeagueStageResolver(gameRepository);
        baseballDataIntegrityGuard = mock(BaseballDataIntegrityGuard.class);
        cacheManager = new ConcurrentMapCacheManager(
                CacheConfig.PREDICTION_VOTE_STATUS,
                CacheConfig.GAME_DETAIL,
                CacheConfig.PREDICTION_MATCH_DAY,
                CacheConfig.RECENT_COMPLETED_GAMES,
                CacheConfig.PREDICTION_MATCH_RANGE);
        transactionManager = mock(PlatformTransactionManager.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        predictionService = new PredictionService(
                predictionRepository,
                gameRepository,
                gameMetadataRepository,
                gameInningScoreRepository,
                gameSummaryRepository,
                voteFinalResultRepository,
                userRepository,
                leagueStageResolver,
                baseballDataIntegrityGuard,
                cacheManager,
                transactionManager
        );
        lenient().when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        lenient().when(gameRepository.findFirstStartDateByTypeFromSeasonYear(anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        lenient().when(gameRepository.findLeagueTypeCodeBySeasonId(anyInt()))
                .thenReturn(Optional.empty());
        lenient().when(gameMetadataRepository.findByGameId(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
    }

/** Creates a mock GameEntity that passes isCanonicalGame() and validateVoteOpen() checks. */
    protected GameEntity buildCanonicalMockGame(String gameId) {
        GameEntity game = mock(GameEntity.class);
        lenient().when(game.getGameId()).thenReturn(gameId);
        lenient().when(game.getHomeTeam()).thenReturn("LG");
        lenient().when(game.getAwayTeam()).thenReturn("HH");
        // status=null → not in BLOCKED_VOTE_STATUSES, startTime=null → no time check
        return game;
    }

    protected void stubVoteWindowOpen(GameEntity game, String gameId) {
        GameMetadataEntity metadata = mock(GameMetadataEntity.class);
        when(game.getGameDate()).thenReturn(LocalDate.now().plusDays(1));
        when(metadata.getStartTime()).thenReturn(LocalTime.of(18, 30));
        when(gameMetadataRepository.findByGameId(gameId)).thenReturn(Optional.of(metadata));
    }

    protected MatchRangeProjection buildRangeMatch(
            String gameId,
            LocalDate gameDate,
            String homeTeam,
            String awayTeam,
            Integer seasonId,
            Integer rawLeagueTypeCode,
            Integer seriesGameNo) {
        MatchRangeProjection match = mock(MatchRangeProjection.class);
        lenient().when(match.getGameId()).thenReturn(gameId);
        lenient().when(match.getGameDate()).thenReturn(gameDate);
        lenient().when(match.getHomeTeam()).thenReturn(homeTeam);
        lenient().when(match.getAwayTeam()).thenReturn(awayTeam);
        lenient().when(match.getStadium()).thenReturn("잠실");
        lenient().when(match.getHomeScore()).thenReturn(0);
        lenient().when(match.getAwayScore()).thenReturn(0);
        lenient().when(match.getIsDummy()).thenReturn(false);
        lenient().when(match.getHomePitcher()).thenReturn(null);
        lenient().when(match.getAwayPitcher()).thenReturn(null);
        lenient().when(match.getSeasonId()).thenReturn(seasonId);
        lenient().when(match.getRawLeagueTypeCode()).thenReturn(rawLeagueTypeCode);
        lenient().when(match.getSeriesGameNo()).thenReturn(seriesGameNo);
        lenient().when(match.getGameStatus()).thenReturn("COMPLETED");
        lenient().when(match.getStartTime()).thenReturn(LocalTime.of(18, 30));
        return match;
    }

    protected GameEntity buildGame(String gameId, LocalDate gameDate, String homeTeam, String awayTeam, boolean isDummy) {
        return GameEntity.builder()
                .gameId(gameId)
                .gameDate(gameDate)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium("잠실")
                .isDummy(isDummy)
                .homeScore(0)
                .awayScore(0)
                .build();
    }

    protected void seedPredictionGameCaches(String gameId, LocalDate gameDate) {
        Cache gameDetailCache = cacheManager.getCache(CacheConfig.GAME_DETAIL);
        Cache matchDayCache = cacheManager.getCache(CacheConfig.PREDICTION_MATCH_DAY);
        Cache recentCompletedGamesCache = cacheManager.getCache(CacheConfig.RECENT_COMPLETED_GAMES);

        gameDetailCache.put(gameId, "stale-detail");
        if (gameDate != null) {
            matchDayCache.put(gameDate.toString(), "stale-day");
        }
        recentCompletedGamesCache.put("all", "stale-recent");
    }

    protected void assertPredictionGameCachesEvicted(String gameId, LocalDate gameDate) {
        Cache gameDetailCache = cacheManager.getCache(CacheConfig.GAME_DETAIL);
        Cache matchDayCache = cacheManager.getCache(CacheConfig.PREDICTION_MATCH_DAY);
        Cache recentCompletedGamesCache = cacheManager.getCache(CacheConfig.RECENT_COMPLETED_GAMES);

        assertThat(gameDetailCache.get(gameId)).isNull();
        if (gameDate != null) {
            assertThat(matchDayCache.get(gameDate.toString())).isNull();
        }
        assertThat(recentCompletedGamesCache.get("all")).isNull();
    }

    protected GameDetailHeaderProjection buildGameDetailHeader(String gameId, LocalDate gameDate) {
        GameDetailHeaderProjection header = mock(GameDetailHeaderProjection.class);
        lenient().when(header.getGameId()).thenReturn(gameId);
        lenient().when(header.getGameDate()).thenReturn(gameDate);
        lenient().when(header.getStadium()).thenReturn("잠실");
        lenient().when(header.getStadiumName()).thenReturn("잠실야구장");
        lenient().when(header.getStartTime()).thenReturn(LocalTime.of(18, 30));
        lenient().when(header.getAttendance()).thenReturn(12345);
        lenient().when(header.getWeather()).thenReturn("맑음");
        lenient().when(header.getGameTimeMinutes()).thenReturn(185);
        lenient().when(header.getHomeTeam()).thenReturn("LG");
        lenient().when(header.getAwayTeam()).thenReturn("HH");
        lenient().when(header.getHomeScore()).thenReturn(4);
        lenient().when(header.getAwayScore()).thenReturn(2);
        lenient().when(header.getHomePitcher()).thenReturn("임찬규");
        lenient().when(header.getAwayPitcher()).thenReturn("류현진");
        lenient().when(header.getGameStatus()).thenReturn("COMPLETED");
        return header;
    }

    protected GameDetailHeaderProjection buildGameDetailHeaderWithoutMetadata(String gameId, LocalDate gameDate) {
        GameDetailHeaderProjection header = buildGameDetailHeader(gameId, gameDate);
        lenient().when(header.getStadiumName()).thenReturn(null);
        lenient().when(header.getStartTime()).thenReturn(null);
        lenient().when(header.getAttendance()).thenReturn(null);
        lenient().when(header.getWeather()).thenReturn(null);
        lenient().when(header.getGameTimeMinutes()).thenReturn(null);
        return header;
    }



    protected PredictionService predictionServiceWithManualDataDates(LocalDate... dates) {
        return new PredictionService(
                predictionRepository,
                gameRepository,
                gameMetadataRepository,
                gameInningScoreRepository,
                gameSummaryRepository,
                voteFinalResultRepository,
                userRepository,
                new LeagueStageResolver(gameRepository),
                baseballDataIntegrityGuard,
                cacheManager,
                transactionManager,
                new ManualBaseballDataOverrideService(Set.of(dates)));
    }

    protected GameInningScoreRequestDto scoreRequest(
            Integer inning,
            String teamSide,
            String teamCode,
            Integer runs,
            Boolean isExtra) {
        GameInningScoreRequestDto request = new GameInningScoreRequestDto();
        request.setInning(inning);
        request.setTeamSide(teamSide);
        request.setTeamCode(teamCode);
        request.setRuns(runs);
        request.setIsExtra(isExtra);
        return request;
    }
}
