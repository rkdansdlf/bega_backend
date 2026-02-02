package com.example.kbo.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
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
}
