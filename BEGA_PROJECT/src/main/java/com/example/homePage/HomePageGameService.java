package com.example.homepage;

import java.time.LocalDate;
import java.time.Year;
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
import com.example.kbo.repository.GameRepository;
import com.example.kbo.util.TeamCodeNormalizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;

import static com.example.common.config.CacheConfig.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class HomePageGameService {

    private final GameRepository gameRepository;
	private final HomePageTeamRepository homePageTeamRepository;
    @Qualifier("stadiumDataSource")
    private final DataSource stadiumDataSource;

	private final Map<String, HomePageTeam> teamMap = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> leagueTypeCodeMap = new ConcurrentHashMap<>();

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

        return games.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private HomePageGameDto convertToDto(GameEntity game) {
		HomePageTeam homeTeam = getTeam(game.getHomeTeam());
		HomePageTeam awayTeam = getTeam(game.getAwayTeam());
        String resolvedHomeTeamId = resolveTeamId(game.getHomeTeam(), homeTeam.getTeamId());
        String resolvedAwayTeamId = resolveTeamId(game.getAwayTeam(), awayTeam.getTeamId());
        String resolvedHomeTeamName = resolveTeamName(resolvedHomeTeamId, homeTeam.getTeamName());
        String resolvedAwayTeamName = resolveTeamName(resolvedAwayTeamId, awayTeam.getTeamName());

		String leagueType = determineLeagueType(game.getSeasonId());
		String gameInfo = "";

		if ("KOREAN_SERIES".equals(leagueType)) {
			gameInfo = "한국시리즈";
		} else if ("POSTSEASON".equals(leagueType)) {
			gameInfo = "포스트시즌";
		}

        String gameStatusKr = convertGameStatus(game.getGameStatus());

        // 점수 데이터 처리 with 상태별 검증
        Integer homeScore = game.getHomeScore();
        Integer awayScore = game.getAwayScore();

        // 경기 종료 상태인데 점수가 없으면 0으로 초기화 (데이터 정합성 보장)
        if ("COMPLETED".equals(game.getGameStatus())) {
            homeScore = (homeScore != null) ? homeScore : 0;
            awayScore = (awayScore != null) ? awayScore : 0;
        }

        return HomePageGameDto.builder()
                .gameId(game.getGameId())
                .stadium(game.getStadium())
                .gameStatus(game.getGameStatus())
                .gameStatusKr(gameStatusKr)
                .homeTeam(resolvedHomeTeamId)
                .homeTeamFull(resolvedHomeTeamName)
                .awayTeam(resolvedAwayTeamId)
                .awayTeamFull(resolvedAwayTeamName)
                .homeScore(homeScore)
                .awayScore(awayScore)
                .time("18:30")
                .leagueType(leagueType)
                .gameInfo(gameInfo)
                .build();
    }

    private String convertGameStatus(String status) {
        if (status == null)
            return "정보 없음";

        switch (status) {
            case "SCHEDULED":
                return "경기 예정";
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

	private String determineLeagueType(Integer seasonId) {
		if (seasonId == null) {
			return "OFFSEASON";
		}

		Integer leagueTypeCode = leagueTypeCodeMap.computeIfAbsent(
				seasonId,
				id -> gameRepository.findLeagueTypeCodeBySeasonId(id).orElse(-1));

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
                .or(() -> gameRepository.findFirstStartDateByTypeFromSeasonYear(0, seasonYear))
                .or(() -> gameRepository.findLatestStartDateByTypeAsOf(0, now))
                .orElse(now);

        LocalDate postseasonStart = gameRepository
                .findFirstPostseasonDate(seasonYear)
                .or(() -> gameRepository.findFirstStartDateByTypeFromSeasonYear(2, seasonYear))
                .or(() -> gameRepository.findLatestStartDateByTypeAsOf(2, now))
                .orElse(now);

        LocalDate koreanSeriesStart = gameRepository
                .findFirstKoreanSeriesDate(seasonYear)
                .or(() -> gameRepository.findFirstStartDateByTypeFromSeasonYear(5, seasonYear))
                .or(() -> gameRepository.findLatestStartDateByTypeAsOf(5, now))
                .orElse(now);

        regularStart = normalizeDateToSeasonYear(regularStart, seasonYear);
        postseasonStart = normalizeDateToSeasonYear(postseasonStart, seasonYear);
        koreanSeriesStart = normalizeDateToSeasonYear(koreanSeriesStart, seasonYear);

        return LeagueStartDatesDto.builder()
                .regularSeasonStart(regularStart.toString())
                .postseasonStart(postseasonStart.toString())
                .koreanSeriesStart(koreanSeriesStart.toString())
                .build();
    }

    private LocalDate normalizeDateToSeasonYear(LocalDate date, int seasonYear) {
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
