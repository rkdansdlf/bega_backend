package com.example.common.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FlywayConfigTest {

    @Test
    @DisplayName("auto-repair가 꺼져 있으면 migrate만 수행한다")
    void autoRepairDisabledSkipsRepair() {
        Flyway flyway = mock(Flyway.class);
        FlywayConfig config = new FlywayConfig();

        config.flywayMigrationStrategy(false).migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }

    @Test
    @DisplayName("auto-repair가 켜져 있으면 repair 후 migrate를 수행한다")
    void autoRepairEnabledRepairsBeforeMigrate() {
        Flyway flyway = mock(Flyway.class);
        FlywayConfig config = new FlywayConfig();

        config.flywayMigrationStrategy(true).migrate(flyway);

        verify(flyway).repair();
        verify(flyway).migrate();
    }
}
