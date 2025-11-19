package com.example.homePage;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HomePageGameRepository extends JpaRepository<HomePageGame, Long>{

	// 특정 날짜의 경기 목록 조회
	List<HomePageGame> findByGameDate(LocalDate gameDate);

	// 특정 시즌의 팀별 순위 데이터 조회
    @Query(value = """
        WITH season_games AS (
            SELECT
                g.home_team,
                g.away_team,
                g.winning_team,
                g.home_score,
                g.away_score,
                CASE
                    WHEN g.home_score = g.away_score THEN 'DRAW'
                    ELSE NULL
                END AS is_draw
            FROM game g
            JOIN kbo_seasons s ON g.season_id = s.season_id
            WHERE s.season_year = :seasonYear
              AND s.league_type_code = 1
              AND g.game_status = 'COMPLETED'
        ),
        team_stats AS (
            SELECT
                team_id,
                SUM(CASE WHEN winning_team = team_id THEN 1 ELSE 0 END) AS wins,
                SUM(CASE
                    WHEN (home_team = team_id AND winning_team = away_team) OR
                         (away_team = team_id AND winning_team = home_team)
                    THEN 1 ELSE 0
                END) AS losses,
                SUM(CASE WHEN is_draw = 'DRAW' THEN 1 ELSE 0 END) AS draws
            FROM (
                SELECT home_team AS team_id, winning_team, away_team, home_team, is_draw FROM season_games
                UNION ALL
                SELECT away_team AS team_id, winning_team, away_team, home_team, is_draw FROM season_games
            ) combined
            GROUP BY team_id
        )
        SELECT
            RANK() OVER (ORDER BY
                CAST(wins AS FLOAT) / NULLIF(wins + losses, 0) DESC,
                wins DESC
            ) AS rank,
            ts.team_id,
            t.team_name,
            ts.wins,
            ts.losses,
            ts.draws,
            ROUND(CAST(ts.wins AS DOUBLE) / NULLIF(ts.wins + ts.losses, 0), 3) AS win_rate,
            (ts.wins + ts.losses + ts.draws) AS games
        FROM team_stats ts
        JOIN teams t ON ts.team_id = t.team_id
        ORDER BY rank
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
}