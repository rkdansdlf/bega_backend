package com.example.ai.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.ai-ingest")
public class AiIngestProperties {

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
        this.tables = tables == null ? List.of() : List.copyOf(tables);
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
        this.checkInterval = checkInterval;
    }

    public Duration getMonitoringDuration() {
        return monitoringDuration;
    }

    public void setMonitoringDuration(Duration monitoringDuration) {
        this.monitoringDuration = monitoringDuration;
    }
}
