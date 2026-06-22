package com.example.homepage;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.MatchRangeProjection;
import com.example.kbo.service.LeagueStageResolver;
import com.example.kbo.validation.BaseballDataIntegrityGuard;
import com.example.kbo.util.GameStatusResolver;
import com.example.kbo.util.TeamCodeNormalizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;

import static com.example.common.config.CacheConfig.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class HomePageGameService {

    private static final String DEFAULT_GAME_TIME = "18:30";
    private static final DateTimeFormatter GAME_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<String> CANONICAL_TEAMS = List.of("SS", "LT", "LG", "DB", "KIA", "KH", "HH", "SSG", "NC", "KT");
    private static final String SCOPE_POSTSEASON = "postseason";
    private static final String SCOPE_KOREAN_SERIES = "koreanseries";
    private static final String SCOPE_SCHEDULED = "scheduled";
    private static final List<Integer> REGULAR_LEAGUE_CODES = List.of(0);
    private static final List<Integer> POSTSEASON_LEAGUE_CODES = List.of(2, 3, 4);
    private static final List<Integer> KOREAN_SERIES_LEAGUE_CODES = List.of(5);
    private static final List<String> SCHEDULED_NAVIGATION_STATUSES = List.of(
            "SCHEDULED",
            "READY",
            "UPCOMING",
            "NOT_STARTED",
            "PRE_GAME",
            "BEFORE_GAME");
    private static final List<String> SCHEDULED_WINDOW_STATUSES = List.of(
            "SCHEDULED",
            "READY",
            "UPCOMING",
            "NOT_STARTED",
            "PRE_GAME",
            "BEFORE_GAME",
            "POSTPONED",
            "CANCELLED",
            "CANCEL");
    private static final List<String> STANDARD_NAVIGATION_EXCLUDED_STATUSES = SCHEDULED_WINDOW_STATUSES;

    private final GameRepository gameRepository;
	private final HomePageTeamRepository homePageTeamRepository;
    @Qualifier("stadiumDataSource")
    private final DataSource stadiumDataSource;
    private final LeagueStageResolver leagueStageResolver;
    private final BaseballDataIntegrityGuard baseballDataIntegrityGuard;

	private final Map<String, HomePageTeam> teamMap = new ConcurrentHashMap<>();

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public void init() {
        try {
            homePageTeamRepository.findAll().forEach(team -> teamMap.put(team.getTeamId(), team));
        } catch (Exception e) {
            // 에러 발생해도 애플리케이션은 계속 실행
        }
    }

    private HomePageTeam getTeam(String teamId) {
        if (teamMap.isEmpty()) {
            init();
        }
        return teamMap.getOrDefault(teamId, new HomePageTeam());
    }

    public String buildScheduledGamesWindowCacheKey(LocalDate startDate, LocalDate endDate) {
        return "scheduledWindow:" + startDate + ":" + endDate;
    }

    public String buildScheduleNavigationCacheKey(LocalDate date) {
        return "navigation:" + date;
    }

    public String buildScopedNavigationCacheKey(LocalDate date, String scope, Integer seasonYear) {
        LocalDate anchorDate = date == null ? LocalDate.now() : date;
        String normalizedScope = normalizeNavigationScope(scope);
        String normalizedSeasonYear = seasonYear == null ? "auto" : seasonYear.toString();
        return "scopedNavigation:" + anchorDate + ":" + normalizedScope + ":" + normalizedSeasonYear;
    }

    @Cacheable(value = GAME_SCHEDULE, key = "#date.toString()")
    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public List<HomePageGameDto> getGamesByDate(LocalDate date) {
        List<MatchRangeProjection> games = gameRepository.findCanonicalRangeProjectionByGameDate(date, CANONICAL_TEAMS);
        if (games.isEmpty()) {
            List<MatchRangeProjection> jdbcFallbackGames = findGameProjectionsByDateWithJdbc(date);
            if (!jdbcFallbackGames.isEmpty()) {
                log.warn("GameRepository returned empty list but JDBC fallback found {} rows for date={}",
                        jdbcFallbackGames.size(),
                        date);
                games = jdbcFallbackGames;
            }
        }
        if (games.isEmpty()) {
            return List.of();
        }
        baseballDataIntegrityGuard.ensurePredictionDateMatches("home.schedule", date, games);

        return games.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Cacheable(value = GAME_SCHEDULE, key = "#root.target.buildScheduledGamesWindowCacheKey(#startDate, #endDate)")
    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public List<HomePageScheduledGameDto> getScheduledGamesWindow(LocalDate startDate, LocalDate endDate) {
        List<MatchRangeProjection> games = gameRepository.findScheduledWindowProjectionByDateRange(
                startDate,
                endDate,
                SCHEDULED_WINDOW_STATUSES,
                CANONICAL_TEAMS);
        if (games.isEmpty()) {
            return List.of();
        }
        baseballDataIntegrityGuard.ensurePredictionRangeMatches("home.scheduled_window", games);
        return games.stream()
                .map(game -> {
                    HomePageGameDto baseDto = convertToDto(game);
                    return HomePageScheduledGameDto.builder()
                            .gameId(baseDto.getGameId())
                            .time(baseDto.getTime())
                            .stadium(baseDto.getStadium())
                            .gameStatus(baseDto.getGameStatus())
                            .gameStatusKr(baseDto.getGameStatusKr())
                            .gameInfo(baseDto.getGameInfo())
                            .leagueType(baseDto.getLeagueType())
                            .homeTeam(baseDto.getHomeTeam())
                            .homeTeamFull(baseDto.getHomeTeamFull())
                            .awayTeam(baseDto.getAwayTeam())
                            .awayTeamFull(baseDto.getAwayTeamFull())
                            .homeScore(baseDto.getHomeScore())
                            .awayScore(baseDto.getAwayScore())
                            .sourceDate(game.getGameDate() == null ? null : game.getGameDate().toString())
                            .leagueBadge(resolveLeagueBadge(baseDto.getLeagueType()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private HomePageGameDto convertToDto(MatchRangeProjection game) {
		HomePageTeam homeTeam = getTeam(game.getHomeTeam());
		HomePageTeam awayTeam = getTeam(game.getAwayTeam());
        String resolvedHomeTeamId = resolveTeamId(game.getHomeTeam(), homeTeam.getTeamId());
        String resolvedAwayTeamId = resolveTeamId(game.getAwayTeam(), awayTeam.getTeamId());
        String resolvedHomeTeamName = resolveTeamName(resolvedHomeTeamId, homeTeam.getTeamName());
        String resolvedAwayTeamName = resolveTeamName(resolvedAwayTeamId, awayTeam.getTeamName());

		String leagueType = determineLeagueType(game);
		String gameInfo = "";

		if ("KOREAN_SERIES".equals(leagueType)) {
			gameInfo = "한국시리즈";
		} else if ("POSTSEASON".equals(leagueType)) {
			gameInfo = "포스트시즌";
		}

        Integer homeScore = game.getHomeScore();
        Integer awayScore = game.getAwayScore();
        boolean hasKnownScore = homeScore != null && awayScore != null;
        String effectiveGameStatus = GameStatusResolver.resolveEffectiveStatus(
                game.getGameStatus(),
                game.getGameDate(),
                game.getStartTime(),
                homeScore,
                awayScore,
                hasKnownScore
        );
        String gameStatusKr = convertGameStatus(effectiveGameStatus);

        return HomePageGameDto.builder()
                .gameId(game.getGameId())
                .gameDate(game.getGameDate() == null ? null : game.getGameDate().toString())
                .sourceDate(game.getGameDate() == null ? null : game.getGameDate().toString())
                .stadium(game.getStadium())
                .gameStatus(effectiveGameStatus)
                .gameStatusKr(gameStatusKr)
                .homeTeam(resolvedHomeTeamId)
                .homeTeamFull(resolvedHomeTeamName)
                .awayTeam(resolvedAwayTeamId)
                .awayTeamFull(resolvedAwayTeamName)
                .homeScore(homeScore)
                .awayScore(awayScore)
                .time(formatGameTime(game.getStartTime()))
                .leagueType(leagueType)
                .gameInfo(gameInfo)
                .build();
    }

    private String formatGameTime(LocalTime startTime) {
        if (startTime == null) {
            return DEFAULT_GAME_TIME;
        }
        return startTime.format(GAME_TIME_FORMATTER);
    }

    private String convertGameStatus(String status) {
        if (status == null)
            return "정보 없음";

        switch (status) {
            case "SCHEDULED":
                return "경기 예정";
            case "LIVE":
                return "경기 진행중";
            case "COMPLETED":
                return "경기 종료";
            case "CANCELLED":
                return "경기 취소";
            case "POSTPONED":
                return "경기 연기";
            case "DRAW":
                return "무승부";
            default:
                return status;
        }
    }

    private String resolveLeagueBadge(String leagueType) {
        if (leagueType == null) {
            return "예정 일정";
        }

        return switch (leagueType) {
            case "REGULAR" -> "정규시즌";
            case "POSTSEASON" -> "포스트시즌";
            case "KOREAN_SERIES" -> "한국시리즈";
            case "PRE", "PRESEASON" -> "프리시즌";
            case "OFFSEASON" -> "기타 일정";
            default -> "예정 일정";
        };
    }

	private String determineLeagueType(MatchRangeProjection game) {
		Integer leagueTypeCode = leagueStageResolver.resolveEffectiveLeagueTypeCode(
                game.getRawLeagueTypeCode(),
                game.getGameDate(),
                game.getSeasonId(),
                game.getGameId());
		if (leagueTypeCode == null
				&& game != null
				&& game.getSeasonId() != null
				&& game.getGameDate() != null
				&& game.getSeasonId().equals(game.getGameDate().getYear())) {
			leagueTypeCode = 0;
		}
		if (leagueTypeCode == null) {
			return "OFFSEASON";
		}

		return switch (leagueTypeCode) {
			case 0 -> "REGULAR";
			case 2, 3, 4 -> "POSTSEASON";
			case 5 -> "KOREAN_SERIES";
			default -> "OFFSEASON";
		};
	}

    @Cacheable(value = TEAM_RANKINGS, key = "#seasonYear", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public List<HomePageTeamRankingDto> getTeamRankings(int seasonYear) {
        LocalDate seasonStart = LocalDate.of(seasonYear, 1, 1);
        LocalDate nextSeasonStart = LocalDate.of(seasonYear + 1, 1, 1);
        long fastStartedAt = System.nanoTime();
        List<Object[]> results = gameRepository.findTeamRankingsBySeasonFast(
                seasonYear,
                seasonStart,
                nextSeasonStart);
        logTeamRankingQueryCompleted(seasonYear, "fast", results.size(), fastStartedAt);
        if (results.isEmpty()) {
            long legacyStartedAt = System.nanoTime();
            results = gameRepository.findTeamRankingsBySeasonFallback(seasonYear);
            logTeamRankingQueryCompleted(seasonYear, "legacy", results.size(), legacyStartedAt);
        }

        return results.stream()
                .map(row -> HomePageTeamRankingDto.builder()
                        .rank(((Number) row[0]).intValue()) // season_rank (bigint)
                        .teamId((String) row[1]) // team_id
                        .teamName((String) row[2]) // team_name
                        .wins(((Number) row[3]).intValue()) // wins (bigint)
                        .losses(((Number) row[4]).intValue()) // losses (bigint)
                        .draws(((Number) row[5]).intValue()) // draws (bigint)
                        .winRate(row[6].toString()) // win_pct (numeric)
                        .games(((Number) row[7]).intValue()) // games_played (bigint)
                        .gamesBehind(row[8] == null ? null : ((Number) row[8]).doubleValue()) // games_behind (numeric)
                        .build())
                .collect(Collectors.toList());
    }

    private void logTeamRankingQueryCompleted(int seasonYear, String source, int rankingCount, long startedAtNanos) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        if (elapsedMs >= 500L) {
            log.warn(
                    "event=home_team_rankings_query_completed seasonYear={} source={} rankingCount={} elapsedMs={}",
                    seasonYear,
                    source,
                    rankingCount,
                    elapsedMs);
            return;
        }
        log.info(
                "event=home_team_rankings_query_completed seasonYear={} source={} rankingCount={} elapsedMs={}",
                seasonYear,
                source,
                rankingCount,
                elapsedMs);
    }

    // 리그 시작 날짜 조회
    @Cacheable(value = LEAGUE_DATES, key = "T(java.time.LocalDate).now().getYear()", sync = true)
    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public LeagueStartDatesDto getLeagueStartDates() {
        LocalDate now = LocalDate.now();
        int seasonYear = now.getYear();

        // kbo_seasons의 운영자 제공 시작일을 먼저 사용하고, 없을 때만 game 집계 쿼리로 보정한다.
        LocalDate regularStart = gameRepository
                .findConfiguredStartDateByTypeFromSeasonYear(0, seasonYear)
                .or(() -> gameRepository.findFirstRegularSeasonDate(seasonYear))
                .or(() -> gameRepository.findLatestStartDateByTypeAsOf(0, now))
                .orElse(now);

        LocalDate postseasonStart = gameRepository
                .findConfiguredStartDateByTypeFromSeasonYear(2, seasonYear)
                .or(() -> gameRepository.findFirstPostseasonDate(seasonYear))
                .or(() -> gameRepository.findLatestStartDateByTypeAsOf(2, now))
                .orElse(null);

        LocalDate koreanSeriesStart = gameRepository
                .findConfiguredStartDateByTypeFromSeasonYear(5, seasonYear)
                .or(() -> gameRepository.findFirstKoreanSeriesDate(seasonYear))
                .or(() -> gameRepository.findLatestStartDateByTypeAsOf(5, now))
                .orElse(null);

        regularStart = normalizeDateToSeasonYear(regularStart, seasonYear);
        postseasonStart = normalizeDateToSeasonYear(postseasonStart, seasonYear);
        koreanSeriesStart = normalizeDateToSeasonYear(koreanSeriesStart, seasonYear);

        return LeagueStartDatesDto.builder()
                .regularSeasonStart(regularStart.toString())
                .postseasonStart(postseasonStart == null ? null : postseasonStart.toString())
                .koreanSeriesStart(koreanSeriesStart == null ? null : koreanSeriesStart.toString())
                .build();
    }

    private LocalDate normalizeDateToSeasonYear(LocalDate date, int seasonYear) {
        if (date == null) {
            return null;
        }
        if (date.getYear() == seasonYear) {
            return date;
        }

        int maxDay = date.getMonth().length(Year.isLeap(seasonYear));
        int normalizedDay = Math.min(date.getDayOfMonth(), maxDay);
        return LocalDate.of(seasonYear, date.getMonth(), normalizedDay);
    }

    // 날짜 네비게이션 정보 조회
    @Cacheable(value = GAME_SCHEDULE, key = "#root.target.buildScheduleNavigationCacheKey(#date)")
    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public ScheduleNavigationDto getScheduleNavigation(LocalDate date) {
        LocalDate prev = gameRepository.findPrevGameDate(date).orElse(null);
        LocalDate next = gameRepository.findNextGameDate(date).orElse(null);

        return ScheduleNavigationDto.builder()
                .prevGameDate(prev)
                .nextGameDate(next)
                .hasPrev(prev != null)
                .hasNext(next != null)
                .build();
    }

    @Cacheable(value = GAME_SCHEDULE, key = "#root.target.buildScopedNavigationCacheKey(#date, #scope, #seasonYear)")
    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public HomeScopedNavigationDto getScopedNavigation(LocalDate date, String scope, Integer seasonYear) {
        LocalDate anchorDate = date == null ? LocalDate.now() : date;
        String normalizedScope = normalizeNavigationScope(scope);
        int resolvedSeasonYear = seasonYear == null ? anchorDate.getYear() : seasonYear;

        LocalDate resolvedDate = resolveScopedDate(anchorDate, normalizedScope, resolvedSeasonYear);
        LocalDate navigationAnchor = resolvedDate == null ? anchorDate : resolvedDate;
        LocalDate prev = findPrevScopedDate(navigationAnchor, normalizedScope, resolvedSeasonYear);
        LocalDate next = findNextScopedDate(navigationAnchor, normalizedScope, resolvedSeasonYear);

        return HomeScopedNavigationDto.builder()
                .resolvedDate(resolvedDate == null ? null : resolvedDate.toString())
                .prevGameDate(prev == null ? null : prev.toString())
                .nextGameDate(next == null ? null : next.toString())
                .hasPrev(prev != null)
                .hasNext(next != null)
                .build();
    }

    private String normalizeNavigationScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "regular";
        }

        String normalized = scope.trim().toLowerCase();
        if (SCOPE_POSTSEASON.equals(normalized)
                || SCOPE_KOREAN_SERIES.equals(normalized)
                || SCOPE_SCHEDULED.equals(normalized)) {
            return normalized;
        }

        return "regular";
    }

    private LocalDate resolveScopedDate(LocalDate anchorDate, String scope, int seasonYear) {
        LocalDate current = findCurrentScopedDate(anchorDate, scope, seasonYear);
        if (current != null) {
            return current;
        }

        LocalDate next = findNextScopedDate(anchorDate.minusDays(1), scope, seasonYear);
        if (next != null) {
            return next;
        }

        return findPrevScopedDate(anchorDate.plusDays(1), scope, seasonYear);
    }

    private LocalDate findCurrentScopedDate(LocalDate anchorDate, String scope, int seasonYear) {
        if (SCOPE_SCHEDULED.equals(scope)) {
            return gameRepository.findScheduledNavigationDateOnOrAfter(anchorDate, SCHEDULED_NAVIGATION_STATUSES)
                    .filter(anchorDate::equals)
                    .orElse(null);
        }

        return gameRepository.findScopedGameDateOnOrAfter(
                        anchorDate,
                        seasonYear,
                        resolveLeagueCodes(scope),
                        STANDARD_NAVIGATION_EXCLUDED_STATUSES)
                .filter(anchorDate::equals)
                .orElse(null);
    }

    private LocalDate findPrevScopedDate(LocalDate anchorDate, String scope, int seasonYear) {
        if (SCOPE_SCHEDULED.equals(scope)) {
            return gameRepository.findPrevScheduledNavigationDate(anchorDate, SCHEDULED_NAVIGATION_STATUSES)
                    .orElse(null);
        }

        return gameRepository.findPrevScopedGameDate(
                        anchorDate,
                        seasonYear,
                        resolveLeagueCodes(scope),
                        STANDARD_NAVIGATION_EXCLUDED_STATUSES)
                .orElse(null);
    }

    private LocalDate findNextScopedDate(LocalDate anchorDate, String scope, int seasonYear) {
        if (SCOPE_SCHEDULED.equals(scope)) {
            return gameRepository.findNextScheduledNavigationDate(anchorDate, SCHEDULED_NAVIGATION_STATUSES)
                    .orElse(null);
        }

        return gameRepository.findNextScopedGameDate(
                        anchorDate,
                        seasonYear,
                        resolveLeagueCodes(scope),
                        STANDARD_NAVIGATION_EXCLUDED_STATUSES)
                .orElse(null);
    }

    private List<Integer> resolveLeagueCodes(String scope) {
        if (SCOPE_POSTSEASON.equals(scope)) {
            return POSTSEASON_LEAGUE_CODES;
        }
        if (SCOPE_KOREAN_SERIES.equals(scope)) {
            return KOREAN_SERIES_LEAGUE_CODES;
        }
        return REGULAR_LEAGUE_CODES;
    }

    private String resolveTeamId(String rawTeamCode, String mappedTeamId) {
        if (mappedTeamId != null && !mappedTeamId.isBlank()) {
            return mappedTeamId;
        }
        String normalized = TeamCodeNormalizer.normalize(rawTeamCode);
        if (normalized == null || normalized.isBlank()) {
            return rawTeamCode;
        }
        return normalized;
    }

    private String resolveTeamName(String resolvedTeamId, String mappedTeamName) {
        if (mappedTeamName != null && !mappedTeamName.isBlank()) {
            return mappedTeamName;
        }
        return resolvedTeamId;
    }

    private List<MatchRangeProjection> findGameProjectionsByDateWithJdbc(LocalDate date) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(stadiumDataSource);
        return jdbcTemplate.query(
                """
                        SELECT
                          g.game_id,
                          g.game_date,
                          g.stadium,
                          g.home_team,
                          g.away_team,
                          g.home_score,
                          g.away_score,
                          g.is_dummy,
                          g.home_pitcher,
                          g.away_pitcher,
                          g.season_id,
                          g.game_status,
                          gm.start_time
                        FROM game g
                        LEFT JOIN game_metadata gm ON gm.game_id = g.game_id
                        WHERE g.game_date = ?
                          AND g.is_dummy IS NOT TRUE
                          AND g.game_id NOT LIKE 'MOCK%'
                        ORDER BY g.game_id
                        """,
                (rs, rowNum) -> new JdbcMatchRangeProjection(
                        rs.getString("game_id"),
                        rs.getDate("game_date") == null ? null : rs.getDate("game_date").toLocalDate(),
                        rs.getString("stadium"),
                        rs.getString("home_team"),
                        rs.getString("away_team"),
                        (Integer) rs.getObject("home_score"),
                        (Integer) rs.getObject("away_score"),
                        (Boolean) rs.getObject("is_dummy"),
                        rs.getString("home_pitcher"),
                        rs.getString("away_pitcher"),
                        (Integer) rs.getObject("season_id"),
                        null,
                        null,
                        rs.getString("game_status"),
                        rs.getObject("start_time", LocalTime.class)),
                java.sql.Date.valueOf(date));
    }

    private record JdbcMatchRangeProjection(
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
