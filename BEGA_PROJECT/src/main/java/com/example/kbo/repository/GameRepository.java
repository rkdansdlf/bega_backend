package com.example.kbo.repository;

import com.example.kbo.entity.GameEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * GameRepository - 통합 경기 Repository
 *
 * 기존 MatchRepository, BegaGameRepository, HomePageGameRepository를 통합
 */
public interface GameRepository extends JpaRepository<GameEntity, Long> {

  String CANONICAL_RANGE_PROJECTION_QUERY = """
      SELECT
          g.game_id AS "gameId",
          g.game_date AS "gameDate",
          g.stadium AS "stadium",
          g.home_team AS "homeTeam",
          g.away_team AS "awayTeam",
          g.home_score AS "homeScore",
          g.away_score AS "awayScore",
          g.is_dummy AS "isDummy",
          g.home_pitcher AS "homePitcher",
          g.away_pitcher AS "awayPitcher",
          g.season_id AS "seasonId",
          NULL AS "rawLeagueTypeCode",
          NULL AS "seriesGameNo",
          g.game_status AS "gameStatus",
          gm.start_time AS "startTime"
      FROM game g
      LEFT JOIN game_metadata gm ON gm.game_id = g.game_id
      WHERE g.game_date BETWEEN :startDate AND :endDate
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
        AND g.home_team IN :canonicalTeams
        AND g.away_team IN :canonicalTeams
      ORDER BY g.game_date ASC, g.game_id ASC
      """;

  String CANONICAL_RANGE_PROJECTION_COUNT_QUERY = """
      SELECT COUNT(*)
      FROM game g
      WHERE g.game_date BETWEEN :startDate AND :endDate
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
        AND g.home_team IN :canonicalTeams
        AND g.away_team IN :canonicalTeams
      """;

  String CANONICAL_GAME_DATE_PROJECTION_QUERY = """
      SELECT
          g.game_id AS "gameId",
          g.game_date AS "gameDate",
          g.stadium AS "stadium",
          g.home_team AS "homeTeam",
          g.away_team AS "awayTeam",
          g.home_score AS "homeScore",
          g.away_score AS "awayScore",
          g.is_dummy AS "isDummy",
          g.home_pitcher AS "homePitcher",
          g.away_pitcher AS "awayPitcher",
          g.season_id AS "seasonId",
          NULL AS "rawLeagueTypeCode",
          NULL AS "seriesGameNo",
          g.game_status AS "gameStatus",
          gm.start_time AS "startTime"
      FROM game g
      LEFT JOIN game_metadata gm ON gm.game_id = g.game_id
      WHERE g.game_date = :gameDate
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
        AND g.home_team IN :canonicalTeams
        AND g.away_team IN :canonicalTeams
      ORDER BY g.game_date ASC, g.game_id ASC
      """;

  String HOME_SCHEDULED_WINDOW_PROJECTION_QUERY = """
      SELECT
          g.game_id AS "gameId",
          g.game_date AS "gameDate",
          g.stadium AS "stadium",
          g.home_team AS "homeTeam",
          g.away_team AS "awayTeam",
          g.home_score AS "homeScore",
          g.away_score AS "awayScore",
          g.is_dummy AS "isDummy",
          g.home_pitcher AS "homePitcher",
          g.away_pitcher AS "awayPitcher",
          g.season_id AS "seasonId",
          NULL AS "rawLeagueTypeCode",
          NULL AS "seriesGameNo",
          g.game_status AS "gameStatus",
          gm.start_time AS "startTime"
      FROM game g
      LEFT JOIN game_metadata gm ON gm.game_id = g.game_id
      WHERE g.game_date BETWEEN :startDate AND :endDate
        AND UPPER(g.game_status) IN :statuses
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
        AND g.home_team IN :canonicalTeams
        AND g.away_team IN :canonicalTeams
      ORDER BY g.game_date ASC, g.game_id ASC
      """;

  String CANONICAL_POSTSEASON_SERIES_HISTORY_QUERY = """
      SELECT
          g.game_id AS "gameId",
          g.game_date AS "gameDate",
          g.stadium AS "stadium",
          g.home_team AS "homeTeam",
          g.away_team AS "awayTeam",
          g.home_score AS "homeScore",
          g.away_score AS "awayScore",
          g.is_dummy AS "isDummy",
          g.home_pitcher AS "homePitcher",
          g.away_pitcher AS "awayPitcher",
          g.season_id AS "seasonId",
          NULL AS "rawLeagueTypeCode",
          NULL AS "seriesGameNo",
          g.game_status AS "gameStatus",
          gm.start_time AS "startTime"
      FROM game g
      LEFT JOIN game_metadata gm ON gm.game_id = g.game_id
      WHERE g.season_id IN :seasonIds
        AND g.game_date <= :gameDate
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
        AND g.home_team IN :canonicalTeams
        AND g.away_team IN :canonicalTeams
      ORDER BY g.game_date ASC, g.game_id ASC
      """;

