package com.example.media.service;

import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.media.entity.MediaFinalizeAttempt;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Reconciles immutable per-attempt object keys, including keys written after a prior delete check. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaFinalizeAttemptCleanupService {

    private static final int MAX_CLEANUPS_PER_RUN = 500;
    private static final int ASSET_CLEANUP_PAGE_SIZE = 100;

    private final MediaFinalizeClaimService claimService;
    private final StorageStrategy storageStrategy;

    public int cleanupDueAttempts() {
        return cleanupDueAttempts(LocalDateTime.now());
    }

    int cleanupDueAttempts(LocalDateTime now) {
        int checked = 0;
        Set<String> seen = new HashSet<>();
        while (checked < MAX_CLEANUPS_PER_RUN) {
            List<String> attemptIds = claimService.findDueAttemptCleanupIds(now);
            if (attemptIds.isEmpty()) {
                break;
            }

            boolean newlySeen = false;
            for (String attemptId : attemptIds) {
                if (!seen.add(attemptId)) {
                    continue;
                }
                newlySeen = true;
                cleanup(attemptId, now, false);
                checked++;
                if (checked >= MAX_CLEANUPS_PER_RUN) {
                    break;
                }
            }
            if (!newlySeen) {
                break;
            }
        }
        return checked;
    }

    public boolean cleanupForAsset(Long assetId) {
        LocalDateTime now = LocalDateTime.now();
        for (int page = 0; ; page++) {
            List<String> attemptIds = claimService.findAttemptCleanupIdsForAsset(assetId, now, page);
            if (attemptIds.isEmpty()) {
                break;
            }
            for (String attemptId : attemptIds) {
                cleanup(attemptId, now, true);
            }
            if (attemptIds.size() < ASSET_CLEANUP_PAGE_SIZE) {
                break;
            }
        }
        return !claimService.hasOutstandingAttemptCleanup(assetId);
    }

    public boolean cleanupAfterFailedFinalize(MediaFinalizeClaimService.FinalizeClaim claim) {
        LocalDateTime now = LocalDateTime.now();
        claimService.markAttemptCleanupRequired(claim);
        return cleanup(claim.token(), now, true);
    }

    private boolean cleanup(String attemptId, LocalDateTime now, boolean force) {
        Optional<MediaFinalizeClaimService.FinalizeAttemptCleanupClaim> claim = force
                ? claimService.claimAttemptCleanupForAsset(attemptId, now)
                : claimService.claimDueAttemptCleanup(attemptId, now);
        if (claim.isEmpty()) {
            return false;
        }

        MediaFinalizeAttempt attempt = claim.get().attempt();
        boolean absent = deleteAndConfirmAbsent(attempt, attempt.getObjectKey());
        absent &= deleteAndConfirmAbsent(attempt, attempt.getFeedObjectKey());
        claimService.finishAttemptCleanup(claim.get(), absent, now);
        return absent;
    }

    private boolean deleteAndConfirmAbsent(MediaFinalizeAttempt attempt, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return true;
        }
        try {
            storageStrategy.deleteChecked(attempt.getBucketName(), objectKey).block();
            return Boolean.FALSE.equals(storageStrategy.exists(attempt.getBucketName(), objectKey).block());
        } catch (RuntimeException exception) {
            log.warn(
                    "Media finalize attempt cleanup will retry: attemptId={}, cause={}",
                    attempt.getAttemptId(),
                    exception.getClass().getSimpleName());
            return false;
        }
    }
}
