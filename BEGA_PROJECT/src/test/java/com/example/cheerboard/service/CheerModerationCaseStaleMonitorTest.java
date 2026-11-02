package com.example.cheerboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cheerboard.repo.CheerReportRepo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class CheerModerationCaseStaleMonitorTest {

    @Mock
    private CheerReportRepo reportRepo;

    private CheerModerationCaseStaleMonitor monitor;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        monitor = new CheerModerationCaseStaleMonitor(reportRepo, new SimpleMeterRegistry());
        appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(CheerModerationCaseStaleMonitor.class)).addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(CheerModerationCaseStaleMonitor.class)).detachAppender(appender);
    }

    @Test
    void tierFollowsTheThresholds() {
        assertThat(CheerModerationCaseStaleMonitor.tierFor(Duration.ofDays(6))).isEqualTo(0);
        assertThat(CheerModerationCaseStaleMonitor.tierFor(Duration.ofDays(7))).isEqualTo(1);
        assertThat(CheerModerationCaseStaleMonitor.tierFor(Duration.ofDays(14))).isEqualTo(2);
        assertThat(CheerModerationCaseStaleMonitor.tierFor(Duration.ofDays(30))).isEqualTo(3);
    }

    @Test
    void eachTierAlertsOnceAndThe30DayTierEscalatesAsError() {
        LocalDateTime filed = LocalDateTime.now().minusDays(8);
        List<Object[]> open = List.<Object[]>of(new Object[] {1L, filed});
        when(reportRepo.findOpenCasesCreatedBefore(any())).thenReturn(open);

        LocalDateTime day8 = filed.plusDays(8);
        assertThat(monitor.checkStaleCases(day8)).isEqualTo(1);
        assertThat(monitor.checkStaleCases(day8.plusHours(1))).isEqualTo(1);
        assertThat(levels()).containsExactly(Level.WARN);

        monitor.checkStaleCases(filed.plusDays(15));
        assertThat(levels()).containsExactly(Level.WARN, Level.WARN);

        monitor.checkStaleCases(filed.plusDays(31));
        assertThat(levels()).containsExactly(Level.WARN, Level.WARN, Level.ERROR);
    }

    @Test
    void closedCaseIsForgottenSoAReopenedCaseAlertsAgain() {
        LocalDateTime filed = LocalDateTime.now().minusDays(8);
        LocalDateTime now = filed.plusDays(8);
        when(reportRepo.findOpenCasesCreatedBefore(any()))
                .thenReturn(List.<Object[]>of(new Object[] {1L, filed}))
                .thenReturn(List.of())
                .thenReturn(List.<Object[]>of(new Object[] {1L, filed}));

        monitor.checkStaleCases(now);
        monitor.checkStaleCases(now);
        assertThat(levels()).containsExactly(Level.WARN);

        monitor.checkStaleCases(now);
        assertThat(levels()).containsExactly(Level.WARN, Level.WARN);
    }

    private List<Level> levels() {
        return appender.list.stream().map(ILoggingEvent::getLevel).toList();
    }
}