  String CANONICAL_COMPLETED_RANGE_PROJECTION_QUERY = """
      SELECT
          g.game_id AS "gameId",
          g.game_date AS "gameDate",
          g.stadium AS "stadium",
          g.home_team AS "homeTeam",
          g.away_team AS "awayTeam",
          g.home_score AS "homeScore",
          g.away_score AS "awayScore",
          g.is_dummy AS "isDummy",
          g.home_pitcher AS "homePitcher",
          g.away_pitcher AS "awayPitcher",
          g.season_id AS "seasonId",
          NULL AS "rawLeagueTypeCode",
          NULL AS "seriesGameNo",
          g.game_status AS "gameStatus",
          gm.start_time AS "startTime"
      FROM game g
      LEFT JOIN game_metadata gm ON gm.game_id = g.game_id
      WHERE g.game_date IN :gameDates
        AND g.home_score IS NOT NULL
        AND g.away_score IS NOT NULL
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
        AND g.home_team IN :canonicalTeams
        AND g.away_team IN :canonicalTeams
      ORDER BY g.game_date ASC, g.game_id ASC
      """;

  // ========================================
  // 기본 조회 메서드
  // ========================================

  /**
   * gameId로 경기 조회
   * 
   * @param gameId 경기 ID (20250412KTLT0 형식)
   * @return 경기 엔티티
   */
  Optional<GameEntity> findByGameId(String gameId);

  @Query("""
      SELECT
          g.gameId AS gameId,
          g.gameDate AS gameDate,
          g.stadium AS stadium,
          m.stadiumName AS stadiumName,
          m.startTime AS startTime,
          m.attendance AS attendance,
          m.weather AS weather,
          m.gameTimeMinutes AS gameTimeMinutes,
          g.homeTeam AS homeTeam,
          g.awayTeam AS awayTeam,
          g.homeScore AS homeScore,
          g.awayScore AS awayScore,
          g.homePitcher AS homePitcher,
          g.awayPitcher AS awayPitcher,
          g.gameStatus AS gameStatus
      FROM GameEntity g
      LEFT JOIN GameMetadataEntity m ON m.gameId = g.gameId
      WHERE g.gameId = :gameId
      """)
  Optional<GameDetailHeaderProjection> findGameDetailHeaderByGameId(@Param("gameId") String gameId);

  /**
   * gameId 목록으로 경기 일괄 조회
   */
  List<GameEntity> findByGameIdIn(Collection<String> gameIds);

  @Query(value = """
      SELECT
          g.game_id AS "gameId",
          CASE
              WHEN g.home_score = g.away_score THEN 'draw'
              WHEN g.home_score > g.away_score THEN 'home'
              ELSE 'away'
          END AS "winner"
      FROM game g
      WHERE g.game_id IN :gameIds
        AND g.home_score IS NOT NULL
        AND g.away_score IS NOT NULL
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
        AND g.home_team IN :canonicalTeams
        AND g.away_team IN :canonicalTeams
      """, nativeQuery = true)
  List<PredictionStatsGameProjection> findPredictionStatsGameSummaries(
      @Param("gameIds") Collection<String> gameIds,
      @Param("canonicalTeams") List<String> canonicalTeams);

  /**
   * 특정 날짜의 경기 목록 조회
   * 
   * @param gameDate 경기 날짜
   * @return 해당 날짜의 모든 경기
   */
  List<GameEntity> findByGameDate(LocalDate gameDate);

  /**
   * 특정 날짜 + 홈/원정 팀 variants 매칭 조회
   */
  @Query("SELECT g FROM GameEntity g " +
      "WHERE g.gameDate = :gameDate " +
      "AND g.homeTeam IN :homeTeamVariants " +
      "AND g.awayTeam IN :awayTeamVariants")
  List<GameEntity> findByGameDateAndTeamVariants(
      @Param("gameDate") LocalDate gameDate,
      @Param("homeTeamVariants") List<String> homeTeamVariants,
      @Param("awayTeamVariants") List<String> awayTeamVariants);

  /**
   * 특정 날짜 + 더미 여부로 조회
   * 
   * @param gameDate 경기 날짜
   * @param isDummy  더미 여부
   * @return 해당 조건의 경기 목록
   */
  List<GameEntity> findByGameDateAndIsDummy(LocalDate gameDate, Boolean isDummy);

  /**
   * 더미 여부로 조회
   * 
   * @param isDummy 더미 여부
   * @return 해당 조건의 경기 목록
   */
  List<GameEntity> findByIsDummy(Boolean isDummy);

  // ========================================
  // Prediction 관련 쿼리
  // ========================================

  /**
   * 최근 완료된 경기 날짜 목록 조회 (중복 제거)
   * 
   * @param today 기준 날짜 (오늘)
   * @return 완료된 경기 날짜 목록 (내림차순)
   */
  @Query("SELECT DISTINCT g.gameDate FROM GameEntity g " +
      "WHERE g.gameDate < :today " +
      "AND g.homeScore IS NOT NULL " +
      "AND g.awayScore IS NOT NULL " +
      "ORDER BY g.gameDate DESC")
  List<LocalDate> findRecentDistinctGameDates(@Param("today") LocalDate today);

