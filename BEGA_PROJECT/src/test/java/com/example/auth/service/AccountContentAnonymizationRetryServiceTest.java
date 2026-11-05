package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountContentAnonymizationRetryServiceTest {
    @Mock UserRepository users;
    @Mock AccountDeletionContentService content;
    @Mock ModerationHoldAlertService moderationHoldAlerts;
    @InjectMocks AccountContentAnonymizationRetryService service;

    @Test
    void checksModerationHoldAndKeepsRetryingWhenPending() {
        UserEntity user = tombstoneDueForRetry();
        when(users.findByIdForWrite(7L)).thenReturn(Optional.of(user));
        when(content.anonymize(7L)).thenReturn(AccountDeletionContentService.Result.MODERATION_PENDING);

        service.retry(7L);

        verify(moderationHoldAlerts).alertIfStale(7L);
        assertThat(user.getContentAnonymizationRetryAt()).isNotNull();
    }

    @Test
    void checksPurgeHoldAndKeepsRetryingWhenPurgePending() {
        UserEntity user = tombstoneDueForRetry();
        when(users.findByIdForWrite(7L)).thenReturn(Optional.of(user));
        when(content.anonymize(7L)).thenReturn(AccountDeletionContentService.Result.PURGE_PENDING);

        service.retry(7L);

        verify(moderationHoldAlerts).alertIfPurgeHeld(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        assertThat(user.getContentAnonymizationRetryAt()).isNotNull();
    }

    @Test
    void keepsRetryingWhenPurgeHoldCheckFails() {
        UserEntity user = tombstoneDueForRetry();
        when(users.findByIdForWrite(7L)).thenReturn(Optional.of(user));
        when(content.anonymize(7L)).thenReturn(AccountDeletionContentService.Result.PURGE_PENDING);
        doThrow(new IllegalStateException("alert failed")).when(moderationHoldAlerts)
                .alertIfPurgeHeld(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(LocalDateTime.class));

        service.retry(7L);

        assertThat(user.getContentAnonymizationRetryAt()).isNotNull();
    }

    @Test
    void keepsRetryingWhenModerationHoldCheckFails() {
        UserEntity user = tombstoneDueForRetry();
        when(users.findByIdForWrite(7L)).thenReturn(Optional.of(user));
        when(content.anonymize(7L)).thenReturn(AccountDeletionContentService.Result.MODERATION_PENDING);
        doThrow(new IllegalStateException("query failed")).when(moderationHoldAlerts).alertIfStale(7L);

        service.retry(7L);

        assertThat(user.getContentAnonymizationRetryAt()).isNotNull();
    }

    @Test
    void forgetsAccountOnceContentIsCompleted() {
        UserEntity user = tombstoneDueForRetry();
        when(users.findByIdForWrite(7L)).thenReturn(Optional.of(user));
        when(content.anonymize(7L)).thenReturn(AccountDeletionContentService.Result.COMPLETED);

        service.retry(7L);

        verify(moderationHoldAlerts).forget(7L);
        assertThat(user.getContentAnonymizationRetryAt()).isNull();
    }

    @Test
    void retainsRetryWhenOwnerIsNotAnAnonymizedTombstone() {
        UserEntity user = UserEntity.builder().id(7L).name("synthetic")
                .contentAnonymizationRetryAt(LocalDateTime.now().minusDays(1)).build();
        when(users.findByIdForWrite(7L)).thenReturn(Optional.of(user));
        service.retry(7L);
        assertThat(user.getContentAnonymizationRetryAt()).isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1));
        org.mockito.Mockito.verifyNoInteractions(content);
    }

    @Test
    void deferralLeavesUnfinishedWorkDurable() {
        UserEntity user = UserEntity.builder().id(7L)
                .contentAnonymizationRetryAt(LocalDateTime.now().minusDays(1)).build();
        when(users.findByIdForWrite(7L)).thenReturn(Optional.of(user));
        service.defer(7L);
        assertThat(user.getContentAnonymizationRetryAt()).isNotNull();
    }

    @Test
    void retriesUntilPurgeFinishesThenClearsPendingMarker() {
        UserEntity user = tombstoneDueForRetry();
        when(users.findByIdForWrite(7L)).thenReturn(Optional.of(user));
        when(content.anonymize(7L)).thenReturn(AccountDeletionContentService.Result.PURGE_PENDING,
                AccountDeletionContentService.Result.COMPLETED);
        service.retry(7L);
        assertThat(user.getContentAnonymizationRetryAt()).isNotNull();
        user.setContentAnonymizationRetryAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1));
        service.retry(7L);
        assertThat(user.getContentAnonymizationRetryAt()).isNull();
    }

    private static UserEntity tombstoneDueForRetry() {
        return UserEntity.builder().id(7L).name("탈퇴한 사용자")
                .email("deleted-7@deleted.invalid").enabled(false).locked(true).pendingDeletion(true)
                .contentAnonymizationRetryAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1)).build();
    }
}
