package com.example.homePage;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HomePageGameRepository extends JpaRepository<HomePageGame, Long>{

	// 특정 날짜의 경기 목록 조회
	List<HomePageGame> findByGameDate(LocalDate gameDate);

	// 특정 시즌의 팀별 순위 데이터 조회 (v_team_rank_all 뷰 사용)
    @Query(value = """
        SELECT
            season_rank,
            team_id,
            team_name,
            wins,
            losses,
            draws,
            win_pct,
            games_played
        FROM v_team_rank_all
        WHERE season_year = :seasonYear
        ORDER BY season_rank
        """, nativeQuery = true)
    List<Object[]> findTeamRankingsBySeason(@Param("seasonYear") int seasonYear);

    // 한국시리즈 우승팀 조회
    @Query(value = """
        SELECT g.winning_team
        FROM game g
        JOIN kbo_seasons s ON g.season_id = s.season_id
        WHERE s.league_type_code = 5
          AND g.game_status = 'COMPLETED'
          AND s.season_year = :seasonYear
        ORDER BY g.game_date DESC, g.game_id DESC
        LIMIT 1
        """, nativeQuery = true)
    String findChampionBySeason(@Param("seasonYear") int seasonYear);
    
    // 정규시즌 첫 경기 날짜 조회 3월 22일 ~ 10월 5일 사이의 가장 빠른 경기 날짜
    
   @Query(value = """
       SELECT MIN(g.game_date)
       FROM game g
       JOIN kbo_seasons s ON g.season_id = s.season_id
       WHERE s.season_year = :seasonYear
         AND s.league_type_code = 1
       """, nativeQuery = true)
   Optional<LocalDate> findFirstRegularSeasonDate(@Param("seasonYear") int seasonYear);

   
   // 포스트시즌 첫 경기 날짜 조회 10월 6일 ~ 10월 25일 사이의 가장 빠른 경기 날짜
   @Query(value = """
       SELECT MIN(g.game_date)
       FROM game g
       JOIN kbo_seasons s ON g.season_id = s.season_id
       WHERE s.season_year = :seasonYear
         AND s.league_type_code = 2
       """, nativeQuery = true)
   Optional<LocalDate> findFirstPostseasonDate(@Param("seasonYear") int seasonYear);

   // 한국시리즈 첫 경기 날짜 조회 10월 26일 ~ 10월 31일 사이의 가장 빠른 경기 날짜
   @Query(value = """
       SELECT MIN(g.game_date)
       FROM game g
       JOIN kbo_seasons s ON g.season_id = s.season_id
       WHERE s.season_year = :seasonYear
         AND s.league_type_code = 5
       """, nativeQuery = true)
   Optional<LocalDate> findFirstKoreanSeriesDate(@Param("seasonYear") int seasonYear);
    
}