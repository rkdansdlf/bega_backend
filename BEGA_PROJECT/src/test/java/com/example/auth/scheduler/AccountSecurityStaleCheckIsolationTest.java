package com.example.auth.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.repository.UserRepository;
import com.example.auth.service.AccountContentAnonymizationRetryService;
import com.example.auth.service.AccountDeletionService;
import com.example.auth.service.AccountDeletionStaleDeferralMonitor;
import com.example.auth.service.AccountSecurityService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class AccountSecurityStaleCheckIsolationTest {

    private AccountDeletionStaleDeferralMonitor monitor;
    private AccountSecurityScheduler scheduler;
    private TimeZone originalTimeZone;

    @BeforeEach
    void setUp() {
        // UTC build hosts read the same local and UTC clock, so pin a zone with a real offset.
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        monitor = mock(AccountDeletionStaleDeferralMonitor.class);
        scheduler = new AccountSecurityScheduler(
                mock(JobScheduler.class),
                mock(AccountSecurityService.class),
                mock(AccountDeletionService.class),
                mock(AccountContentAnonymizationRetryService.class),
                mock(UserRepository.class),
                monitor);
    }

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    void bothChecksRunWithTheClockEachColumnIsWrittenWith() {
        LocalDateTime localBefore = LocalDateTime.now();
        LocalDateTime utcBefore = LocalDateTime.now(ZoneOffset.UTC);

        scheduler.checkStaleDeferredDeletions();

        LocalDateTime localAfter = LocalDateTime.now();
        LocalDateTime utcAfter = LocalDateTime.now(ZoneOffset.UTC);
        ArgumentCaptor<LocalDateTime> localCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> utcCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(monitor).checkStaleDeferrals(localCaptor.capture());
        verify(monitor).checkStaleContentRetries(utcCaptor.capture());
        assertThat(localCaptor.getValue()).isBetween(localBefore, localAfter);
        assertThat(utcCaptor.getValue()).isBetween(utcBefore, utcAfter);
        // Seoul is UTC+9 with no DST, so the local value sits nine hours ahead of the UTC value.
        assertThat(Duration.between(utcCaptor.getValue(), localCaptor.getValue()).toMinutes())
                .isBetween(539L, 541L);
    }

    @Test
    void deferralFailureStillRunsContentRetryCheckAndIsRethrown() {
        when(monitor.checkStaleDeferrals(any())).thenThrow(new IllegalStateException("deferral query failed"));

        assertThatThrownBy(() -> scheduler.checkStaleDeferredDeletions())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("deferral query failed");
        verify(monitor).checkStaleContentRetries(any());
    }

    @Test
    void contentRetryFailureIsRethrownAfterDeferralCheckRan() {
        when(monitor.checkStaleContentRetries(any())).thenThrow(new IllegalStateException("retry query failed"));

        assertThatThrownBy(() -> scheduler.checkStaleDeferredDeletions())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("retry query failed");
        InOrder order = inOrder(monitor);
        order.verify(monitor).checkStaleDeferrals(any());
        order.verify(monitor).checkStaleContentRetries(any());
    }

    @Test
    void bothChecksFailingRethrowsTheDeferralFailureWithTheContentRetryFailureSuppressed() {
        IllegalStateException deferralFailure = new IllegalStateException("deferral query failed");
        when(monitor.checkStaleDeferrals(any())).thenThrow(deferralFailure);
        when(monitor.checkStaleContentRetries(any())).thenThrow(new IllegalStateException("retry query failed"));

        Throwable thrown = catchThrowable(() -> scheduler.checkStaleDeferredDeletions());

        assertThat(thrown).isSameAs(deferralFailure);
        assertThat(thrown.getSuppressed()).hasSize(1);
        assertThat(thrown.getSuppressed()[0]).hasMessage("retry query failed");
    }
}
