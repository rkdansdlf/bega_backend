package com.example.auth.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountContentAnonymizationRetryService {
    private final UserRepository userRepository;
    private final AccountDeletionContentService contentService;
    private final ModerationHoldAlertService moderationHoldAlerts;

    @Transactional
    public void retry(Long userId) {
        UserEntity user = userRepository.findByIdForWrite(userId).orElse(null);
        if (user == null || user.getContentAnonymizationRetryAt() == null) return;
        if (user.getContentAnonymizationRetryAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) return;
        if (!AccountDeletionIdentityService.isAnonymizedTombstone(user)) {
            user.setContentAnonymizationRetryAt(nextAttempt());
            return;
        }
        AccountDeletionContentService.Result result = contentService.anonymize(userId);
        if (result == AccountDeletionContentService.Result.COMPLETED) {
            moderationHoldAlerts.forget(userId);
        } else if (result == AccountDeletionContentService.Result.PURGE_PENDING) {
            checkPurgeHold(userId);
        } else {
            moderationHoldAlerts.clearPurgeHold(userId);
            if (result == AccountDeletionContentService.Result.MODERATION_PENDING) {
                checkModerationHold(userId);
            }
        }
        UserEntity current = userRepository.findByIdForWrite(userId)
                .orElseThrow(() -> new IllegalStateException("The account disappeared during content finalization"));
        current.setContentAnonymizationRetryAt(result == AccountDeletionContentService.Result.COMPLETED
                ? null : nextAttempt());
    }

    @Transactional
    public void defer(Long userId) {
        userRepository.findByIdForWrite(userId).ifPresent(user -> {
            if (user.getContentAnonymizationRetryAt() != null) user.setContentAnonymizationRetryAt(nextAttempt());
        });
    }

    private void checkModerationHold(Long userId) {
        try {
            moderationHoldAlerts.alertIfStale(userId);
        } catch (RuntimeException ex) {
            // The alert is observability only; the retry must still be rescheduled.
            log.warn("Moderation hold check failed; content retry continues: userId={} error={}",
                    userId, ex.getClass().getSimpleName());
        }
    }

    private void checkPurgeHold(Long userId) {
        try {
            moderationHoldAlerts.alertIfPurgeHeld(userId, LocalDateTime.now(ZoneOffset.UTC));
        } catch (RuntimeException ex) {
            // The alert is observability only; the retry must still be rescheduled.
            log.warn("Purge hold check failed; content retry continues: userId={} error={}",
                    userId, ex.getClass().getSimpleName());
        }
    }

    private LocalDateTime nextAttempt() {
        return LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10);
    }
}
