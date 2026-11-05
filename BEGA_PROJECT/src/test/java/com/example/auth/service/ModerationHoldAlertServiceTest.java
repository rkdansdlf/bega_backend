package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cheerboard.repo.CheerReportRepo;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModerationHoldAlertServiceTest {
    @Mock CheerReportRepo reports;
    @InjectMocks ModerationHoldAlertService service;

    @Test
    void alertsAtMostOncePerAccountPerDay() {
        when(reports.existsOpenModerationOlderThan(eq(7L), any(LocalDateTime.class))).thenReturn(true);

        service.alertIfStale(7L);
        service.alertIfStale(7L);

        verify(reports, times(1)).existsOpenModerationOlderThan(eq(7L), any(LocalDateTime.class));
    }

    @Test
    void doesNotRecordAccountWhileHoldIsNotStale() {
        when(reports.existsOpenModerationOlderThan(eq(7L), any(LocalDateTime.class))).thenReturn(false);

        service.alertIfStale(7L);
        service.alertIfStale(7L);

        verify(reports, times(2)).existsOpenModerationOlderThan(eq(7L), any(LocalDateTime.class));
    }

    @Test
    void forgetLetsAccountAlertAgain() {
        when(reports.existsOpenModerationOlderThan(eq(7L), any(LocalDateTime.class))).thenReturn(true);

        service.alertIfStale(7L);
        service.forget(7L);
        service.alertIfStale(7L);

        verify(reports, times(2)).existsOpenModerationOlderThan(eq(7L), any(LocalDateTime.class));
    }

    @Test
    void cutoffIsTwentyFourHoursBeforeNow() {
        when(reports.existsOpenModerationOlderThan(eq(7L), any(LocalDateTime.class))).thenReturn(false);
        LocalDateTime before = LocalDateTime.now().minusHours(24);

        service.alertIfStale(7L);

        LocalDateTime after = LocalDateTime.now().minusHours(24);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reports).existsOpenModerationOlderThan(eq(7L), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, after);
    }

    @Test
    void purgeHoldAlertsOnlyAfterItHasLastedTwentyFourHours() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 9, 0);

        assertThat(service.alertIfPurgeHeld(7L, start)).isFalse();
        assertThat(service.alertIfPurgeHeld(7L, start.plusHours(23))).isFalse();
        assertThat(service.alertIfPurgeHeld(7L, start.plusHours(24))).isTrue();
    }

    @Test
    void purgeHoldAlertsAtMostOncePerDay() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 9, 0);
        service.alertIfPurgeHeld(7L, start);

        assertThat(service.alertIfPurgeHeld(7L, start.plusHours(24))).isTrue();
        assertThat(service.alertIfPurgeHeld(7L, start.plusHours(25))).isFalse();
        assertThat(service.alertIfPurgeHeld(7L, start.plusHours(48))).isTrue();
    }

    @Test
    void endingThePurgeHoldRestartsTheClock() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 9, 0);
        service.alertIfPurgeHeld(7L, start);
        service.clearPurgeHold(7L);

        assertThat(service.alertIfPurgeHeld(7L, start.plusHours(30))).isFalse();
        assertThat(service.alertIfPurgeHeld(7L, start.plusHours(54))).isTrue();
    }

    @Test
    void forgetEndsThePurgeHoldToo() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 9, 0);
        service.alertIfPurgeHeld(7L, start);
        service.forget(7L);

        assertThat(service.alertIfPurgeHeld(7L, start.plusHours(30))).isFalse();
    }
}
