package com.example.ai.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiIngestPropertiesTest {

    @Test
    void tablesRejectNonAllowlistedDatabaseSources() {
        AiIngestProperties properties = new AiIngestProperties();

        assertThatThrownBy(() -> properties.setTables(List.of("game", "users")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trusted ingestion source");
    }

    @Test
    void monitoringIntervalsMustBePositive() {
        AiIngestProperties properties = new AiIngestProperties();

        assertThatThrownBy(() -> properties.setCheckInterval(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("check interval");
        assertThatThrownBy(() -> properties.setMonitoringDuration(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("monitoring duration");
    }
}
