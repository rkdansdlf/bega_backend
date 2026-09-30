package com.example.common.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FlywayConfigTest {

    @Test
    @DisplayName("auto-repair가 꺼져 있으면 migrate만 수행한다")
    void autoRepairDisabledSkipsRepair() {
        Flyway flyway = mock(Flyway.class);
        FlywayConfig config = new FlywayConfig();

        config.flywayMigrationStrategy(false, new MockEnvironment().withProperty("spring.profiles.active", "prod"))
                .migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }

    @Test
    @DisplayName("개발 환경에서 auto-repair가 켜져 있으면 repair 후 migrate를 수행한다")
    void autoRepairEnabledOutsideProductionRepairsBeforeMigrate() {
        Flyway flyway = mock(Flyway.class);
        FlywayConfig config = new FlywayConfig();

        config.flywayMigrationStrategy(true, new MockEnvironment().withProperty("spring.profiles.active", "dev"))
                .migrate(flyway);

        verify(flyway).repair();
        verify(flyway).migrate();
    }

    @Test
    @DisplayName("운영 환경은 명시적으로 요청된 auto-repair도 거부한다")
    void productionRejectsAutoRepair() {
        Flyway flyway = mock(Flyway.class);
        FlywayConfig config = new FlywayConfig();

        assertThrows(IllegalStateException.class,
                () -> config.flywayMigrationStrategy(
                                true,
                                new MockEnvironment().withProperty("spring.profiles.active", "prod"))
                        .migrate(flyway));

        verify(flyway, never()).repair();
        verify(flyway, never()).migrate();
    }
}
