package com.example.kbo.repository;

import com.example.kbo.entity.GameEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
      WITH team_stats AS (
          SELECT
              tf.current_code as team_id,
              MAX(tf.name) as team_name,
              COALESCE(SUM(CASE WHEN g.winning_team = t.team_id THEN 1 ELSE 0 END), 0) as wins,
              COALESCE(SUM(CASE
                  WHEN (g.home_team = t.team_id AND g.winning_team IS NOT NULL AND g.winning_team != t.team_id)
                    OR (g.away_team = t.team_id AND g.winning_team IS NOT NULL AND g.winning_team != t.team_id)
                  THEN 1 ELSE 0 END), 0) as losses,
              COALESCE(SUM(CASE
                  WHEN (g.home_team = t.team_id OR g.away_team = t.team_id)
                       AND g.winning_team IS NULL
                       AND g.game_status = 'COMPLETED'
                  THEN 1 ELSE 0 END), 0) as draws,
              COALESCE(COUNT(CASE
                  WHEN (g.home_team = t.team_id OR g.away_team = t.team_id)
                       AND g.game_status = 'COMPLETED'
                  THEN 1 END), 0) as games_played
          FROM teams t
          JOIN team_franchises tf ON t.franchise_id = tf.id
          LEFT JOIN game g ON (g.home_team = t.team_id OR g.away_team = t.team_id)
          JOIN kbo_seasons s ON g.season_id = s.season_id
          WHERE s.season_year = :seasonYear
            AND s.league_type_code = 0
            AND g.game_status = 'COMPLETED'
          GROUP BY tf.id, tf.current_code
      )
      SELECT
          RANK() OVER (ORDER BY wins DESC, losses ASC) as season_rank,
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
          ROUND(
              (
                  (FIRST_VALUE(wins) OVER (ORDER BY wins DESC, losses ASC) - wins)
                  + (losses - FIRST_VALUE(losses) OVER (ORDER BY wins DESC, losses ASC))
              ) / 2.0,
              1
          ) as games_behind
      FROM team_stats
      WHERE games_played > 0
      ORDER BY season_rank
      """, nativeQuery = true)
  List<Object[]> findTeamRankingsBySeason(@Param("seasonYear") int seasonYear);

  /**
   * 한국시리즈 우승팀 조회
   * 
   * @param seasonYear 시즌 연도
   * @return 우승 팀 ID
   */
  @Query(value = """
      SELECT g.winning_team
      FROM game g
      JOIN kbo_seasons s ON g.season_id = s.season_id
      WHERE s.league_type_code = 5
        AND g.game_status = 'COMPLETED'
        AND s.season_year = :seasonYear
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
      SELECT s.start_date
      FROM kbo_seasons s
      WHERE s.season_year = :seasonYear
        AND s.league_type_code = 0
      """, nativeQuery = true)
  Optional<LocalDate> findFirstRegularSeasonDate(@Param("seasonYear") int seasonYear);

  /**
   * 포스트시즌 시작 날짜 조회
   * 
   * @param seasonYear 시즌 연도
   * @return 시작 날짜
   */
  @Query(value = """
      SELECT s.start_date
      FROM kbo_seasons s
      WHERE s.season_year = :seasonYear
        AND s.league_type_code = 2
      FETCH FIRST 1 ROWS ONLY
      """, nativeQuery = true)
  Optional<LocalDate> findFirstPostseasonDate(@Param("seasonYear") int seasonYear);

  /**
   * 한국시리즈 시작 날짜 조회
   * 
   * @param seasonYear 시즌 연도
   * @return 시작 날짜
   */
  @Query(value = """
      SELECT s.start_date
      FROM kbo_seasons s
      WHERE s.season_year = :seasonYear
        AND s.league_type_code = 5
      FETCH FIRST 1 ROWS ONLY
      """, nativeQuery = true)
  Optional<LocalDate> findFirstKoreanSeriesDate(@Param("seasonYear") int seasonYear);

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
}
