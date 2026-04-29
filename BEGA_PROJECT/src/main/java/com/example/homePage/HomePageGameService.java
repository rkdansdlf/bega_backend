package com.example.homepage;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.repository.GameMetadataRepository;
import com.example.kbo.repository.GameRepository;
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
    private final GameMetadataRepository gameMetadataRepository;
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

    @Cacheable(value = GAME_SCHEDULE, key = "#date.toString()")
    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public List<HomePageGameDto> getGamesByDate(LocalDate date) {
        List<GameEntity> games = gameRepository.findByGameDate(date);
        if (games.isEmpty()) {
            List<GameEntity> jdbcFallbackGames = findGamesByDateWithJdbc(date);
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
        baseballDataIntegrityGuard.ensureHomeGamesByDate("home.schedule", date, games);
        Map<String, LocalTime> startTimes = loadStartTimes(games);

        return games.stream()
                .map(game -> convertToDto(game, startTimes.get(game.getGameId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public List<HomePageScheduledGameDto> getScheduledGamesWindow(LocalDate startDate, LocalDate endDate) {
        List<GameEntity> games = gameRepository.findScheduledGamesByDateRange(
                startDate,
                endDate,
                SCHEDULED_WINDOW_STATUSES);
        if (games.isEmpty()) {
            return List.of();
        }
        baseballDataIntegrityGuard.ensureHomeScheduledWindow(
                "home.scheduled_window",
                startDate,
                endDate,
                games);
        Map<String, LocalTime> startTimes = loadStartTimes(games);
        return games.stream()
                .map(game -> {
                    HomePageGameDto baseDto = convertToDto(game, startTimes.get(game.getGameId()));
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

    private HomePageGameDto convertToDto(GameEntity game, LocalTime startTime) {
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
                startTime,
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
                .time(formatGameTime(startTime))
                .leagueType(leagueType)
                .gameInfo(gameInfo)
                .build();
    }

    private Map<String, LocalTime> loadStartTimes(List<GameEntity> games) {
        if (games == null || games.isEmpty()) {
            return Map.of();
        }

        List<String> gameIds = games.stream()
                .map(GameEntity::getGameId)
                .filter(gameId -> gameId != null && !gameId.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (gameIds.isEmpty()) {
            return Map.of();
        }

        Map<String, LocalTime> startTimes = new HashMap<>();
        for (GameMetadataEntity metadata : gameMetadataRepository.findAllById(gameIds)) {
            if (metadata.getGameId() == null || metadata.getStartTime() == null) {
                continue;
            }
            startTimes.putIfAbsent(metadata.getGameId(), metadata.getStartTime());
        }
        return startTimes;
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

	private String determineLeagueType(GameEntity game) {
		Integer leagueTypeCode = leagueStageResolver.resolveEffectiveLeagueTypeCode(game);
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

    // v_team_rank_all 뷰에서 순위 데이터를 가져오도록 수정
    @Cacheable(value = TEAM_RANKINGS, key = "#seasonYear", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public List<HomePageTeamRankingDto> getTeamRankings(int seasonYear) {
        List<Object[]> results = gameRepository.findTeamRankingsBySeason(seasonYear);
        if (log.isDebugEnabled()) {
            log.debug("Team rankings query completed - seasonYear={}, source=primary, count={}", seasonYear, results.size());
        }
        if (results.isEmpty()) {
            results = gameRepository.findTeamRankingsBySeasonFallback(seasonYear);
            if (log.isDebugEnabled()) {
                log.debug("Team rankings query completed - seasonYear={}, source=fallback, count={}", seasonYear, results.size());
            }
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

    // 리그 시작 날짜 조회
    @Cacheable(value = LEAGUE_DATES, key = "T(java.time.LocalDate).now().getYear()")
    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public LeagueStartDatesDto getLeagueStartDates() {
        LocalDate now = LocalDate.now();
        int seasonYear = now.getYear();

        // DB에서 각 리그의 첫 경기 날짜 조회
        LocalDate regularStart = gameRepository
                .findFirstRegularSeasonDate(seasonYear)
                .or(() -> gameRepository.findConfiguredStartDateByTypeFromSeasonYear(0, seasonYear))
                .or(() -> gameRepository.findFirstStartDateByTypeFromSeasonYear(0, seasonYear))
                .or(() -> gameRepository.findLatestStartDateByTypeAsOf(0, now))
                .orElse(now);

        LocalDate postseasonStart = gameRepository
                .findFirstPostseasonDate(seasonYear)
                .or(() -> gameRepository.findConfiguredStartDateByTypeFromSeasonYear(2, seasonYear))
                .or(() -> gameRepository.findFirstStartDateByTypeFromSeasonYear(2, seasonYear))
                .or(() -> gameRepository.findLatestStartDateByTypeAsOf(2, now))
                .orElse(null);

        LocalDate koreanSeriesStart = gameRepository
                .findFirstKoreanSeriesDate(seasonYear)
                .or(() -> gameRepository.findConfiguredStartDateByTypeFromSeasonYear(5, seasonYear))
                .or(() -> gameRepository.findFirstStartDateByTypeFromSeasonYear(5, seasonYear))
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

    private List<GameEntity> findGamesByDateWithJdbc(LocalDate date) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(stadiumDataSource);
        return jdbcTemplate.query(
                """
                        SELECT
                          id,
                          game_id,
                          game_date,
                          stadium,
                          home_team,
                          away_team,
                          home_score,
                          away_score,
                          winning_team,
                          winning_score,
                          season_id,
                          stadium_id,
                          game_status,
                          is_dummy,
                          home_pitcher,
                          away_pitcher
                        FROM game
                        WHERE game_date = ?
                        ORDER BY game_id
                        """,
                (rs, rowNum) -> GameEntity.builder()
                        .id(rs.getLong("id"))
                        .gameId(rs.getString("game_id"))
                        .gameDate(rs.getDate("game_date").toLocalDate())
                        .stadium(rs.getString("stadium"))
                        .homeTeam(rs.getString("home_team"))
                        .awayTeam(rs.getString("away_team"))
                        .homeScore((Integer) rs.getObject("home_score"))
                        .awayScore((Integer) rs.getObject("away_score"))
                        .winningTeam(rs.getString("winning_team"))
                        .winningScore((Integer) rs.getObject("winning_score"))
                        .seasonId((Integer) rs.getObject("season_id"))
                        .stadiumId(rs.getString("stadium_id"))
                        .gameStatus(rs.getString("game_status"))
                        .isDummy((Boolean) rs.getObject("is_dummy"))
                        .homePitcher(rs.getString("home_pitcher"))
                        .awayPitcher(rs.getString("away_pitcher"))
                        .build(),
                java.sql.Date.valueOf(date));
    }
}
