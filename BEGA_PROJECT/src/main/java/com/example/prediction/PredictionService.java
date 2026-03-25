package com.example.prediction;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameInningScoreEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.entity.GameSummaryEntity;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.GameInningScoreRepository;
import com.example.kbo.repository.GameMetadataRepository;
import com.example.kbo.repository.GameSummaryRepository;
import com.example.kbo.service.LeagueStageResolver;
import com.example.kbo.util.KboTeamCodePolicy;
import com.example.kbo.util.TeamCodeResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
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

        List<GameEntity> matches = gameRepository.findAllByGameDatesIn(recentDates);

        return Objects.requireNonNull(matches.stream()
                .filter(this::isCanonicalGame)
                .map(this::toMatchDto)
                .collect(Collectors.toList()));
    }

    // 특정 날짜의 경기 조회 (실제 DB 데이터만 조회, 더미 및 Mock 제외)
    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public List<MatchDto> getMatchesByDate(LocalDate date) {
        List<GameEntity> matches = gameRepository.findByGameDate(date).stream()
                .filter(game -> !Boolean.TRUE.equals(game.getIsDummy()))
                .filter(game -> !game.getGameId().startsWith("MOCK"))
                .filter(this::isCanonicalGame)
                .collect(Collectors.toList());

        return Objects.requireNonNull(matches.stream()
                .map(this::toMatchDto)
                .collect(Collectors.toList()));
    }

    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public MatchDayNavigationResponseDto getMatchDayNavigation(LocalDate date) {
        LocalDate targetDate = Objects.requireNonNull(date, "조회 날짜가 올바르지 않습니다.");
        List<MatchDto> matches = getMatchesByDate(targetDate);
        Optional<LocalDate> prevDate = gameRepository.findCanonicalPrevGameDate(targetDate, QUERYABLE_TEAM_CODES);
        Optional<LocalDate> nextDate = gameRepository.findCanonicalNextGameDate(targetDate, QUERYABLE_TEAM_CODES);

        return new MatchDayNavigationResponseDto(
                targetDate,
                matches,
                prevDate.orElse(null),
                nextDate.orElse(null),
                prevDate.isPresent(),
                nextDate.isPresent()
        );
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
        Page<GameEntity> matches = getCanonicalMatchRangePage(startDate, endDate, includePast, page, pageSize);
        Map<String, Integer> seriesGameNos = resolveSeriesGameNos(matches.getContent());

        return Objects.requireNonNull(matches.getContent().stream()
                .map(match -> toMatchDto(match, seriesGameNos.get(match.getGameId())))
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
        Page<GameEntity> matches = getCanonicalMatchRangePage(startDate, endDate, includePast, page, pageSize);
        Map<String, Integer> seriesGameNos = resolveSeriesGameNos(matches.getContent());

        return new MatchRangePageResponseDto(
                matches.getContent().stream()
                        .map(match -> toMatchDto(match, seriesGameNos.get(match.getGameId())))
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
        Optional<LocalDate> earliestGameDate = gameRepository.findCanonicalMinGameDate(QUERYABLE_TEAM_CODES);
        Optional<LocalDate> latestGameDate = gameRepository.findCanonicalMaxGameDate(QUERYABLE_TEAM_CODES);
        boolean hasData = earliestGameDate.isPresent() && latestGameDate.isPresent();

        return new MatchBoundsResponseDto(
                hasData ? earliestGameDate.get() : null,
                hasData ? latestGameDate.get() : null,
                hasData
        );
    }

    private MatchDto toMatchDto(GameEntity game) {
        Integer leagueTypeCode = leagueStageResolver.resolveEffectiveLeagueTypeCode(game);
        return toMatchDto(game, resolveSeriesGameNo(game, leagueTypeCode));
    }

    private MatchDto toMatchDto(GameEntity game, Integer seriesGameNo) {
        Integer leagueTypeCode = leagueStageResolver.resolveEffectiveLeagueTypeCode(game);
        return MatchDto.builder()
                .gameId(game.getGameId())
                .gameDate(game.getGameDate())
                .homeTeam(com.example.kbo.util.TeamCodeNormalizer.normalize(game.getHomeTeam()))
                .awayTeam(com.example.kbo.util.TeamCodeNormalizer.normalize(game.getAwayTeam()))
                .stadium(game.getStadium())
                .homeScore(game.getHomeScore())
                .awayScore(game.getAwayScore())
                .winner(game.getWinner())
                .isDummy(game.getIsDummy())
                .homePitcher(MatchDto.pitcherOf(game.getHomePitcher()))
                .awayPitcher(MatchDto.pitcherOf(game.getAwayPitcher()))
                .aiSummary(null)
                .winProbability(null)
                .seasonId(game.getSeasonId())
                .leagueType(mapLeagueType(leagueTypeCode))
                .postSeasonSeries(mapPostSeasonSeries(leagueTypeCode))
                .seriesGameNo(seriesGameNo)
                .build();
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

    private Integer resolveSeriesGameNo(GameEntity game, Integer leagueTypeCode) {
        if (leagueTypeCode == null || leagueTypeCode < 2 || leagueTypeCode > 5) {
            return null;
        }
        if (game.getSeasonId() == null || game.getGameDate() == null || game.getGameId() == null) {
            return null;
        }
        long previousGames = gameRepository.countPreviousCompletedSeriesGames(
                game.getSeasonId(),
                game.getHomeTeam(),
                game.getAwayTeam(),
                game.getGameDate(),
                game.getGameId()
        );
        return Math.toIntExact(previousGames + 1L);
    }

    private Map<String, Integer> resolveSeriesGameNos(List<GameEntity> games) {
        if (games == null || games.isEmpty()) {
            return Map.of();
        }

        Map<SeriesGameKey, SeriesAnchor> anchorsByKey = new LinkedHashMap<>();
        for (GameEntity game : games) {
            SeriesGameKey key = toSeriesGameKey(game);
            if (key == null || anchorsByKey.containsKey(key)) {
                continue;
            }
            anchorsByKey.put(key, new SeriesAnchor(game.getGameDate(), game.getGameId()));
        }

        if (anchorsByKey.isEmpty()) {
            return Map.of();
        }

        Map<SeriesGameKey, Long> baseCountsByKey = new HashMap<>();
        for (Map.Entry<SeriesGameKey, SeriesAnchor> entry : anchorsByKey.entrySet()) {
            SeriesGameKey key = entry.getKey();
            SeriesAnchor anchor = entry.getValue();
            long previousGames = gameRepository.countPreviousCompletedSeriesGames(
                    key.seasonId(),
                    key.homeTeam(),
                    key.awayTeam(),
                    anchor.gameDate(),
                    anchor.gameId());
            baseCountsByKey.put(key, previousGames);
        }

        Map<SeriesGameKey, Long> runningCountsByKey = new HashMap<>();
        Map<String, Integer> seriesGameNosByGameId = new HashMap<>();
        for (GameEntity game : games) {
            SeriesGameKey key = toSeriesGameKey(game);
            if (key == null) {
                continue;
            }

            long baseCount = baseCountsByKey.getOrDefault(key, 0L);
            long seriesGameNo = runningCountsByKey.merge(key, 1L, Long::sum) + baseCount;
            seriesGameNosByGameId.put(game.getGameId(), Math.toIntExact(seriesGameNo));
        }

        return seriesGameNosByGameId;
    }

    private SeriesGameKey toSeriesGameKey(GameEntity game) {
        Integer leagueTypeCode = leagueStageResolver.resolveEffectiveLeagueTypeCode(game);
        if (leagueTypeCode == null || leagueTypeCode < 2 || leagueTypeCode > 5) {
            return null;
        }
        if (game.getSeasonId() == null || game.getGameDate() == null || game.getGameId() == null) {
            return null;
        }

        String homeTeam = normalizeSeriesTeamCode(game.getHomeTeam());
        String awayTeam = normalizeSeriesTeamCode(game.getAwayTeam());
        if (homeTeam == null || awayTeam == null) {
            return null;
        }

        if (homeTeam.compareTo(awayTeam) <= 0) {
            return new SeriesGameKey(game.getSeasonId(), homeTeam, awayTeam);
        }
        return new SeriesGameKey(game.getSeasonId(), awayTeam, homeTeam);
    }

    private String normalizeSeriesTeamCode(String teamCode) {
        if (teamCode == null) {
            return null;
        }
        String normalized = teamCode.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private Page<GameEntity> getCanonicalMatchRangePage(
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

        LocalDate effectiveEndDate = endDate;
        LocalDate effectiveStartDate = includePast ? startDate : (startDate.isBefore(today) ? today : startDate);

        if (effectiveStartDate.isAfter(effectiveEndDate)) {
            log.info(
                    "prediction.range.end_reached includePast={} requestedWindow={}~{} effectiveWindow={}~{} page={} size={} reason=effective-window-empty",
                    includePast,
                    startDate,
                    endDate,
                    effectiveStartDate,
                    effectiveEndDate,
                    pageable.getPageNumber(),
                    pageable.getPageSize()
            );
            return Page.empty(pageable);
        }

        Page<GameEntity> rangePage = gameRepository.findCanonicalByDateRange(
                effectiveStartDate,
                effectiveEndDate,
                QUERYABLE_TEAM_CODES,
                pageable);

        if (rangePage.isEmpty()) {
            log.info(
                    "prediction.range.end_reached includePast={} requestedWindow={}~{} effectiveWindow={}~{} page={} size={} reason=no-content",
                    includePast,
                    startDate,
                    endDate,
                    effectiveStartDate,
                    effectiveEndDate,
                    rangePage.getNumber(),
                    rangePage.getSize()
            );
        } else {
            log.info(
                    "prediction.range.load includePast={} result=success window={}~{} page={} size={} content={} hasNext={} hasPrevious={}",
                    includePast,
                    effectiveStartDate,
                    effectiveEndDate,
                    rangePage.getNumber(),
                    rangePage.getSize(),
                    rangePage.getNumberOfElements(),
                    rangePage.hasNext(),
                    rangePage.hasPrevious()
            );
        }

        return rangePage;
    }

    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public GameDetailDto getGameDetail(String gameId) {
        GameEntity game = gameRepository.findByGameId(gameId)
                .orElseThrow(() -> new IllegalArgumentException("경기 정보를 찾을 수 없습니다."));

        GameMetadataEntity metadata = gameMetadataRepository.findByGameId(gameId)
                .orElse(null);

        List<GameInningScoreEntity> inningScores = gameInningScoreRepository
                .findAllByGameIdOrderByInningAscTeamSideAsc(gameId);

        List<GameSummaryEntity> summaries = gameSummaryRepository
                .findAllByGameIdOrderBySummaryTypeAscIdAsc(gameId);

        return Objects.requireNonNull(GameDetailDto.from(game, metadata, inningScores, summaries));
    }

    @Transactional(transactionManager = "kboGameTransactionManager")
    public int upsertInningScores(String gameId, List<GameInningScoreRequestDto> scores) {
        gameRepository.findByGameId(gameId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기입니다: " + gameId));

        gameInningScoreRepository.deleteAllByGameId(gameId);

        List<GameInningScoreEntity> entities = scores.stream()
                .map(dto -> GameInningScoreEntity.builder()
                        .gameId(gameId)
                        .inning(dto.getInning())
                        .teamSide(dto.getTeamSide())
                        .teamCode(dto.getTeamCode())
                        .runs(dto.getRuns())
                        .isExtra(dto.getIsExtra())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        gameInningScoreRepository.saveAll(entities);
        log.info("Upserted {} inning score records for gameId={}", entities.size(), gameId);
        return entities.size();
    }

    @Transactional(transactionManager = "transactionManager")
    public void vote(Long userId, PredictionRequestDto request) {
        String gameId = normalizeGameId(request == null ? null : request.getGameId());
        String votedTeam = normalizeVotedTeam(request == null ? null : request.getVotedTeam());

        int attempt = 0;
        while (attempt <= MAX_VOTE_RETRY_ATTEMPTS) {
            attempt++;

            try {
                GameEntity game = gameRepository.findByGameId(gameId)
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기입니다."));

                if (!isCanonicalGame(game)) {
                    throw new IllegalArgumentException("예측 대상이 아닌 경기입니다.");
                }

                validateVoteOpen(game);

                com.example.auth.entity.UserEntity user = userRepository.findByIdForWrite(userId)
                        .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

                Optional<Prediction> existing = predictionRepository
                        .findByGameIdAndUserIdForWrite(gameId, userId);

                if (existing.isPresent()) {
                    Prediction prediction = existing.get();

                    if (prediction.getVotedTeam().equals(votedTeam)) {
                        if (attempt > 1) {
                            return;
                        }

                        predictionRepository.delete(prediction);
                        return;
                    }

                    prediction.updateVotedTeam(votedTeam);
                    return;
                }

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
                    throw new IllegalStateException("예측 처리 중 중복 요청이 충돌했습니다. 잠시 후 다시 시도해주세요.");
                }
            }
        }
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public PredictionResponseDto getVoteStatus(String gameId) {
        Optional<VoteFinalResult> finalResult = voteFinalResultRepository.findById(gameId);

        if (finalResult.isPresent()) {
            VoteFinalResult result = finalResult.get();
            int finalVotesA = result.getFinalVotesA();
            int finalVotesB = result.getFinalVotesB();
            int totalVotes = finalVotesA + finalVotesB;

            // 저장된 투표수 기반으로 퍼센트 실시간 계산
            int homePercentage = totalVotes > 0 ? (int) Math.round((finalVotesA * 100.0) / totalVotes) : 0;
            int awayPercentage = totalVotes > 0 ? (int) Math.round((finalVotesB * 100.0) / totalVotes) : 0;

            return Objects.requireNonNull(PredictionResponseDto.builder()
                    .gameId(gameId)
                    .homeVotes((long) finalVotesA)
                    .awayVotes((long) finalVotesB)
                    .totalVotes((long) totalVotes)
                    .homePercentage(homePercentage)
                    .awayPercentage(awayPercentage)
                    .build());
        }

        Long homeVotes = predictionRepository.countByGameIdAndVotedTeam(gameId, "home");
        Long awayVotes = predictionRepository.countByGameIdAndVotedTeam(gameId, "away");
        Long totalVotes = homeVotes + awayVotes;

        int homePercentage = totalVotes > 0 ? (int) Math.round((homeVotes * 100.0) / totalVotes) : 0;
        int awayPercentage = totalVotes > 0 ? (int) Math.round((awayVotes * 100.0) / totalVotes) : 0;

        return Objects.requireNonNull(PredictionResponseDto.builder()
                .gameId(gameId)
                .homeVotes(homeVotes)
                .awayVotes(awayVotes)
                .totalVotes(totalVotes)
                .homePercentage(homePercentage)
                .awayPercentage(awayPercentage)
                .build());
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

    private record SeriesGameKey(Integer seasonId, String homeTeam, String awayTeam) {
    }

    private record SeriesAnchor(LocalDate gameDate, String gameId) {
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

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public UserPredictionStatsDto getUserStats(Long userId) {
        List<Prediction> predictions = predictionRepository
                .findAllByUserIdOrderByCreatedAtDesc(java.util.Objects.requireNonNull(userId));
        List<String> gameIds = predictions.stream()
                .map(Prediction::getGameId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<GameEntity> loadedGames = gameIds.isEmpty() ? List.of() : gameRepository.findByGameIdIn(gameIds);
        Map<String, GameEntity> gamesById = loadedGames
                .stream()
                .collect(Collectors.toMap(GameEntity::getGameId, game -> game));

        int totalFinished = 0;
        int correctCount = 0;
        int currentStreak = 0;
        boolean streakBroken = false;

        for (Prediction prediction : predictions) {
            GameEntity game = gamesById.get(prediction.getGameId());
            if (game == null || !isCanonicalGame(game)) {
                continue;
            }
            if (game.isFinished()) {
                totalFinished++;
                String actualWinner = game.getWinner(); // "home", "away", or "draw"
                boolean isCorrect = prediction.getVotedTeam().equalsIgnoreCase(actualWinner);

                if (isCorrect) {
                    correctCount++;
                    if (!streakBroken) {
                        currentStreak++;
                    }
                } else {
                    // 결과가 나왔는데 틀린 경우 streak 종료
                    streakBroken = true;
                }
            }
            // 경기가 아직 안 끝났으면 streak 계산에는 영향을 주지 않고 건너뜀 (최신순이므로)
        }

        double accuracy = totalFinished > 0 ? Math.round((correctCount * 100.0 / totalFinished) * 10.0) / 10.0 : 0.0;

        return Objects.requireNonNull(
                UserPredictionStatsDto.builder().totalPredictions(totalFinished).correctPredictions(correctCount)
                        .accuracy(accuracy).streak(currentStreak).build());
    }
}
