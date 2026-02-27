package com.example.kbo.repository;

import com.example.kbo.entity.GameEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * GameRepository - 통합 경기 Repository
 *
 * 기존 MatchRepository, BegaGameRepository, HomePageGameRepository를 통합
 */
@Repository
public interface GameRepository extends JpaRepository<GameEntity, Long> {

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
      "AND g.isDummy IS NOT TRUE " +
      "AND g.gameId NOT LIKE 'MOCK%' " +
      "AND g.homeTeam IN :canonicalTeams " +
      "AND g.awayTeam IN :canonicalTeams " +
      "ORDER BY g.gameDate ASC, g.gameId ASC")
  Page<GameEntity> findCanonicalByDateRange(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("canonicalTeams") List<String> canonicalTeams,
      Pageable pageable);

  @Query("SELECT MIN(g.gameDate) FROM GameEntity g " +
      "WHERE g.isDummy IS NOT TRUE " +
      "AND g.gameId NOT LIKE 'MOCK%' " +
      "AND g.homeTeam IN :canonicalTeams " +
      "AND g.awayTeam IN :canonicalTeams")
  Optional<LocalDate> findCanonicalMinGameDate(
      @Param("canonicalTeams") List<String> canonicalTeams);

  @Query("SELECT MAX(g.gameDate) FROM GameEntity g " +
      "WHERE g.isDummy IS NOT TRUE " +
      "AND g.gameId NOT LIKE 'MOCK%' " +
      "AND g.homeTeam IN :canonicalTeams " +
      "AND g.awayTeam IN :canonicalTeams")
  Optional<LocalDate> findCanonicalMaxGameDate(
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
          FROM team_stats
          ORDER BY games_played DESC, team_id ASC
          FETCH FIRST 10 ROWS ONLY
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
          FROM team_stats
          ORDER BY games_played DESC, team_id ASC
          FETCH FIRST 10 ROWS ONLY
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
      ORDER BY g.game_date DESC, g.game_id DESC
      FETCH FIRST 1 ROWS ONLY
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
      JOIN kbo_seasons s ON (
          g.season_id = s.season_id
          OR (g.season_id IS NULL AND s.season_year = EXTRACT(YEAR FROM g.game_date))
      )
      WHERE COALESCE(s.season_year, EXTRACT(YEAR FROM g.game_date)) = :seasonYear
        AND COALESCE(s.league_type_code, 0) = 0
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
      JOIN kbo_seasons s ON (
          g.season_id = s.season_id
          OR (g.season_id IS NULL AND s.season_year = EXTRACT(YEAR FROM g.game_date))
      )
      WHERE COALESCE(s.season_year, EXTRACT(YEAR FROM g.game_date)) = :seasonYear
        AND COALESCE(s.league_type_code, 0) = 2
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
      JOIN kbo_seasons s ON (
          g.season_id = s.season_id
          OR (g.season_id IS NULL AND s.season_year = EXTRACT(YEAR FROM g.game_date))
      )
      WHERE COALESCE(s.season_year, EXTRACT(YEAR FROM g.game_date)) = :seasonYear
        AND COALESCE(s.league_type_code, 0) = 5
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
   * 특정 리그 타입의 최근 시작일 조회 (기준일 이전)
   */
  @Query(value = """
      SELECT s.start_date
      FROM kbo_seasons s
      WHERE s.league_type_code = :leagueTypeCode
        AND s.start_date IS NOT NULL
        AND s.start_date <= :asOfDate
      ORDER BY s.season_year DESC
      FETCH FIRST 1 ROWS ONLY
      """, nativeQuery = true)
  Optional<LocalDate> findLatestStartDateByTypeAsOf(
          @Param("leagueTypeCode") int leagueTypeCode,
          @Param("asOfDate") LocalDate asOfDate);

  /**
   * 특정 리그 타입의 최근 시작일 조회 (기준일 제한 없음)
   */
  @Query(value = """
      SELECT s.start_date
      FROM kbo_seasons s
      WHERE s.league_type_code = :leagueTypeCode
        AND s.start_date IS NOT NULL
      ORDER BY s.season_year DESC
      FETCH FIRST 1 ROWS ONLY
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

  /**
   * season_id로 리그 타입 코드 조회
   */
  @Query(value = """
      SELECT s.league_type_code
      FROM kbo_seasons s
      WHERE s.season_id = :seasonId
      FETCH FIRST 1 ROWS ONLY
      """, nativeQuery = true)
  Optional<Integer> findLeagueTypeCodeBySeasonId(@Param("seasonId") Integer seasonId);
}
