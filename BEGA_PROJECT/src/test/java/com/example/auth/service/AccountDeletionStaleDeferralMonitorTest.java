package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.repository.UserRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AccountDeletionStaleDeferralMonitorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 5, 0);

    @Mock
    private UserRepository userRepository;

    @Test
    void noStaleDeferralsKeepsGaugeAtZeroAndSkipsSampling() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AccountDeletionStaleDeferralMonitor monitor = new AccountDeletionStaleDeferralMonitor(userRepository, registry);
        when(userRepository.countStaleDeferredDeletions(NOW.minusDays(30))).thenReturn(0L);

        long count = monitor.checkStaleDeferrals(NOW);

        assertThat(count).isZero();
        assertThat(gaugeValue(registry)).isZero();
        verify(userRepository, never()).findStaleDeferredDeletionUserIds(any(), any());
    }

    @Test
    void staleDeferralsUpdateGaugeAndSampleOnlyUserIds() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AccountDeletionStaleDeferralMonitor monitor = new AccountDeletionStaleDeferralMonitor(userRepository, registry);
        when(userRepository.countStaleDeferredDeletions(NOW.minusDays(30))).thenReturn(3L);
        when(userRepository.findStaleDeferredDeletionUserIds(eq(NOW.minusDays(30)), eq(PageRequest.of(0, 20))))
                .thenReturn(List.of(7L, 9L, 11L));

        long count = monitor.checkStaleDeferrals(NOW);

        assertThat(count).isEqualTo(3L);
        assertThat(gaugeValue(registry)).isEqualTo(3.0);
        verify(userRepository).findStaleDeferredDeletionUserIds(eq(NOW.minusDays(30)), eq(PageRequest.of(0, 20)));
    }

    @Test
    void gaugeReturnsToZeroOnceOverdueDeletionsAreFinalized() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AccountDeletionStaleDeferralMonitor monitor = new AccountDeletionStaleDeferralMonitor(userRepository, registry);
        when(userRepository.countStaleDeferredDeletions(NOW.minusDays(30))).thenReturn(2L, 0L);
        when(userRepository.findStaleDeferredDeletionUserIds(eq(NOW.minusDays(30)), eq(PageRequest.of(0, 20))))
                .thenReturn(List.of(5L, 6L));

        monitor.checkStaleDeferrals(NOW);
        assertThat(gaugeValue(registry)).isEqualTo(2.0);

        monitor.checkStaleDeferrals(NOW);
        assertThat(gaugeValue(registry)).isZero();
    }

    @Test
    void stalledContentRetriesUpdateTheirOwnGaugeAndSampleOnlyUserIds() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AccountDeletionStaleDeferralMonitor monitor = new AccountDeletionStaleDeferralMonitor(userRepository, registry);
        when(userRepository.countStaleContentRetries(NOW.minusHours(24))).thenReturn(2L);
        when(userRepository.findStaleContentRetryUserIds(eq(NOW.minusHours(24)), eq(PageRequest.of(0, 20))))
                .thenReturn(List.of(4L, 8L));

        long count = monitor.checkStaleContentRetries(NOW);

        assertThat(count).isEqualTo(2L);
        assertThat(contentRetryGaugeValue(registry)).isEqualTo(2.0);
        assertThat(gaugeValue(registry)).isZero();
        verify(userRepository, never()).countStaleDeferredDeletions(any());
    }

    @Test
    void healthyContentRetriesKeepGaugeAtZeroAndSkipSampling() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AccountDeletionStaleDeferralMonitor monitor = new AccountDeletionStaleDeferralMonitor(userRepository, registry);
        when(userRepository.countStaleContentRetries(NOW.minusHours(24))).thenReturn(0L);

        assertThat(monitor.checkStaleContentRetries(NOW)).isZero();
        assertThat(contentRetryGaugeValue(registry)).isZero();
        verify(userRepository, never()).findStaleContentRetryUserIds(any(), any());
    }

    private static double gaugeValue(SimpleMeterRegistry registry) {
        Gauge gauge = registry.get("account.deletion.stale_deferred").gauge();
        return gauge.value();
    }

    private static double contentRetryGaugeValue(SimpleMeterRegistry registry) {
        Gauge gauge = registry.get("account.deletion.stale_content_retry").gauge();
        return gauge.value();
    }
}