  /**
   * 특정 날짜 목록의 완료된 경기 조회
   * 
   * @param dates 날짜 목록
   * @return 완료된 경기 목록 (날짜 오름차순)
   */
  @Query("SELECT g FROM GameEntity g " +
      "WHERE g.gameDate IN :dates " +
      "AND g.homeScore IS NOT NULL " +
      "AND g.awayScore IS NOT NULL " +
      "ORDER BY g.gameDate ASC, g.gameId ASC")
  List<GameEntity> findAllByGameDatesIn(@Param("dates") List<LocalDate> dates);

  /**
   * 기간 내 모든 경기 조회
   * 
   * @param startDate 시작 날짜
   * @param endDate   종료 날짜
   * @return 모든 경기 목록 (날짜 오름차순)
   */
  @Query("SELECT g FROM GameEntity g " +
      "WHERE g.gameDate BETWEEN :startDate AND :endDate " +
      "ORDER BY g.gameDate ASC, g.gameId ASC")
  List<GameEntity> findAllByDateRange(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  @Query("SELECT g FROM GameEntity g " +
      "WHERE g.gameDate BETWEEN :startDate AND :endDate " +
      "AND UPPER(g.gameStatus) IN :statuses " +
      "AND COALESCE(g.isDummy, false) = false " +
      "AND g.gameId NOT LIKE 'MOCK%' " +
      "ORDER BY g.gameDate ASC, g.gameId ASC")
  List<GameEntity> findScheduledGamesByDateRange(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("statuses") Collection<String> statuses);

  @Query("SELECT g FROM GameEntity g " +
      "WHERE g.gameDate BETWEEN :startDate AND :endDate " +
      "AND COALESCE(g.isDummy, false) = false " +
      "AND g.gameId NOT LIKE 'MOCK%' " +
      "AND g.homeTeam IN :canonicalTeams " +
      "AND g.awayTeam IN :canonicalTeams " +
      "ORDER BY g.gameDate ASC, g.gameId ASC")
  Page<GameEntity> findCanonicalByDateRange(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("canonicalTeams") List<String> canonicalTeams,
      Pageable pageable);

  @Query(value = CANONICAL_RANGE_PROJECTION_QUERY, nativeQuery = true)
  List<MatchRangeProjection> findCanonicalRangeProjectionByDateRangeNoCount(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("canonicalTeams") List<String> canonicalTeams,
      Pageable pageable);

  @Query(value = CANONICAL_RANGE_PROJECTION_QUERY,
      countQuery = CANONICAL_RANGE_PROJECTION_COUNT_QUERY,
      nativeQuery = true)
  Page<MatchRangeProjection> findCanonicalRangeProjectionByDateRange(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("canonicalTeams") List<String> canonicalTeams,
      Pageable pageable);

  @Query("SELECT MIN(g.gameDate) FROM GameEntity g " +
      "WHERE COALESCE(g.isDummy, false) = false " +
      "AND g.gameId NOT LIKE 'MOCK%' " +
      "AND g.homeTeam IN :canonicalTeams " +
      "AND g.awayTeam IN :canonicalTeams")
  Optional<LocalDate> findCanonicalMinGameDate(
      @Param("canonicalTeams") List<String> canonicalTeams);

  @Query("SELECT MAX(g.gameDate) FROM GameEntity g " +
      "WHERE COALESCE(g.isDummy, false) = false " +
      "AND g.gameId NOT LIKE 'MOCK%' " +
      "AND g.homeTeam IN :canonicalTeams " +
      "AND g.awayTeam IN :canonicalTeams")
  Optional<LocalDate> findCanonicalMaxGameDate(
      @Param("canonicalTeams") List<String> canonicalTeams);

  @Query("SELECT MAX(g.gameDate) FROM GameEntity g " +
      "WHERE g.gameDate < :date " +
      "AND COALESCE(g.isDummy, false) = false " +
      "AND g.gameId NOT LIKE 'MOCK%' " +
      "AND g.homeTeam IN :canonicalTeams " +
      "AND g.awayTeam IN :canonicalTeams")
  Optional<LocalDate> findCanonicalPrevGameDate(
      @Param("date") LocalDate date,
      @Param("canonicalTeams") List<String> canonicalTeams);

  @Query("SELECT MIN(g.gameDate) FROM GameEntity g " +
      "WHERE g.gameDate > :date " +
      "AND COALESCE(g.isDummy, false) = false " +
      "AND g.gameId NOT LIKE 'MOCK%' " +
      "AND g.homeTeam IN :canonicalTeams " +
      "AND g.awayTeam IN :canonicalTeams")
  Optional<LocalDate> findCanonicalNextGameDate(
      @Param("date") LocalDate date,
      @Param("canonicalTeams") List<String> canonicalTeams);

		  @Query(value = """
		      SELECT
		          (
		              SELECT MAX(g.game_date)
		              FROM game g
		              WHERE g.game_date < :date
		                AND g.is_dummy IS NOT TRUE
		                AND g.game_id NOT LIKE 'MOCK%'
		                AND g.home_team IN :canonicalTeams
		                AND g.away_team IN :canonicalTeams
		          ) AS "prevDate",
		          (
		              SELECT MIN(g.game_date)
		              FROM game g
		              WHERE g.game_date > :date
		                AND g.is_dummy IS NOT TRUE
		                AND g.game_id NOT LIKE 'MOCK%'
		                AND g.home_team IN :canonicalTeams
		                AND g.away_team IN :canonicalTeams
		          ) AS "nextDate"
		      """, nativeQuery = true)
  CanonicalAdjacentGameDatesProjection findCanonicalAdjacentGameDates(
      @Param("date") LocalDate date,
      @Param("canonicalTeams") List<String> canonicalTeams);

  @Query(value = """
      SELECT
          MIN(g.game_date) AS "earliestGameDate",
          MAX(g.game_date) AS "latestGameDate"
      FROM game g
      WHERE g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
        AND g.home_team IN :canonicalTeams
        AND g.away_team IN :canonicalTeams
      """, nativeQuery = true)
  CanonicalGameDateBoundsProjection findCanonicalGameDateBounds(
      @Param("canonicalTeams") List<String> canonicalTeams);

  @Query(value = CANONICAL_GAME_DATE_PROJECTION_QUERY, nativeQuery = true)
  List<MatchRangeProjection> findCanonicalRangeProjectionByGameDate(
      @Param("gameDate") LocalDate gameDate,
      @Param("canonicalTeams") List<String> canonicalTeams);

  @Query(value = HOME_SCHEDULED_WINDOW_PROJECTION_QUERY, nativeQuery = true)
  List<MatchRangeProjection> findScheduledWindowProjectionByDateRange(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("statuses") Collection<String> statuses,
      @Param("canonicalTeams") List<String> canonicalTeams);

  @Query(value = CANONICAL_POSTSEASON_SERIES_HISTORY_QUERY, nativeQuery = true)
  List<MatchRangeProjection> findCanonicalPostseasonSeriesHistoryThroughGameDate(
      @Param("gameDate") LocalDate gameDate,
      @Param("seasonIds") Collection<Integer> seasonIds,
      @Param("canonicalTeams") List<String> canonicalTeams);

  @Query(value = CANONICAL_COMPLETED_RANGE_PROJECTION_QUERY, nativeQuery = true)
  List<MatchRangeProjection> findCanonicalCompletedRangeProjectionByGameDates(
      @Param("gameDates") List<LocalDate> gameDates,
      @Param("canonicalTeams") List<String> canonicalTeams);

  /**
   * 기간 내 완료된 경기 조회
   * 
   * @param startDate 시작 날짜
   * @param endDate   종료 날짜
   * @return 완료된 경기 목록 (날짜 내림차순)
   */
  @Query("SELECT g FROM GameEntity g " +
      "WHERE g.gameDate BETWEEN :startDate AND :endDate " +
      "AND g.homeScore IS NOT NULL " +
      "AND g.awayScore IS NOT NULL " +
      "ORDER BY g.gameDate DESC")
  List<GameEntity> findCompletedByDateRange(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  // ========================================
  // HomePage 관련 쿼리 (Native Query)
  // ========================================

  /**
   * 특정 시즌의 정규시즌 팀별 순위 데이터 조회.
   *
   * season_id가 있는 정규시즌 row는 kbo_seasons 직접 조인으로 조회하고,
   * legacy season_id NULL row는 별도 date-range branch로 처리해 OR/COALESCE/EXTRACT
   * 조합이 주요 ranking cold path에서 실행되지 않게 한다.
   *
   * @param seasonYear 시즌 연도
   * @param seasonStart 시즌 연도 시작일
   * @param nextSeasonStart 다음 시즌 연도 시작일
   * @return 순위 데이터 (Object[] 배열)
   */
  @Query(value = """
      WITH season_games AS (
          SELECT
              UPPER(TRIM(g.home_team)) AS home_team_id,
              UPPER(TRIM(g.away_team)) AS away_team_id,
              g.home_score,
              g.away_score
          FROM game g
          JOIN kbo_seasons s ON g.season_id = s.season_id
          WHERE s.season_year = :seasonYear
            AND COALESCE(s.league_type_code, 0) = 0
            AND g.home_score IS NOT NULL
            AND g.away_score IS NOT NULL
            AND g.is_dummy IS NOT TRUE
            AND g.game_id NOT LIKE 'MOCK%'
          UNION ALL
          SELECT
              UPPER(TRIM(g.home_team)) AS home_team_id,
              UPPER(TRIM(g.away_team)) AS away_team_id,
              g.home_score,
              g.away_score
          FROM game g
          WHERE g.season_id IS NULL
            AND g.game_date >= :seasonStart
            AND g.game_date < :nextSeasonStart
            AND g.home_score IS NOT NULL
            AND g.away_score IS NOT NULL
            AND g.is_dummy IS NOT TRUE
            AND g.game_id NOT LIKE 'MOCK%'
      ),
      team_games AS (
          SELECT
              home_team_id AS team_id,
              home_score AS team_score,
              away_score AS opp_score
          FROM season_games
          UNION ALL
          SELECT
              away_team_id AS team_id,
              away_score AS team_score,
              home_score AS opp_score
          FROM season_games
      ),
      team_stats AS (
          SELECT
              team_id,
              team_id AS team_name,
              SUM(CASE WHEN team_score > opp_score THEN 1 ELSE 0 END) AS wins,
              SUM(CASE WHEN team_score < opp_score THEN 1 ELSE 0 END) AS losses,
              SUM(CASE WHEN team_score = opp_score THEN 1 ELSE 0 END) AS draws,
              COUNT(*) AS games_played
          FROM team_games
          WHERE team_id IS NOT NULL
            AND team_id <> ''
          GROUP BY team_id
      ),
      top_teams AS (
          SELECT team_id
          FROM (
              SELECT
                  team_id,
                  ROW_NUMBER() OVER (ORDER BY games_played DESC, team_id ASC) AS team_order_no
              FROM team_stats
          ) ranked_teams
          WHERE team_order_no <= 10
      ),
      ranked AS (
          SELECT
              ts.team_id,
              ts.team_name,
              ts.wins,
              ts.losses,
              ts.draws,
              ts.games_played,
              RANK() OVER (ORDER BY ts.wins DESC, ts.losses ASC, ts.team_id ASC) AS season_rank,
              FIRST_VALUE(ts.wins) OVER (ORDER BY ts.wins DESC, ts.losses ASC, ts.team_id ASC) AS top_wins,
              FIRST_VALUE(ts.losses) OVER (ORDER BY ts.wins DESC, ts.losses ASC, ts.team_id ASC) AS top_losses
          FROM team_stats ts
          JOIN top_teams tt ON tt.team_id = ts.team_id
      )
      SELECT
          season_rank,
          team_id,
          team_name,
          wins,
          losses,
          draws,
          CASE
              WHEN (wins + losses) > 0
              THEN ROUND(CAST(wins AS DECIMAL) / (wins + losses), 3)
              ELSE 0.000
          END as win_pct,
          games_played,
          ROUND(((top_wins - wins) + (losses - top_losses)) / 2.0, 1) as games_behind
      FROM ranked
      ORDER BY season_rank, team_id
      """, nativeQuery = true)
  List<Object[]> findTeamRankingsBySeasonFast(
      @Param("seasonYear") int seasonYear,
      @Param("seasonStart") LocalDate seasonStart,
      @Param("nextSeasonStart") LocalDate nextSeasonStart);

  /**
   * 특정 시즌의 팀별 순위 데이터 조회 (game 테이블에서 집계)
   * 
   * @param seasonYear 시즌 연도
   * @return 순위 데이터 (Object[] 배열)
   */
  @Query(value = """
      WITH season_games AS (
          SELECT
              UPPER(TRIM(g.home_team)) AS home_team_id,
              UPPER(TRIM(g.away_team)) AS away_team_id,
              g.home_score,
              g.away_score
          FROM game g
          LEFT JOIN kbo_seasons s ON (
              g.season_id = s.season_id
              OR (g.season_id IS NULL AND s.season_year = EXTRACT(YEAR FROM g.game_date))
          )
          WHERE COALESCE(s.season_year, EXTRACT(YEAR FROM g.game_date)) = :seasonYear
            AND COALESCE(s.league_type_code, 0) = 0
            AND g.home_score IS NOT NULL
            AND g.away_score IS NOT NULL
            AND g.is_dummy IS NOT TRUE
            AND g.game_id NOT LIKE 'MOCK%'
      ),
      team_games AS (
          SELECT
              home_team_id AS team_id,
              home_score AS team_score,
              away_score AS opp_score
          FROM season_games
          UNION ALL
          SELECT
              away_team_id AS team_id,
              away_score AS team_score,
              home_score AS opp_score
          FROM season_games
      ),
      team_stats AS (
          SELECT
              team_id,
              team_id AS team_name,
              SUM(CASE WHEN team_score > opp_score THEN 1 ELSE 0 END) AS wins,
              SUM(CASE WHEN team_score < opp_score THEN 1 ELSE 0 END) AS losses,
              SUM(CASE WHEN team_score = opp_score THEN 1 ELSE 0 END) AS draws,
              COUNT(*) AS games_played
          FROM team_games
          WHERE team_id IS NOT NULL
            AND team_id <> ''
          GROUP BY team_id
      ),
      top_teams AS (
          SELECT team_id
          FROM (
              SELECT
                  team_id,
                  ROW_NUMBER() OVER (ORDER BY games_played DESC, team_id ASC) AS team_order_no
              FROM team_stats
          ) ranked_teams
          WHERE team_order_no <= 10
      ),
      ranked AS (
          SELECT
              ts.team_id,
              ts.team_name,
              ts.wins,
              ts.losses,
              ts.draws,
              ts.games_played,
              RANK() OVER (ORDER BY ts.wins DESC, ts.losses ASC, ts.team_id ASC) AS season_rank,
              FIRST_VALUE(ts.wins) OVER (ORDER BY ts.wins DESC, ts.losses ASC, ts.team_id ASC) AS top_wins,
              FIRST_VALUE(ts.losses) OVER (ORDER BY ts.wins DESC, ts.losses ASC, ts.team_id ASC) AS top_losses
          FROM team_stats ts
          JOIN top_teams tt ON tt.team_id = ts.team_id
      )
      SELECT
          season_rank,
          team_id,
          team_name,
          wins,
          losses,
          draws,
          CASE
              WHEN (wins + losses) > 0
              THEN ROUND(CAST(wins AS DECIMAL) / (wins + losses), 3)
              ELSE 0.000
          END as win_pct,
          games_played,
          ROUND(((top_wins - wins) + (losses - top_losses)) / 2.0, 1) as games_behind
      FROM ranked
      ORDER BY season_rank, team_id
      """, nativeQuery = true)
  List<Object[]> findTeamRankingsBySeason(@Param("seasonYear") int seasonYear);

  /**
   * 시즌 연도로 팀 순위를 집계하는 폴백 쿼리
   *
   * @param seasonYear 시즌 연도
   * @return 순위 데이터 (Object[] 배열)
   */
  @Query(value = """
      WITH season_games AS (
          SELECT
              UPPER(TRIM(g.home_team)) AS home_team_id,
              UPPER(TRIM(g.away_team)) AS away_team_id,
              g.home_score,
              g.away_score
          FROM game g
          LEFT JOIN kbo_seasons s ON g.season_id = s.season_id
          WHERE EXTRACT(YEAR FROM g.game_date) = :seasonYear
            AND (s.season_id IS NULL OR COALESCE(s.league_type_code, 0) = 0)
            AND g.home_score IS NOT NULL
            AND g.away_score IS NOT NULL
            AND g.is_dummy IS NOT TRUE
            AND g.game_id NOT LIKE 'MOCK%'
      ),
      team_games AS (
          SELECT
              home_team_id AS team_id,
              home_score AS team_score,
              away_score AS opp_score
          FROM season_games
          UNION ALL
          SELECT
              away_team_id AS team_id,
              away_score AS team_score,
              home_score AS opp_score
          FROM season_games
      ),
      team_stats AS (
          SELECT
              team_id,
              team_id AS team_name,
              SUM(CASE WHEN team_score > opp_score THEN 1 ELSE 0 END) AS wins,
              SUM(CASE WHEN team_score < opp_score THEN 1 ELSE 0 END) AS losses,
              SUM(CASE WHEN team_score = opp_score THEN 1 ELSE 0 END) AS draws,
              COUNT(*) AS games_played
          FROM team_games
          WHERE team_id IS NOT NULL
            AND team_id <> ''
          GROUP BY team_id
      ),
      top_teams AS (
          SELECT team_id
          FROM (
              SELECT
                  team_id,
                  ROW_NUMBER() OVER (ORDER BY games_played DESC, team_id ASC) AS team_order_no
              FROM team_stats
          ) ranked_teams
          WHERE team_order_no <= 10
      ),
      ranked AS (
          SELECT
              ts.team_id,
              ts.team_name,
              ts.wins,
              ts.losses,
              ts.draws,
              ts.games_played,
              RANK() OVER (ORDER BY ts.wins DESC, ts.losses ASC, ts.team_id ASC) AS season_rank,
              FIRST_VALUE(ts.wins) OVER (ORDER BY ts.wins DESC, ts.losses ASC, ts.team_id ASC) AS top_wins,
              FIRST_VALUE(ts.losses) OVER (ORDER BY ts.wins DESC, ts.losses ASC, ts.team_id ASC) AS top_losses
          FROM team_stats ts
          JOIN top_teams tt ON tt.team_id = ts.team_id
      )
      SELECT
          season_rank,
          team_id,
          team_name,
          wins,
          losses,
          draws,
          CASE
              WHEN (wins + losses) > 0
              THEN ROUND(CAST(wins AS DECIMAL) / (wins + losses), 3)
              ELSE 0.000
          END as win_pct,
          games_played,
          ROUND(((top_wins - wins) + (losses - top_losses)) / 2.0, 1) as games_behind
      FROM ranked
      ORDER BY season_rank, team_id
      """, nativeQuery = true)
  List<Object[]> findTeamRankingsBySeasonFallback(@Param("seasonYear") int seasonYear);

  /**
   * 한국시리즈 우승팀 조회
   * 
   * @param seasonYear 시즌 연도
   * @return 우승 팀 ID
   */
	  @Query(value = """
	      SELECT g.winning_team
	      FROM game g
	      JOIN kbo_seasons s ON (
	          g.season_id = s.season_id
	          OR (g.season_id IS NULL AND s.season_year = EXTRACT(YEAR FROM g.game_date))
	      )
	      WHERE COALESCE(s.league_type_code, 5) = 5
	        AND UPPER(TRIM(g.game_status)) IN ('COMPLETED', 'FINAL', 'FINISHED', 'DONE', 'END', 'E', 'F')
	        AND COALESCE(s.season_year, EXTRACT(YEAR FROM g.game_date)) = :seasonYear
	        AND NOT EXISTS (
	            SELECT 1
	            FROM game later_game
	            JOIN kbo_seasons later_season ON (
	                later_game.season_id = later_season.season_id
	                OR (later_game.season_id IS NULL AND later_season.season_year = EXTRACT(YEAR FROM later_game.game_date))
	            )
	            WHERE COALESCE(later_season.league_type_code, 5) = 5
	              AND UPPER(TRIM(later_game.game_status)) IN ('COMPLETED', 'FINAL', 'FINISHED', 'DONE', 'END', 'E', 'F')
	              AND COALESCE(later_season.season_year, EXTRACT(YEAR FROM later_game.game_date)) = :seasonYear
	              AND (
	                  later_game.game_date > g.game_date
	                  OR (later_game.game_date = g.game_date AND later_game.game_id > g.game_id)
	              )
	        )
	      """, nativeQuery = true)
	  String findChampionBySeason(@Param("seasonYear") int seasonYear);

  /**
   * 정규시즌 시작 날짜 조회
   * 
   * @param seasonYear 시즌 연도
   * @return 시작 날짜
   */
  @Query(value = """
      SELECT MIN(g.game_date)
      FROM game g
      JOIN kbo_seasons s ON g.season_id = s.season_id
      WHERE s.season_year = :seasonYear
        AND s.league_type_code = 0
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
      """, nativeQuery = true)
  Optional<LocalDate> findFirstRegularSeasonDate(@Param("seasonYear") int seasonYear);

  /**
   * 포스트시즌 시작 날짜 조회
   * 
   * @param seasonYear 시즌 연도
   * @return 시작 날짜
   */
  @Query(value = """
      SELECT MIN(g.game_date)
      FROM game g
      JOIN kbo_seasons s ON g.season_id = s.season_id
      WHERE s.season_year = :seasonYear
        AND s.league_type_code = 2
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
      """, nativeQuery = true)
  Optional<LocalDate> findFirstPostseasonDate(@Param("seasonYear") int seasonYear);

  /**
   * 한국시리즈 시작 날짜 조회
   * 
   * @param seasonYear 시즌 연도
   * @return 시작 날짜
   */
  @Query(value = """
      SELECT MIN(g.game_date)
      FROM game g
      JOIN kbo_seasons s ON g.season_id = s.season_id
      WHERE s.season_year = :seasonYear
        AND s.league_type_code = 5
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
      """, nativeQuery = true)
  Optional<LocalDate> findFirstKoreanSeriesDate(@Param("seasonYear") int seasonYear);

  /**
   * 특정 리그 타입의 해당 시즌 연도 기준 첫 경기 날짜 조회
   */
  @Query(value = """
      SELECT MIN(g.game_date)
      FROM game g
      JOIN kbo_seasons s ON (
          g.season_id = s.season_id
          OR (g.season_id IS NULL AND s.season_year = EXTRACT(YEAR FROM g.game_date))
      )
      WHERE COALESCE(s.season_year, EXTRACT(YEAR FROM g.game_date)) = :seasonYear
        AND COALESCE(s.league_type_code, 0) = :leagueTypeCode
      """, nativeQuery = true)
  Optional<LocalDate> findFirstStartDateByTypeFromSeasonYear(
      @Param("leagueTypeCode") int leagueTypeCode,
      @Param("seasonYear") int seasonYear);

  /**
   * kbo_seasons 테이블 기준 시즌/리그 타입 시작일 조회
   */
	  @Query(value = """
	      SELECT s.start_date
	      FROM kbo_seasons s
	      WHERE s.season_year = :seasonYear
	        AND s.league_type_code = :leagueTypeCode
	        AND s.start_date IS NOT NULL
	        AND NOT EXISTS (
	            SELECT 1
	            FROM kbo_seasons later
	            WHERE later.season_year = :seasonYear
	              AND later.league_type_code = :leagueTypeCode
	              AND later.start_date IS NOT NULL
	              AND later.season_id > s.season_id
	        )
	      """, nativeQuery = true)
	  Optional<LocalDate> findConfiguredStartDateByTypeFromSeasonYear(
      @Param("leagueTypeCode") int leagueTypeCode,
      @Param("seasonYear") int seasonYear);

  /**
   * 특정 리그 타입의 최근 시작일 조회 (기준일 이전)
   */
	  @Query(value = """
	      SELECT MAX(s.start_date)
	      FROM kbo_seasons s
	      WHERE s.league_type_code = :leagueTypeCode
	        AND s.start_date IS NOT NULL
	        AND s.start_date <= :asOfDate
	      """, nativeQuery = true)
	  Optional<LocalDate> findLatestStartDateByTypeAsOf(
          @Param("leagueTypeCode") int leagueTypeCode,
          @Param("asOfDate") LocalDate asOfDate);

  /**
   * 특정 리그 타입의 최근 시작일 조회 (기준일 제한 없음)
   */
	  @Query(value = """
	      SELECT MAX(s.start_date)
	      FROM kbo_seasons s
	      WHERE s.league_type_code = :leagueTypeCode
	        AND s.start_date IS NOT NULL
	      """, nativeQuery = true)
	  Optional<LocalDate> findLatestStartDateByType(@Param("leagueTypeCode") int leagueTypeCode);

  /**
   * 이전 경기 날짜 조회 (현재 날짜 기준 과거 중 가장 최근)
   * 
   * @param date 기준 날짜
   * @return 이전 경기 날짜
   */
  @Query("SELECT MAX(g.gameDate) FROM GameEntity g WHERE g.gameDate < :date")
  Optional<LocalDate> findPrevGameDate(@Param("date") LocalDate date);

  /**
   * 다음 경기 날짜 조회 (현재 날짜 기준 미래 중 가장 가까운)
   * 
   * @param date 기준 날짜
   * @return 다음 경기 날짜
   */
  @Query("SELECT MIN(g.gameDate) FROM GameEntity g WHERE g.gameDate > :date")
  Optional<LocalDate> findNextGameDate(@Param("date") LocalDate date);

  @Query(value = """
      SELECT MIN(g.game_date)
      FROM game g
      LEFT JOIN kbo_seasons s ON g.season_id = s.season_id
      WHERE g.game_date >= :date
        AND COALESCE(s.season_year, EXTRACT(YEAR FROM g.game_date)) = :seasonYear
        AND COALESCE(s.league_type_code, 0) IN (:leagueTypeCodes)
        AND UPPER(COALESCE(g.game_status, '')) NOT IN (:excludedStatuses)
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
      """, nativeQuery = true)
  Optional<LocalDate> findScopedGameDateOnOrAfter(
      @Param("date") LocalDate date,
      @Param("seasonYear") int seasonYear,
      @Param("leagueTypeCodes") Collection<Integer> leagueTypeCodes,
      @Param("excludedStatuses") Collection<String> excludedStatuses);

  @Query(value = """
      SELECT MAX(g.game_date)
      FROM game g
      LEFT JOIN kbo_seasons s ON g.season_id = s.season_id
      WHERE g.game_date < :date
        AND COALESCE(s.season_year, EXTRACT(YEAR FROM g.game_date)) = :seasonYear
        AND COALESCE(s.league_type_code, 0) IN (:leagueTypeCodes)
        AND UPPER(COALESCE(g.game_status, '')) NOT IN (:excludedStatuses)
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
      """, nativeQuery = true)
  Optional<LocalDate> findPrevScopedGameDate(
      @Param("date") LocalDate date,
      @Param("seasonYear") int seasonYear,
      @Param("leagueTypeCodes") Collection<Integer> leagueTypeCodes,
      @Param("excludedStatuses") Collection<String> excludedStatuses);

  @Query(value = """
      SELECT MIN(g.game_date)
      FROM game g
      LEFT JOIN kbo_seasons s ON g.season_id = s.season_id
      WHERE g.game_date > :date
        AND COALESCE(s.season_year, EXTRACT(YEAR FROM g.game_date)) = :seasonYear
        AND COALESCE(s.league_type_code, 0) IN (:leagueTypeCodes)
        AND UPPER(COALESCE(g.game_status, '')) NOT IN (:excludedStatuses)
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
      """, nativeQuery = true)
  Optional<LocalDate> findNextScopedGameDate(
      @Param("date") LocalDate date,
      @Param("seasonYear") int seasonYear,
      @Param("leagueTypeCodes") Collection<Integer> leagueTypeCodes,
      @Param("excludedStatuses") Collection<String> excludedStatuses);

  @Query(value = """
      SELECT MIN(g.game_date)
      FROM game g
      WHERE g.game_date >= :date
        AND UPPER(g.game_status) IN (:statuses)
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
      """, nativeQuery = true)
  Optional<LocalDate> findScheduledNavigationDateOnOrAfter(
      @Param("date") LocalDate date,
      @Param("statuses") Collection<String> statuses);

  @Query(value = """
      SELECT MAX(g.game_date)
      FROM game g
      WHERE g.game_date < :date
        AND UPPER(g.game_status) IN (:statuses)
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
      """, nativeQuery = true)
  Optional<LocalDate> findPrevScheduledNavigationDate(
      @Param("date") LocalDate date,
      @Param("statuses") Collection<String> statuses);

  @Query(value = """
      SELECT MIN(g.game_date)
      FROM game g
      WHERE g.game_date > :date
        AND UPPER(g.game_status) IN (:statuses)
        AND g.is_dummy IS NOT TRUE
        AND g.game_id NOT LIKE 'MOCK%'
      """, nativeQuery = true)
  Optional<LocalDate> findNextScheduledNavigationDate(
      @Param("date") LocalDate date,
      @Param("statuses") Collection<String> statuses);

  /**
   * season_id로 리그 타입 코드 조회
   */
	  @Query(value = """
	      SELECT s.league_type_code
	      FROM kbo_seasons s
	      WHERE s.season_id = :seasonId
	      """, nativeQuery = true)
	  Optional<Integer> findLeagueTypeCodeBySeasonId(@Param("seasonId") Integer seasonId);

  @Query(value = """
      SELECT
          s.season_id AS "seasonId",
          s.season_year AS "seasonYear",
          s.league_type_code AS "leagueTypeCode",
          s.league_type_name AS "leagueTypeName"
	      FROM kbo_seasons s
	      WHERE s.season_id = :seasonId
	      """, nativeQuery = true)
	  Optional<SeasonInfoProjection> findSeasonInfoBySeasonId(@Param("seasonId") Integer seasonId);
}
