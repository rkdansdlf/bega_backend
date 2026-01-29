package com.example.kbo.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class CheckOracleController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/counts")
    public Map<String, Object> getCounts() {
        Map<String, Object> counts = new HashMap<>();
        String[] tables = { "game", "game_summary", "game_events", "game_play_by_play", "teams", "kbo_seasons",
                "player_basic", "team_franchises" };

        for (String table : tables) {
            try {
                Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
                counts.put(table, count);
            } catch (Exception e) {
                counts.put(table + "_error", e.getMessage());
            }
        }
        return counts;
    }

    @GetMapping("/inspect-metadata")
    public Map<String, Object> inspectMetadata() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("game_summary", jdbcTemplate.queryForList("SELECT * FROM game_summary FETCH FIRST 5 ROWS ONLY"));
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    @GetMapping("/inspect-franchises")
    public Map<String, Object> inspectFranchises() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("team_franchises", jdbcTemplate.queryForList("SELECT * FROM team_franchises ORDER BY id"));
            result.put("teams", jdbcTemplate
                    .queryForList("SELECT team_id, team_name, franchise_id, is_active FROM teams ORDER BY team_id"));
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    @GetMapping("/inspect-history")
    public Map<String, Object> inspectHistory() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("history",
                    jdbcTemplate.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC"));
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    @GetMapping("/inspect-offseason-data")
    public Map<String, Object> inspectOffseasonData() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("count", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_movements", Long.class));
            result.put("recent_rows", jdbcTemplate.queryForList(
                    "SELECT * FROM player_movements ORDER BY movement_date DESC FETCH FIRST 20 ROWS ONLY"));
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    @GetMapping("/seed-offseason-data")
    public Map<String, Object> seedOffseasonData() {
        Map<String, Object> result = new HashMap<>();
        try {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_movements", Long.class);
            if (count == 0) {
                jdbcTemplate.update(
                        "INSERT INTO player_movements (id, player_name, team_code, section, movement_date, remarks, created_at, updated_at) VALUES (1, 'Heo Gyeong-min', 'KT', 'FA Contract', TO_DATE('2024-11-20', 'YYYY-MM-DD'), '4Y 40B KRW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                jdbcTemplate.update(
                        "INSERT INTO player_movements (id, player_name, team_code, section, movement_date, remarks, created_at, updated_at) VALUES (2, 'Sim Woo-jun', 'HH', 'FA Contract', TO_DATE('2024-11-25', 'YYYY-MM-DD'), '4Y 50B KRW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                jdbcTemplate.update(
                        "INSERT INTO player_movements (id, player_name, team_code, section, movement_date, remarks, created_at, updated_at) VALUES (3, 'Jang Hyun-sik', 'LG', 'FA Contract', TO_DATE('2024-12-05', 'YYYY-MM-DD'), '4Y 52B KRW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                result.put("status", "Seeded 3 rows.");
            } else {
                result.put("status", "Table already has data. Skipped seeding.");
            }
            result.put("count", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_movements", Long.class));
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }
}
