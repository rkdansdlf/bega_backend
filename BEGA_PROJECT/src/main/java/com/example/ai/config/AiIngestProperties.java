package com.example.ai.config;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.ai-ingest")
public class AiIngestProperties {

    private static final Set<String> TRUSTED_SOURCE_TABLES = Set.of(
            "teams",
            "team_franchises",
            "team_history",
            "stadiums",
            "kbo_seasons",
            "player_basic",
            "awards",
            "player_movements",
            "player_season_batting",
            "player_season_pitching",
            "team_season_batting",
            "team_season_pitching",
            "stat_rankings",
            "game",
            "game_metadata",
            "game_flow_summary",
            "game_lineups",
            "game_batting_stats",
            "game_pitching_stats",
            "game_summary",
            "kbo_metrics_explained",
            "markdown_docs_rules_terms",
            "markdown_docs_strategy_metrics",
            "markdown_docs_culture_history",
            "markdown_docs_2025_storylines",
            "markdown_docs_chatbot_kb_v2",
            "kbo_regulations_basic",
            "kbo_regulations_player",
            "kbo_regulations_game",
            "kbo_regulations_technical",
            "kbo_regulations_discipline",
            "kbo_regulations_postseason",
            "kbo_regulations_special",
            "kbo_regulations_terms");

    private boolean enabled;
    private String cron = "30 4 * * *";
    private List<String> tables = List.of("game", "game_metadata", "game_summary");
    private Integer seasonYear;
    private Duration checkInterval = Duration.ofSeconds(30);
    private Duration monitoringDuration = Duration.ofHours(2);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public List<String> getTables() {
        return tables;
    }

    public void setTables(List<String> tables) {
        List<String> normalized = tables == null
                ? List.of()
                : tables.stream()
                        .map(table -> table == null ? "" : table.trim().toLowerCase(Locale.ROOT))
                        .distinct()
                        .toList();
        if (normalized.isEmpty()
                || normalized.size() > TRUSTED_SOURCE_TABLES.size()
                || normalized.stream().anyMatch(table -> table.length() > 128)
                || !TRUSTED_SOURCE_TABLES.containsAll(normalized)) {
            throw new IllegalArgumentException("unsupported trusted ingestion source table");
        }
        this.tables = normalized;
    }

    public Integer getSeasonYear() {
        return seasonYear;
    }

    public void setSeasonYear(Integer seasonYear) {
        this.seasonYear = seasonYear;
    }

    public Duration getCheckInterval() {
        return checkInterval;
    }

    public void setCheckInterval(Duration checkInterval) {
        if (checkInterval == null || checkInterval.isZero() || checkInterval.isNegative()) {
            throw new IllegalArgumentException("AI ingestion check interval must be positive");
        }
        this.checkInterval = checkInterval;
    }

    public Duration getMonitoringDuration() {
        return monitoringDuration;
    }

    public void setMonitoringDuration(Duration monitoringDuration) {
        if (monitoringDuration == null
                || monitoringDuration.isZero()
                || monitoringDuration.isNegative()) {
            throw new IllegalArgumentException("AI ingestion monitoring duration must be positive");
        }
        this.monitoringDuration = monitoringDuration;
    }
}
