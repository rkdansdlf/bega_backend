package com.example.prediction;

import com.example.common.config.CacheConfig;
import com.example.common.exception.ConflictBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import com.example.kbo.repository.CanonicalAdjacentGameDatesProjection;
import com.example.kbo.repository.CanonicalGameDateBoundsProjection;
import com.example.kbo.repository.GameDetailHeaderProjection;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameInningScoreEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.entity.GameSummaryEntity;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.GameInningScoreRepository;
import com.example.kbo.repository.GameMetadataRepository;
import com.example.kbo.repository.MatchRangeProjection;
import com.example.kbo.repository.PredictionStatsGameProjection;
import com.example.kbo.repository.GameSummaryRepository;
import com.example.kbo.service.LeagueStageResolver;
import com.example.kbo.validation.BaseballDataIntegrityGuard;
import com.example.kbo.util.GameStatusResolver;
import com.example.kbo.util.KboTeamCodePolicy;
import com.example.kbo.util.TeamCodeResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final GameRepository gameRepository;
    private final GameMetadataRepository gameMetadataRepository;
    private final GameInningScoreRepository gameInningScoreRepository;
    private final GameSummaryRepository gameSummaryRepository;
    private final VoteFinalResultRepository voteFinalResultRepository;
    private final com.example.auth.repository.UserRepository userRepository;
    private final LeagueStageResolver leagueStageResolver;
    private final BaseballDataIntegrityGuard baseballDataIntegrityGuard;
    private final CacheManager cacheManager;
    private final PlatformTransactionManager transactionManager;
    private static final Set<String> BLOCKED_VOTE_STATUSES = Set.of(
            "COMPLETED",
            "CANCELLED",
            "POSTPONED",
            "SUSPENDED",
            "DELAYED",
            "LIVE",
            "IN_PROGRESS",
            "INPROGRESS");
    private static final Pattern GAME_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final List<String> CANONICAL_TEAMS = List.of("SS", "LT", "LG", "DB", "KIA", "KH", "HH", "SSG", "NC", "KT");
    private static final List<String> QUERYABLE_TEAM_CODES = CANONICAL_TEAMS.stream()
            .flatMap(teamCode -> TeamCodeResolver.resolveVariants(teamCode).stream())
            .distinct()
            .collect(Collectors.toList());
    private static final int MAX_VOTE_RETRY_ATTEMPTS = 2;
    private static final long MAX_SNAPSHOT_SYNC_RANGE_DAYS = 31;
    private static final String MATCH_NOT_FOUND_CODE = "MATCH_NOT_FOUND";

    public PredictionService(
            PredictionRepository predictionRepository,
            GameRepository gameRepository,
            GameMetadataRepository gameMetadataRepository,
            GameInningScoreRepository gameInningScoreRepository,
            GameSummaryRepository gameSummaryRepository,
            VoteFinalResultRepository voteFinalResultRepository,
            com.example.auth.repository.UserRepository userRepository,
            LeagueStageResolver leagueStageResolver,
            BaseballDataIntegrityGuard baseballDataIntegrityGuard,
            CacheManager cacheManager,
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        this.predictionRepository = predictionRepository;
        this.gameRepository = gameRepository;
        this.gameMetadataRepository = gameMetadataRepository;
        this.gameInningScoreRepository = gameInningScoreRepository;
        this.gameSummaryRepository = gameSummaryRepository;
        this.voteFinalResultRepository = voteFinalResultRepository;
        this.userRepository = userRepository;
        this.leagueStageResolver = leagueStageResolver;
        this.baseballDataIntegrityGuard = baseballDataIntegrityGuard;
        this.cacheManager = cacheManager;
        this.transactionManager = transactionManager;
    }

    @Cacheable(value = CacheConfig.RECENT_COMPLETED_GAMES, key = "'all'")
    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public List<MatchDto> getRecentCompletedGames() {
        LocalDate today = LocalDate.now();

        List<LocalDate> allDates = gameRepository.findRecentDistinctGameDates(today);
        List<LocalDate> recentDates = Objects.requireNonNull(allDates.stream()
                .limit(7)
                .collect(Collectors.toList()));

        if (recentDates.isEmpty()) {
            return List.of();
        }

        for (LocalDate recentDate : recentDates) {
            baseballDataIntegrityGuard.ensurePredictionDateMatches(
                    "prediction.past_games",
                    recentDate,
                    gameRepository.findCanonicalRangeProjectionByGameDate(
                            recentDate,
                            QUERYABLE_TEAM_CODES));
        }

        List<MatchRangeProjection> matches = gameRepository.findCanonicalCompletedRangeProjectionByGameDates(
                recentDates,
                QUERYABLE_TEAM_CODES);
        baseballDataIntegrityGuard.ensurePredictionRangeMatches("prediction.past_games", matches);

        Map<String, Integer> seriesGameNos = computeSeriesGameNos(matches);
        return Objects.requireNonNull(matches.stream()
                .map(m -> toMatchDto(m, seriesGameNos))
                .collect(Collectors.toList()));
    }

    // 특정 날짜의 경기 조회 (실제 DB 데이터만 조회, 더미 및 Mock 제외)
    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public List<MatchDto> getMatchesByDate(LocalDate date) {
        List<MatchRangeProjection> matches = gameRepository.findCanonicalRangeProjectionByGameDate(
                date,
                QUERYABLE_TEAM_CODES);
        if (matches.isEmpty()) {
            CanonicalAdjacentGameDatesProjection adjacentDates =
                    gameRepository.findCanonicalAdjacentGameDates(date, QUERYABLE_TEAM_CODES);
            if (isCanonicalOffDay(adjacentDates)) {
                return List.of();
            }
        }
        baseballDataIntegrityGuard.ensurePredictionDateMatches("prediction.matches_by_date", date, matches);

        Map<String, Integer> seriesGameNos = computeSeriesGameNos(matches);
        return Objects.requireNonNull(matches.stream()
                .map(m -> toMatchDto(m, seriesGameNos))
                .collect(Collectors.toList()));
    }

    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public MatchDayNavigationResponseDto getMatchDayNavigation(LocalDate date) {
        LocalDate targetDate = Objects.requireNonNull(date, "조회 날짜가 올바르지 않습니다.");
        List<MatchRangeProjection> rawMatches = gameRepository.findCanonicalRangeProjectionByGameDate(
                targetDate,
                QUERYABLE_TEAM_CODES);
        CanonicalAdjacentGameDatesProjection adjacentDates =
                gameRepository.findCanonicalAdjacentGameDates(targetDate, QUERYABLE_TEAM_CODES);

        if (rawMatches.isEmpty() && !isCanonicalOffDay(adjacentDates)) {
            baseballDataIntegrityGuard.ensurePredictionDateMatches(
                    "prediction.matches_by_date",
                    targetDate,
                    rawMatches);
        } else if (!rawMatches.isEmpty()) {
            baseballDataIntegrityGuard.ensurePredictionDateMatches(
                    "prediction.matches_by_date",
                    targetDate,
                    rawMatches);
        }

        Map<String, Integer> seriesGameNos = computeSeriesGameNos(rawMatches);
        List<MatchDto> matches = rawMatches.stream()
                .map(m -> toMatchDto(m, seriesGameNos))
                .collect(Collectors.toList());
        LocalDate prevDate = adjacentDates == null ? null : adjacentDates.getPrevDate();
        LocalDate nextDate = adjacentDates == null ? null : adjacentDates.getNextDate();

        return new MatchDayNavigationResponseDto(
                targetDate,
                matches,
                prevDate,
                nextDate,
                prevDate != null,
                nextDate != null
        );
    }

    private boolean isCanonicalOffDay(CanonicalAdjacentGameDatesProjection adjacentDates) {
        return adjacentDates != null
                && adjacentDates.getPrevDate() != null
                && adjacentDates.getNextDate() != null;
    }

    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public List<MatchDto> getMatchesByDateRange(LocalDate startDate, LocalDate endDate) {
        return getMatchesByDateRange(startDate, endDate, true, 0, 1000);
    }

    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public List<MatchDto> getMatchesByDateRange(
            LocalDate startDate,
            LocalDate endDate,
            boolean includePast,
            int page,
            int pageSize
    ) {
        List<MatchRangeProjection> matches = getCanonicalMatchRangeProjectionList(
                startDate,
                endDate,
                includePast,
                page,
                pageSize);
        baseballDataIntegrityGuard.ensurePredictionRangeMatches("prediction.matches_by_range", matches);

        Map<String, Integer> seriesGameNos = computeSeriesGameNos(matches);
        return Objects.requireNonNull(matches.stream()
                .map(m -> toMatchDto(m, seriesGameNos))
                .collect(Collectors.toList()));
    }

    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public MatchRangePageResponseDto getMatchesByDateRangeWithMetadata(
            LocalDate startDate,
            LocalDate endDate,
            boolean includePast,
            int page,
            int pageSize
    ) {
        Page<MatchRangeProjection> matches = getCanonicalMatchRangeProjectionPage(
                startDate,
                endDate,
                includePast,
                page,
                pageSize);
        baseballDataIntegrityGuard.ensurePredictionRangeMatches(
                "prediction.matches_by_range",
                matches.getContent());

        Map<String, Integer> seriesGameNos = computeSeriesGameNos(matches.getContent());
        return new MatchRangePageResponseDto(
                matches.getContent().stream()
                        .map(m -> toMatchDto(m, seriesGameNos))
                        .collect(Collectors.toList()),
                matches.getNumber(),
                matches.getSize(),
                matches.getTotalElements(),
                matches.getTotalPages(),
                matches.hasNext(),
                matches.hasPrevious()
        );
    }

    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public MatchBoundsResponseDto getMatchBounds() {
        CanonicalGameDateBoundsProjection bounds = gameRepository.findCanonicalGameDateBounds(QUERYABLE_TEAM_CODES);
        LocalDate earliestGameDate = bounds == null ? null : bounds.getEarliestGameDate();
        LocalDate latestGameDate = bounds == null ? null : bounds.getLatestGameDate();
        boolean hasData = earliestGameDate != null && latestGameDate != null;

        return new MatchBoundsResponseDto(
                hasData ? earliestGameDate : null,
                hasData ? latestGameDate : null,
                hasData
        );
    }

    private MatchDto toMatchDto(MatchRangeProjection match, Map<String, Integer> seriesGameNos) {
        Integer leagueTypeCode = leagueStageResolver.resolveEffectiveLeagueTypeCode(
                match.getRawLeagueTypeCode(),
                match.getGameDate(),
                match.getSeasonId(),
                match.getGameId());
        boolean hasKnownScore = match.getHomeScore() != null && match.getAwayScore() != null;
        String effectiveGameStatus = GameStatusResolver.resolveEffectiveStatus(
                match.getGameStatus(),
                match.getGameDate(),
                match.getStartTime(),
                match.getHomeScore(),
                match.getAwayScore(),
                hasKnownScore);
        Integer seriesGameNo = seriesGameNos != null ? seriesGameNos.get(match.getGameId()) : null;
        return MatchDto.builder()
                .gameId(match.getGameId())
                .gameDate(match.getGameDate())
                .homeTeam(com.example.kbo.util.TeamCodeNormalizer.normalize(match.getHomeTeam()))
                .awayTeam(com.example.kbo.util.TeamCodeNormalizer.normalize(match.getAwayTeam()))
                .stadium(match.getStadium())
                .startTime(match.getStartTime())
                .homeScore(match.getHomeScore())
                .awayScore(match.getAwayScore())
                .winner(resolveWinner(match.getHomeScore(), match.getAwayScore()))
                .gameStatus(effectiveGameStatus)
                .isDummy(match.getIsDummy())
                .homePitcher(MatchDto.pitcherOf(match.getHomePitcher()))
                .awayPitcher(MatchDto.pitcherOf(match.getAwayPitcher()))
                .aiSummary(null)
                .winProbability(null)
                .seasonId(match.getSeasonId())
                .leagueType(mapLeagueType(leagueTypeCode))
                .postSeasonSeries(mapPostSeasonSeries(leagueTypeCode))
                .seriesGameNo(seriesGameNo)
                .build();
    }

    /**
     * 포스트시즌 경기(rawLeagueTypeCode 2~5)의 seriesGameNo를 Java에서 계산합니다.
     * DB 상관 서브쿼리를 제거한 대신 쿼리 결과 목록(game_date ASC, game_id ASC 정렬)을
     * 순회하며 같은 시즌/팀 조합 내 누적 경기 번호를 계산합니다.
     * 정규 시즌 경기(code 0,1)는 null을 반환합니다.
     */
    private Map<String, Integer> computeSeriesGameNos(List<MatchRangeProjection> matches) {
        if (matches == null || matches.isEmpty()) {
            return Collections.emptyMap();
        }
        // key: "seasonId:teamA:teamB" (팀 코드 정렬), value: 누적 게임 수
        Map<String, Integer> seriesCounter = new HashMap<>();
        Map<String, Integer> result = new HashMap<>();
        for (MatchRangeProjection m : matches) {
            Integer code = m.getRawLeagueTypeCode();
            if (code == null || code < 2 || code > 5) {
                continue; // 포스트시즌 외에는 계산 불필요
            }
            String gameId = m.getGameId();
            if (gameId == null) continue;
            Integer seasonId = m.getSeasonId();
            String homeTeam = m.getHomeTeam();
            String awayTeam = m.getAwayTeam();
            if (seasonId == null || homeTeam == null || awayTeam == null) {
                continue;
            }
            String teamA = homeTeam.trim().toUpperCase(Locale.ROOT);
            String teamB = awayTeam.trim().toUpperCase(Locale.ROOT);
            // 정렬하여 팀 순서에 무관한 키 생성
            String seriesKey = seasonId + ":" + (teamA.compareTo(teamB) <= 0
                    ? teamA + ":" + teamB
                    : teamB + ":" + teamA);
            int gameNo = seriesCounter.merge(seriesKey, 1, Integer::sum);
            result.put(gameId, gameNo);
        }
        return result;
    }

    private String mapLeagueType(Integer leagueTypeCode) {
        if (leagueTypeCode == null) {
            return null;
        }
        return switch (leagueTypeCode) {
            case 0 -> "REGULAR";
            case 1 -> "PRE";
            case 2, 3, 4, 5 -> "POST";
            default -> null;
        };
    }

    private String mapPostSeasonSeries(Integer leagueTypeCode) {
        if (leagueTypeCode == null) {
            return null;
        }
        return switch (leagueTypeCode) {
            case 2 -> "WC";
            case 3 -> "SEMI_PO";
            case 4 -> "PO";
            case 5 -> "KS";
            default -> null;
        };
    }

    private String resolveWinner(Integer homeScore, Integer awayScore) {
        if (homeScore == null || awayScore == null) {
            return null;
        }
        if (homeScore.equals(awayScore)) {
            return "draw";
        }
        return homeScore > awayScore ? "home" : "away";
    }

    private List<MatchRangeProjection> getCanonicalMatchRangeProjectionList(
            LocalDate startDate,
            LocalDate endDate,
            boolean includePast,
            int page,
            int pageSize
    ) {
        CanonicalRangeRequest request = normalizeCanonicalRangeRequest(startDate, endDate, includePast, page, pageSize);

        if (request.effectiveStartDate().isAfter(request.effectiveEndDate())) {
            log.info(
                    "prediction.range.end_reached includePast={} requestedWindow={}~{} effectiveWindow={}~{} page={} size={} reason=effective-window-empty",
                    includePast,
                    startDate,
                    endDate,
                    request.effectiveStartDate(),
                    request.effectiveEndDate(),
                    request.pageable().getPageNumber(),
                    request.pageable().getPageSize()
            );
            return List.of();
        }

        List<MatchRangeProjection> matches = gameRepository.findCanonicalRangeProjectionByDateRangeNoCount(
                request.effectiveStartDate(),
                request.effectiveEndDate(),
                QUERYABLE_TEAM_CODES,
                request.pageable());

        if (matches.isEmpty()) {
            log.info(
                    "prediction.range.end_reached includePast={} requestedWindow={}~{} effectiveWindow={}~{} page={} size={} reason=no-content",
                    includePast,
                    startDate,
                    endDate,
                    request.effectiveStartDate(),
                    request.effectiveEndDate(),
                    request.pageable().getPageNumber(),
                    request.pageable().getPageSize()
            );
        } else {
            log.info(
                    "prediction.range.load includePast={} result=success window={}~{} page={} size={} content={}",
                    includePast,
                    request.effectiveStartDate(),
                    request.effectiveEndDate(),
                    request.pageable().getPageNumber(),
                    request.pageable().getPageSize(),
                    matches.size()
            );
        }

        return matches;
    }

    private Page<MatchRangeProjection> getCanonicalMatchRangeProjectionPage(
            LocalDate startDate,
            LocalDate endDate,
            boolean includePast,
            int page,
            int pageSize
    ) {
        CanonicalRangeRequest request = normalizeCanonicalRangeRequest(startDate, endDate, includePast, page, pageSize);

        if (request.effectiveStartDate().isAfter(request.effectiveEndDate())) {
            log.info(
                    "prediction.range.end_reached includePast={} requestedWindow={}~{} effectiveWindow={}~{} page={} size={} reason=effective-window-empty",
                    includePast,
                    startDate,
                    endDate,
                    request.effectiveStartDate(),
                    request.effectiveEndDate(),
                    request.pageable().getPageNumber(),
                    request.pageable().getPageSize()
            );
            return Page.empty(request.pageable());
        }

        Page<MatchRangeProjection> rangePage = gameRepository.findCanonicalRangeProjectionByDateRange(
                request.effectiveStartDate(),
                request.effectiveEndDate(),
                QUERYABLE_TEAM_CODES,
                request.pageable());

        if (rangePage.isEmpty()) {
            log.info(
                    "prediction.range.end_reached includePast={} requestedWindow={}~{} effectiveWindow={}~{} page={} size={} reason=no-content",
                    includePast,
                    startDate,
                    endDate,
                    request.effectiveStartDate(),
                    request.effectiveEndDate(),
                    rangePage.getNumber(),
                    rangePage.getSize()
            );
        } else {
            log.info(
                    "prediction.range.load includePast={} result=success window={}~{} page={} size={} content={} hasNext={} hasPrevious={}",
                    includePast,
                    request.effectiveStartDate(),
                    request.effectiveEndDate(),
                    rangePage.getNumber(),
                    rangePage.getSize(),
                    rangePage.getNumberOfElements(),
                    rangePage.hasNext(),
                    rangePage.hasPrevious()
            );
        }

        return rangePage;
    }

    private CanonicalRangeRequest normalizeCanonicalRangeRequest(
            LocalDate startDate,
            LocalDate endDate,
            boolean includePast,
            int page,
            int pageSize
    ) {
        if (startDate == null || endDate == null) {
            log.warn(
                    "prediction.range.error reason=invalid-window-null startDate={} endDate={} includePast={} page={} size={}",
                    startDate,
                    endDate,
                    includePast,
                    page,
                    pageSize
            );
            throw new IllegalArgumentException("조회 기간이 올바르지 않습니다.");
        }

        if (startDate.isAfter(endDate)) {
            log.warn(
                    "prediction.range.error reason=invalid-window-order startDate={} endDate={} includePast={} page={} size={}",
                    startDate,
                    endDate,
                    includePast,
                    page,
                    pageSize
            );
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다.");
        }

        LocalDate today = LocalDate.now();
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(500, pageSize))
        );
        LocalDate effectiveStartDate = includePast ? startDate : (startDate.isBefore(today) ? today : startDate);
        return new CanonicalRangeRequest(effectiveStartDate, endDate, pageable);
    }

    @Cacheable(value = CacheConfig.GAME_DETAIL, key = "#gameId")
    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public GameDetailDto getGameDetail(String gameId) {
        baseballDataIntegrityGuard.requireValidGame("prediction.game_detail", gameId);
        GameDetailHeaderProjection detailHeader = gameRepository.findGameDetailHeaderByGameId(gameId)
                .orElseThrow(() -> new NotFoundBusinessException(
                        MATCH_NOT_FOUND_CODE,
                        "경기 정보를 찾을 수 없습니다."));

        List<GameInningScoreEntity> rawInningScores = gameInningScoreRepository
                .findAllByGameIdOrderByInningAscTeamSideAsc(gameId);
        List<GameInningScoreEntity> inningScores = filterMeaningfulInningScores(
                gameId,
                rawInningScores,
                detailHeader.getHomeScore(),
                detailHeader.getAwayScore(),
                "game_detail"
        );

        List<GameSummaryEntity> summaries = gameSummaryRepository
                .findAllByGameIdOrderBySummaryTypeAscIdAsc(gameId);
        baseballDataIntegrityGuard.ensurePredictionGameSummaryRecords(
                "prediction.game_detail.summary",
                gameId,
                detailHeader.getGameDate(),
                detailHeader.getGameStatus(),
                detailHeader.getHomeScore(),
                detailHeader.getAwayScore(),
                summaries);

        return Objects.requireNonNull(GameDetailDto.from(detailHeader, inningScores, summaries));
    }

    @Transactional(transactionManager = "kboGameTransactionManager")
    public int upsertInningScores(String gameId, List<GameInningScoreRequestDto> scores) {
        GameEntity game = gameRepository.findByGameId(gameId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기입니다: " + gameId));
        validateInningScoreRequests(scores);

        gameInningScoreRepository.deleteAllByGameId(gameId);

        List<GameInningScoreEntity> entities = scores.stream()
                .map(dto -> GameInningScoreEntity.builder()
                        .gameId(gameId)
                        .inning(dto.getInning())
                        .teamSide(dto.getTeamSide())
                        .teamCode(dto.getTeamCode())
                        .runs(dto.getRuns())
                        .isExtra(GameInningScoreSupport.normalizeIsExtraFlag(dto.getInning(), dto.getIsExtra()))
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        gameInningScoreRepository.saveAll(entities);
        synchronizeGameScoreSnapshot(game, scores);
        log.info("Upserted {} inning score records for gameId={}", entities.size(), gameId);
        return entities.size();
    }

    @Transactional(transactionManager = "kboGameTransactionManager")
    public GameScoreSyncResultDto syncGameSnapshot(String gameId) {
        GameEntity game = gameRepository.findByGameId(gameId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기입니다: " + gameId));
        List<GameInningScoreEntity> rawInningScores = gameInningScoreRepository
                .findAllByGameIdOrderByInningAscTeamSideAsc(gameId);
        List<GameInningScoreEntity> inningScores = filterMeaningfulInningScores(
                gameId,
                rawInningScores,
                game.getHomeScore(),
                game.getAwayScore(),
                "sync_snapshot"
        );

        boolean usedInningScores = !inningScores.isEmpty();
        Integer homeScore;
        if (usedInningScores) {
            homeScore = inningScores.stream()
                    .filter(score -> "home".equalsIgnoreCase(score.getTeamSide()))
                    .mapToInt(score -> score.getRuns() == null ? 0 : score.getRuns())
                    .sum();
        } else {
            homeScore = game.getHomeScore();
        }

        Integer awayScore;
        if (usedInningScores) {
            awayScore = inningScores.stream()
                    .filter(score -> "away".equalsIgnoreCase(score.getTeamSide()))
                    .mapToInt(score -> score.getRuns() == null ? 0 : score.getRuns())
                    .sum();
        } else {
            awayScore = game.getAwayScore();
        }

        boolean canSync = homeScore != null && awayScore != null;
        if (canSync) {
            applyGameScoreSnapshot(game, homeScore, awayScore);
            gameRepository.saveAndFlush(game);
        }

        return new GameScoreSyncResultDto(
                game.getGameId(),
                game.getHomeScore(),
                game.getAwayScore(),
                game.getGameStatus(),
                inningScores.size(),
                canSync,
                usedInningScores,
                game.getWinningTeam(),
                game.getWinningScore()
        );
    }

    @Transactional(transactionManager = "kboGameTransactionManager")
    public GameScoreSyncBatchResultDto syncGameSnapshotsByDateRange(LocalDate startDate, LocalDate endDate) {
        validateSnapshotDateRange(startDate, endDate);

        List<GameEntity> games = gameRepository.findAllByDateRange(startDate, endDate);
        List<GameScoreSyncResultDto> results = games.stream()
                .map(GameEntity::getGameId)
                .map(this::syncGameSnapshot)
                .collect(Collectors.toList());

        int syncedGames = (int) results.stream().filter(GameScoreSyncResultDto::synced).count();
        int skippedGames = results.size() - syncedGames;

        return new GameScoreSyncBatchResultDto(
                startDate,
                endDate,
                results.size(),
                syncedGames,
                skippedGames,
                results
        );
    }

    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public GameStatusMismatchBatchResultDto findGameStatusMismatchesByDateRange(LocalDate startDate, LocalDate endDate) {
        validateSnapshotDateRange(startDate, endDate);

        List<GameEntity> games = gameRepository.findAllByDateRange(startDate, endDate);
        List<GameStatusMismatchDto> mismatches = new ArrayList<>();
        List<NonCanonicalGameDto> nonCanonicalGames = new ArrayList<>();

        for (GameEntity game : games) {
            if (!isCanonicalGame(game)) {
                NonCanonicalGameDto nonCanonicalGame = buildNonCanonicalGame(game);
                if (nonCanonicalGame != null) {
                    nonCanonicalGames.add(nonCanonicalGame);
                }
                continue;
            }

            GameStatusMismatchDto mismatch = buildStatusMismatch(game);
            if (mismatch != null) {
                mismatches.add(mismatch);
            }
        }

        return new GameStatusMismatchBatchResultDto(
                startDate,
                endDate,
                games.size(),
                mismatches.size(),
                mismatches,
                nonCanonicalGames.size(),
                nonCanonicalGames
        );
    }

    @Transactional(transactionManager = "kboGameTransactionManager")
    public GameStatusRepairBatchResultDto repairGameStatusMismatchesByDateRange(
            LocalDate startDate,
            LocalDate endDate,
            boolean dryRun
    ) {
        GameStatusMismatchBatchResultDto mismatchBatch = findGameStatusMismatchesByDateRange(startDate, endDate);
        List<GameScoreSyncResultDto> repairedGames = dryRun
                ? List.of()
                : mismatchBatch.mismatches().stream()
                .map(GameStatusMismatchDto::gameId)
                .map(this::syncGameSnapshot)
                .collect(Collectors.toList());

        return new GameStatusRepairBatchResultDto(
                mismatchBatch.startDate(),
                mismatchBatch.endDate(),
                dryRun,
                mismatchBatch.totalGames(),
                mismatchBatch.mismatchCount(),
                repairedGames.size(),
                mismatchBatch.mismatches(),
                repairedGames,
                mismatchBatch.nonCanonicalCount(),
                mismatchBatch.nonCanonicalGames()
        );
    }

    private NonCanonicalGameDto buildNonCanonicalGame(GameEntity game) {
        if (game == null || game.getGameId() == null) {
            return null;
        }

        List<String> reasons = new ArrayList<>();
        if (!KboTeamCodePolicy.isCanonicalTeamCode(game.getHomeTeam())) {
            reasons.add("non_canonical_home_team");
        }
        if (!KboTeamCodePolicy.isCanonicalTeamCode(game.getAwayTeam())) {
            reasons.add("non_canonical_away_team");
        }

        return new NonCanonicalGameDto(
                game.getGameId(),
                game.getGameDate(),
                resolveGameStartTime(game),
                game.getGameStatus(),
                game.getHomeTeam(),
                game.getAwayTeam(),
                game.getHomeScore(),
                game.getAwayScore(),
                reasons
        );
    }

    private GameStatusMismatchDto buildStatusMismatch(GameEntity game) {
        if (game == null || game.getGameId() == null) {
            return null;
        }

        LocalTime startTime = resolveGameStartTime(game);
        List<GameInningScoreEntity> rawInningScores = gameInningScoreRepository
                .findAllByGameIdOrderByInningAscTeamSideAsc(game.getGameId());
        List<GameInningScoreEntity> inningScores = filterMeaningfulInningScores(
                game.getGameId(),
                rawInningScores,
                game.getHomeScore(),
                game.getAwayScore(),
                "status_mismatch"
        );
        boolean hasKnownScore = game.getHomeScore() != null && game.getAwayScore() != null;
        boolean hasInningScores = !inningScores.isEmpty();
        String normalizedRawStatus = GameStatusResolver.resolveEffectiveStatus(
                game.getGameStatus(),
                game.getGameDate(),
                startTime,
                game.getHomeScore(),
                game.getAwayScore(),
                false
        );
        String effectiveStatus = GameStatusResolver.resolveEffectiveStatus(
                game.getGameStatus(),
                game.getGameDate(),
                startTime,
                game.getHomeScore(),
                game.getAwayScore(),
                hasKnownScore || hasInningScores
        );

        if (Objects.equals(normalizedRawStatus, effectiveStatus)) {
            return null;
        }

        List<String> reasons = new ArrayList<>();
        if (hasKnownScore) {
            reasons.add("score_present");
        }
        if (hasInningScores) {
            reasons.add("inning_scores_present");
        }

        return new GameStatusMismatchDto(
                game.getGameId(),
                game.getGameDate(),
                startTime,
                game.getGameStatus(),
                normalizedRawStatus,
                effectiveStatus,
                game.getHomeScore(),
                game.getAwayScore(),
                inningScores.size(),
                hasKnownScore,
                hasInningScores,
                reasons
        );
    }

    private void validateSnapshotDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("조회 날짜가 올바르지 않습니다.");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다.");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) >= MAX_SNAPSHOT_SYNC_RANGE_DAYS) {
            throw new IllegalArgumentException("한 번에 최대 31일까지만 동기화할 수 있습니다.");
        }
    }

    private void synchronizeGameScoreSnapshot(GameEntity game, List<GameInningScoreRequestDto> scores) {
        if (game == null || scores == null || scores.isEmpty()) {
            return;
        }

        int homeScore = scores.stream()
                .filter(score -> score != null && score.getRuns() != null)
                .filter(score -> "home".equalsIgnoreCase(score.getTeamSide()))
                .mapToInt(GameInningScoreRequestDto::getRuns)
                .sum();
        int awayScore = scores.stream()
                .filter(score -> score != null && score.getRuns() != null)
                .filter(score -> "away".equalsIgnoreCase(score.getTeamSide()))
                .mapToInt(GameInningScoreRequestDto::getRuns)
                .sum();

        applyGameScoreSnapshot(game, homeScore, awayScore);
        gameRepository.saveAndFlush(game);
    }

    private void applyGameScoreSnapshot(GameEntity game, int homeScore, int awayScore) {
        game.setHomeScore(homeScore);
        game.setAwayScore(awayScore);

        String effectiveStatus = GameStatusResolver.resolveSnapshotStatus(
                game.getGameDate(),
                resolveGameStartTime(game),
                homeScore,
                awayScore
        );
        game.setGameStatus(effectiveStatus);

        if ("COMPLETED".equals(effectiveStatus)) {
            game.setWinningTeam(homeScore > awayScore ? game.getHomeTeam() : game.getAwayTeam());
            game.setWinningScore(Math.max(homeScore, awayScore));
            return;
        }

        game.setWinningTeam(null);
        game.setWinningScore(null);
    }

    private LocalTime resolveGameStartTime(GameEntity game) {
        if (game == null || game.getGameId() == null) {
            return null;
        }

        return gameMetadataRepository.findByGameId(game.getGameId())
                .map(GameMetadataEntity::getStartTime)
                .orElse(null);
    }

    private List<GameInningScoreEntity> filterMeaningfulInningScores(
            String gameId,
            List<GameInningScoreEntity> inningScores,
            Integer homeScore,
            Integer awayScore,
            String context
    ) {
        List<GameInningScoreEntity> meaningfulScores = GameInningScoreSupport.normalizeMeaningful(
                inningScores,
                homeScore,
                awayScore
        );
        int rawCount = inningScores == null ? 0 : inningScores.size();
        int filteredCount = Math.max(0, rawCount - meaningfulScores.size());

        if (filteredCount > 0) {
            log.warn(
                    "prediction.inning_scores.filtered context={} gameId={} rawCount={} meaningfulCount={} filteredCount={}",
                    context,
                    gameId,
                    rawCount,
                    meaningfulScores.size(),
                    filteredCount
            );
        }

        return meaningfulScores;
    }

    private void validateInningScoreRequests(List<GameInningScoreRequestDto> scores) {
        if (scores == null || scores.isEmpty()) {
            return;
        }

        boolean hasPlaceholderRow = scores.stream()
                .anyMatch(score -> score != null && score.getRuns() == null);
        if (hasPlaceholderRow) {
            throw new IllegalArgumentException("득점 정보가 없는 이닝은 저장할 수 없습니다.");
        }
    }

    @CacheEvict(value = CacheConfig.PREDICTION_USER_STATS, key = "#userId")
    public void vote(Long userId, PredictionRequestDto request) {
        String gameId = normalizeGameId(request == null ? null : request.getGameId());
        String votedTeam = normalizeVotedTeam(request == null ? null : request.getVotedTeam());

        int attempt = 0;
        while (attempt <= MAX_VOTE_RETRY_ATTEMPTS) {
            attempt++;

            try {
                executeVoteAttempt(userId, gameId, votedTeam);
                return;
            } catch (DataIntegrityViolationException ex) {
                if (!isRetryableVoteConflict(ex)) {
                    throw ex;
                }

                log.warn(
                        "prediction.vote.conflict.retry userId={} gameId={} attempt={} maxAttempts={}",
                        userId,
                        gameId,
                        attempt,
                        MAX_VOTE_RETRY_ATTEMPTS
                );

                if (attempt > MAX_VOTE_RETRY_ATTEMPTS) {
                    log.error(
                            "prediction.vote.conflict.retry userId={} gameId={} result=failed-after-retries attempts={}",
                            userId,
                            gameId,
                            attempt
                    );
                    throw new ConflictBusinessException(
                            "PREDICTION_VOTE_CONFLICT",
                            "예측 처리 중 중복 요청이 충돌했습니다. 잠시 후 다시 시도해주세요.");
                }
            }
        }
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public PredictionResponseDto getVoteStatus(String gameId) {
        String normalizedGameId = normalizeGameId(gameId);
        PredictionResponseDto cachedResponse = getCachedVoteStatus(normalizedGameId);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        Optional<VoteFinalResult> finalResult = voteFinalResultRepository.findById(normalizedGameId);

        if (finalResult.isPresent()) {
            VoteFinalResult result = finalResult.get();
            int finalVotesA = result.getFinalVotesA();
            int finalVotesB = result.getFinalVotesB();
            int totalVotes = finalVotesA + finalVotesB;

            // 저장된 투표수 기반으로 퍼센트 실시간 계산
            int homePercentage = totalVotes > 0 ? (int) Math.round((finalVotesA * 100.0) / totalVotes) : 0;
            int awayPercentage = totalVotes > 0 ? (int) Math.round((finalVotesB * 100.0) / totalVotes) : 0;

            PredictionResponseDto response = Objects.requireNonNull(PredictionResponseDto.builder()
                    .gameId(normalizedGameId)
                    .homeVotes((long) finalVotesA)
                    .awayVotes((long) finalVotesB)
                    .totalVotes((long) totalVotes)
                    .homePercentage(homePercentage)
                    .awayPercentage(awayPercentage)
                    .build());
            cacheVoteStatus(normalizedGameId, response);
            return response;
        }

        PredictionVoteCountsProjection voteCounts = predictionRepository.findVoteCountsByGameId(normalizedGameId);
        Long homeVotes = voteCounts != null && voteCounts.getHomeVotes() != null ? voteCounts.getHomeVotes() : 0L;
        Long awayVotes = voteCounts != null && voteCounts.getAwayVotes() != null ? voteCounts.getAwayVotes() : 0L;
        Long totalVotes = homeVotes + awayVotes;

        int homePercentage = totalVotes > 0 ? (int) Math.round((homeVotes * 100.0) / totalVotes) : 0;
        int awayPercentage = totalVotes > 0 ? (int) Math.round((awayVotes * 100.0) / totalVotes) : 0;

        PredictionResponseDto response = Objects.requireNonNull(PredictionResponseDto.builder()
                .gameId(normalizedGameId)
                .homeVotes(homeVotes)
                .awayVotes(awayVotes)
                .totalVotes(totalVotes)
                .homePercentage(homePercentage)
                .awayPercentage(awayPercentage)
                .build());
        cacheVoteStatus(normalizedGameId, response);
        return response;
    }

    private boolean isCanonicalGame(GameEntity game) {
        if (game == null) {
            return false;
        }
        return KboTeamCodePolicy.isCanonicalTeamCode(game.getHomeTeam())
                && KboTeamCodePolicy.isCanonicalTeamCode(game.getAwayTeam());
    }

    @Transactional(transactionManager = "transactionManager")
    public void cancelVote(Long userId, String gameId) {
        String normalizedGameId = normalizeGameId(gameId);

        GameEntity game = gameRepository.findByGameId(normalizedGameId)
                .orElseThrow(() -> new IllegalArgumentException("경기 정보를 찾을 수 없습니다."));

        validateVoteOpen(game);

        Prediction prediction = predictionRepository
                .findByGameIdAndUserIdForWrite(normalizedGameId, userId)
                .orElseThrow(() -> new IllegalStateException("투표 내역이 없습니다."));

        // 포인트 반환 없음 (No Refund Policy)

        predictionRepository.delete(prediction);
        evictVoteStatusCacheAfterCommit(normalizedGameId);
    }

    private void executeVoteAttempt(Long userId, String gameId, String votedTeam) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.executeWithoutResult(status -> executeVoteAttemptInTransaction(userId, gameId, votedTeam));
    }

    private void executeVoteAttemptInTransaction(Long userId, String gameId, String votedTeam) {
        GameEntity game = gameRepository.findByGameId(gameId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기입니다."));

        if (!isCanonicalGame(game)) {
            throw new IllegalArgumentException("예측 대상이 아닌 경기입니다.");
        }

        validateVoteOpen(game);

        Optional<Prediction> existing = predictionRepository
                .findByGameIdAndUserIdForWrite(gameId, userId);

        if (existing.isPresent()) {
            Prediction prediction = existing.get();

            if (prediction.getVotedTeam().equals(votedTeam)) {
                evictVoteStatusCacheAfterCommit(gameId);
                return;
            }

            prediction.updateVotedTeam(votedTeam);
            evictVoteStatusCacheAfterCommit(gameId);
            return;
        }

        com.example.auth.entity.UserEntity user = userRepository.findByIdForWrite(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

        if (user.getCheerPoints() == null || user.getCheerPoints() < 1) {
            throw new IllegalArgumentException(
                    "응원 포인트가 부족합니다. (현재: " + (user.getCheerPoints() == null ? 0 : user.getCheerPoints()) + ")");
        }

        user.deductCheerPoints(1);

        predictionRepository.saveAndFlush(Prediction.builder()
                .gameId(gameId)
                .userId(userId)
                .votedTeam(votedTeam)
                .build());
        evictVoteStatusCacheAfterCommit(gameId);
    }

    @Transactional(transactionManager = "transactionManager")
    public void saveFinalVoteResult(String gameId) {
        GameEntity game = gameRepository.findByGameId(gameId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기입니다."));

        if (!game.isFinished()) {
            throw new IllegalStateException("종료되지 않은 경기는 최종 결과를 저장할 수 없습니다.");
        }

        PredictionResponseDto currentStatus = getVoteStatus(gameId);

        // Raw Vote Counts 저장 (Long -> int 변환 필요)
        int homeVotes = currentStatus.getHomeVotes().intValue();
        int awayVotes = currentStatus.getAwayVotes().intValue();

        // 승자 판별은 퍼센트 기준이 아닌 득표수 기준으로 해도 됨 (동일 결과)
        String finalWinner = "DRAW";
        if (homeVotes > awayVotes) {
            finalWinner = "HOME";
        } else if (awayVotes > homeVotes) {
            finalWinner = "AWAY";
        }

        VoteFinalResult finalResult = VoteFinalResult.builder()
                .gameId(gameId)
                .finalVotesA(homeVotes)
                .finalVotesB(awayVotes)
                .finalWinner(finalWinner)
                .build();

        voteFinalResultRepository.save(finalResult);
        evictVoteStatusCacheAfterCommit(gameId);
    }

    private PredictionResponseDto getCachedVoteStatus(String gameId) {
        Cache cache = cacheManager.getCache(CacheConfig.PREDICTION_VOTE_STATUS);
        if (cache == null) {
            return null;
        }
        try {
            Cache.ValueWrapper wrapper = cache.get(gameId);
            Object value = wrapper == null ? null : wrapper.get();
            if (value instanceof PredictionVoteStatusCacheEntry entry) {
                return entry.toResponseDto();
            }
            if (value != null) {
                log.warn("예측 투표 상태 캐시 payload 타입이 올바르지 않아 무효화합니다: gameId={}, actualType={}",
                        gameId, value.getClass().getName());
                safeEvictCacheEntry(cache, gameId, CacheConfig.PREDICTION_VOTE_STATUS);
            }
            return null;
        } catch (RuntimeException e) {
            log.warn("예측 투표 상태 캐시 조회 실패. 캐시를 비우고 DB fallback으로 전환합니다: gameId={}, reason={}",
                    gameId, summarizeCacheFailure(e));
            safeEvictCacheEntry(cache, gameId, CacheConfig.PREDICTION_VOTE_STATUS);
            return null;
        }
    }

    private void cacheVoteStatus(String gameId, PredictionResponseDto response) {
        Cache cache = cacheManager.getCache(CacheConfig.PREDICTION_VOTE_STATUS);
        if (cache != null && response != null) {
            try {
                cache.put(gameId, PredictionVoteStatusCacheEntry.from(response));
            } catch (RuntimeException e) {
                log.warn("예측 투표 상태 캐시 저장 실패. 응답은 계속 진행합니다: gameId={}, reason={}",
                        gameId, summarizeCacheFailure(e));
                safeEvictCacheEntry(cache, gameId, CacheConfig.PREDICTION_VOTE_STATUS);
            }
        }
    }

    private void evictVoteStatusCacheAfterCommit(String gameId) {
        String normalizedGameId = normalizeGameId(gameId);
        Cache cache = cacheManager.getCache(CacheConfig.PREDICTION_VOTE_STATUS);
        if (cache == null) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.evict(normalizedGameId);
                    // Re-warm cache immediately to prevent thundering herd on popular games
                    try {
                        PredictionVoteCountsProjection voteCounts =
                                predictionRepository.findVoteCountsByGameId(normalizedGameId);
                        Long homeVotes = voteCounts != null && voteCounts.getHomeVotes() != null
                                ? voteCounts.getHomeVotes() : 0L;
                        Long awayVotes = voteCounts != null && voteCounts.getAwayVotes() != null
                                ? voteCounts.getAwayVotes() : 0L;
                        Long totalVotes = homeVotes + awayVotes;
                        int homePercentage = totalVotes > 0
                                ? (int) Math.round((homeVotes * 100.0) / totalVotes) : 0;
                        int awayPercentage = totalVotes > 0
                                ? (int) Math.round((awayVotes * 100.0) / totalVotes) : 0;
                        PredictionResponseDto warmed = PredictionResponseDto.builder()
                                .gameId(normalizedGameId)
                                .homeVotes(homeVotes)
                                .awayVotes(awayVotes)
                                .totalVotes(totalVotes)
                                .homePercentage(homePercentage)
                                .awayPercentage(awayPercentage)
                                .build();
                        cacheVoteStatus(normalizedGameId, warmed);
                    } catch (Exception e) {
                        log.debug("투표 현황 캐시 워밍 실패. 다음 요청 시 DB에서 조회됩니다: gameId={}",
                                normalizedGameId);
                    }
                }
            });
            return;
        }

        cache.evict(normalizedGameId);
    }

    private String summarizeCacheFailure(RuntimeException exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        String message = rootCause.getMessage();
        if (message == null || message.isBlank()) {
            message = rootCause.getClass().getSimpleName();
        }

        String normalized = message.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 220) {
            return normalized.substring(0, 220) + "...";
        }
        return normalized;
    }

    private void safeEvictCacheEntry(Cache cache, Object key, String cacheName) {
        if (cache == null || key == null) {
            return;
        }
        try {
            cache.evict(key);
        } catch (RuntimeException e) {
            log.warn("캐시 엔트리 무효화 실패: cache={}, key={}, reason={}",
                    cacheName, key, summarizeCacheFailure(e));
        }
    }

    private void validateVoteOpen(GameEntity game) {
        // 1. 경기 상태 체크 (차단 상태면 투표 불가)
        String status = game.getGameStatus();
        if (status != null) {
            String normalizedStatus = status.trim().toUpperCase();
            if (BLOCKED_VOTE_STATUSES.contains(normalizedStatus)) {
                throw new IllegalArgumentException("이미 진행 중이거나 종료된 경기(상태: " + status + ")는 투표할 수 없습니다.");
            }
        }

        // 2. 시간 기반 체크 (경기 시작 시간 이후 투표 불가)
        Optional<GameMetadataEntity> metadataOpt = gameMetadataRepository.findByGameId(game.getGameId());
        LocalDate gameDate = game.getGameDate();

        if (gameDate == null) {
            // 날짜 정보가 없으면 보수적으로 차단 (데이터 이상)
            throw new IllegalArgumentException("경기 날짜 정보가 없어 투표를 진행할 수 없습니다.");
        }

        if (metadataOpt.isEmpty() || metadataOpt.get().getStartTime() == null) {
            throw new IllegalArgumentException("경기 시작 시간 정보가 없어 투표할 수 없습니다.");
        }

        LocalDateTime startDateTime = LocalDateTime.of(gameDate, metadataOpt.get().getStartTime());
        if (!LocalDateTime.now().isBefore(startDateTime)) {
            throw new IllegalArgumentException("이미 시작된 경기는 투표할 수 없습니다.");
        }
    }

    private String normalizeGameId(String gameId) {
        String normalized = gameId == null ? "" : gameId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("게임 ID가 필요합니다.");
        }
        if (!GAME_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("게임 ID 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String normalizeVotedTeam(String votedTeam) {
        String normalized = votedTeam == null ? "" : votedTeam.trim().toLowerCase();
        if (!normalized.equals("home") && !normalized.equals("away")) {
            throw new IllegalArgumentException("투표 팀은 home 또는 away만 가능합니다.");
        }
        return normalized;
    }

    private boolean isRetryableVoteConflict(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        String normalizedMessage = message == null ? "" : message.toLowerCase();

        return normalizedMessage.contains("uq_predictions_user_game")
                || normalizedMessage.contains("uk_predictions_user_game")
                || normalizedMessage.contains("predictions_user_id_game_id")
                || normalizedMessage.contains("duplicate key");
    }

    /**
     * 사용자의 예측 적중률/스트릭 집계.
     * 사용자별 누적 예측이 수천 건에 이를 수 있어 결과를 5분간 Redis(PREDICTION_USER_STATS)에 캐시한다.
     * 새 투표 발생 시 vote() 종료 시점에 동일 키를 evict 한다.
     */
    @Cacheable(value = CacheConfig.PREDICTION_USER_STATS, key = "#userId", unless = "#result == null")
    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public UserPredictionStatsDto getUserStats(Long userId) {
        List<PredictionStatsRowProjection> predictions = predictionRepository
                .findStatsRowsByUserIdOrderByCreatedAtDesc(java.util.Objects.requireNonNull(userId));
        if (predictions.isEmpty()) {
            return Objects.requireNonNull(
                    UserPredictionStatsDto.builder()
                            .totalPredictions(0)
                            .correctPredictions(0)
                            .accuracy(0.0)
                            .streak(0)
                            .build());
        }

        List<String> gameIds = predictions.stream()
                .map(PredictionStatsRowProjection::getGameId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<PredictionStatsGameProjection> loadedGames = gameIds.isEmpty()
                ? List.of()
                : gameRepository.findPredictionStatsGameSummaries(gameIds, QUERYABLE_TEAM_CODES);
        Map<String, String> winnerByGameId = loadedGames
                .stream()
                .collect(Collectors.toMap(PredictionStatsGameProjection::getGameId, PredictionStatsGameProjection::getWinner));

        // 집계 루프 (전체 순회, 총 완료 + 정답 수)
        int totalFinished = 0;
        int correctCount = 0;
        for (PredictionStatsRowProjection prediction : predictions) {
            String actualWinner = winnerByGameId.get(prediction.getGameId());
            if (actualWinner == null) continue;
            totalFinished++;
            if (prediction.getVotedTeam().equalsIgnoreCase(actualWinner)) {
                correctCount++;
            }
        }

        // 스트릭 루프 (최근 50개 완료 경기만, 최신순으로 정렬됨)
        int currentStreak = 0;
        int finishedForStreak = 0;
        for (PredictionStatsRowProjection prediction : predictions) {
            if (finishedForStreak >= 50) break;
            String actualWinner = winnerByGameId.get(prediction.getGameId());
            if (actualWinner == null) continue;
            finishedForStreak++;
            if (prediction.getVotedTeam().equalsIgnoreCase(actualWinner)) {
                currentStreak++;
            } else {
                break;
            }
        }

        double accuracy = totalFinished > 0 ? Math.round((correctCount * 100.0 / totalFinished) * 10.0) / 10.0 : 0.0;

        return Objects.requireNonNull(
                UserPredictionStatsDto.builder().totalPredictions(totalFinished).correctPredictions(correctCount)
                        .accuracy(accuracy).streak(currentStreak).build());
    }

    private record CanonicalRangeRequest(
            LocalDate effectiveStartDate,
            LocalDate effectiveEndDate,
            Pageable pageable) {
    }
}
