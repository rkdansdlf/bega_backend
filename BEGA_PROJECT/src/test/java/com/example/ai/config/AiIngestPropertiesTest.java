package com.example.ai.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
