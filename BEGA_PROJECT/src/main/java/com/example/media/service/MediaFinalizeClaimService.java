package com.example.media.service;

import com.example.common.validation.OwnerIdentity;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.entity.MediaFinalizeAttempt;
import com.example.media.entity.MediaFinalizeAttemptStatus;
import com.example.media.repository.MediaAssetRepository;
import com.example.media.repository.MediaFinalizeAttemptRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** Keeps each database transaction short and never spans object-storage I/O. */
@Service
@RequiredArgsConstructor
public class MediaFinalizeClaimService {

    private static final long CLAIM_LEASE_MINUTES = 15;
    private static final int CLEANUP_BATCH_SIZE = 100;

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaFinalizeAttemptRepository finalizeAttemptRepository;

    @Transactional
    public Optional<FinalizeClaim> claim(Long ownerUserId, Long assetId, String bucketName) {
        LocalDateTime now = LocalDateTime.now();
        String token = UUID.randomUUID().toString();
        if (mediaAssetRepository.claimFinalize(
                        assetId, ownerUserId, token, now, now.plusMinutes(CLAIM_LEASE_MINUTES))
                != 1) {
            return Optional.empty();
        }
        MediaAsset asset = mediaAssetRepository.findByIdAndOwnerUserId(assetId, ownerUserId)
                .filter(claimed -> token.equals(claimed.getFinalizeToken()))
                .orElse(null);
        if (asset == null) {
            return Optional.empty();
        }
        finalizeAttemptRepository.markActiveAttemptsForCleanup(assetId, now);
        finalizeAttemptRepository.save(new MediaFinalizeAttempt(
                token, assetId, asset.getDomain(), bucketName,
                now.plusMinutes(CLAIM_LEASE_MINUTES), now));
        return Optional.of(new FinalizeClaim(asset, token));
    }

    @Transactional
    public boolean renew(FinalizeClaim claim) {
        LocalDateTime now = LocalDateTime.now();
        if (mediaAssetRepository.renewFinalizeClaim(
                        claim.asset().getId(), claim.token(), now, now.plusMinutes(CLAIM_LEASE_MINUTES))
                != 1) {
            return false;
        }
        if (finalizeAttemptRepository.renewActiveAttempt(
                        claim.token(), now, now.plusMinutes(CLAIM_LEASE_MINUTES))
                != 1) {
            throw new IllegalStateException("Active media finalize attempt disappeared during lease renewal.");
        }
        return true;
    }

    @Transactional
    public boolean recordObjectKeys(FinalizeClaim claim, String objectKey, String feedObjectKey) {
        LocalDateTime now = LocalDateTime.now();
        if (mediaAssetRepository.recordFinalizeObjectKeys(
                        claim.asset().getId(), claim.token(), objectKey, feedObjectKey, now)
                != 1) {
            return false;
        }
        if (finalizeAttemptRepository.recordAttemptObjectKeys(
                        claim.token(), objectKey, feedObjectKey, now)
                != 1) {
            throw new IllegalStateException("Active media finalize attempt could not retain its output keys.");
        }
        return true;
    }

    @Transactional
    public boolean clearObjectKeys(FinalizeClaim claim) {
        return mediaAssetRepository.clearFinalizeObjectKeys(
                        claim.asset().getId(), claim.token(), LocalDateTime.now())
                == 1;
    }

    @Transactional
    public void release(FinalizeClaim claim) {
        LocalDateTime now = LocalDateTime.now();
        mediaAssetRepository.releaseFinalizeClaim(claim.asset().getId(), claim.token(), now);
        finalizeAttemptRepository.markAttemptCleanupRequired(claim.token(), now);
    }

    @Transactional
    public Optional<MediaAsset> complete(
            FinalizeClaim claim,
            FinalizedObject primary,
            FinalizedObject feed) {
        MediaAsset claimedAsset = claim.asset();
        LocalDateTime now = LocalDateTime.now();
        if (mediaAssetRepository.completeFinalizeClaim(
                        claimedAsset.getId(),
                        claimedAsset.getOwnerUserId(),
                        claim.token(),
                        primary.objectKey(),
                        primary.contentType(),
                        primary.bytes(),
                        primary.width(),
                        primary.height(),
                        now)
                != 1) {
            return Optional.empty();
        }
        if (finalizeAttemptRepository.markAttemptPublished(claim.token(), now) != 1) {
            throw new IllegalStateException("Active media finalize attempt disappeared before publication.");
        }

        MediaAsset readyAsset = mediaAssetRepository.findByIdAndOwnerUserId(
                        claimedAsset.getId(), claimedAsset.getOwnerUserId())
                .orElseThrow(() -> new IllegalStateException("Claimed media asset disappeared during finalize."));
        if (feed != null) {
            MediaAsset feedAsset = mediaAssetRepository.findByDerivedFrom_Id(readyAsset.getId())
                    .orElseGet(() -> MediaAsset.builder()
                            .ownerUserId(readyAsset.getOwnerUserId())
                            .domain(readyAsset.getDomain())
                            .status(MediaAssetStatus.PENDING)
                            .originalFileName(readyAsset.getOriginalFileName())
                            .declaredContentType(readyAsset.getDeclaredContentType())
                            .declaredBytes(readyAsset.getDeclaredBytes())
                            .declaredWidth(readyAsset.getDeclaredWidth())
                            .declaredHeight(readyAsset.getDeclaredHeight())
                            .stagingObjectKey(readyAsset.getStagingObjectKey())
                            .uploadExpiresAt(readyAsset.getUploadExpiresAt())
                            .derivedFrom(readyAsset)
                            .build());
            feedAsset.markReady(feed.objectKey(), feed.contentType(), feed.bytes(), feed.width(), feed.height());
            mediaAssetRepository.saveAndFlush(feedAsset);
        }
        return Optional.of(readyAsset);
    }

    @Transactional
    public Optional<FinalizeCleanupClaim> claimExpiredPendingForCleanup(
            MediaAsset asset,
            LocalDateTime now,
            LocalDateTime uploadCutoff) {
        String cleanupToken = UUID.randomUUID().toString();
        if (mediaAssetRepository.claimExpiredPendingForCleanup(
                        asset.getId(), cleanupToken, now, now.plusMinutes(CLAIM_LEASE_MINUTES), uploadCutoff)
                != 1) {
            return Optional.empty();
        }
        finalizeAttemptRepository.markActiveAttemptsForCleanup(asset.getId(), now);
        return Optional.of(new FinalizeCleanupClaim(asset, cleanupToken));
    }

    @Transactional
    public Optional<FinalizeCleanupClaim> claimExpiredForCleanup(
            MediaAsset asset,
            LocalDateTime now,
            LocalDateTime uploadCutoff) {
        if (asset.getFinalizeToken() == null) {
            return Optional.empty();
        }
        String cleanupToken = UUID.randomUUID().toString();
        if (mediaAssetRepository.claimExpiredFinalizeForCleanup(
                        asset.getId(),
                        asset.getFinalizeToken(),
                        cleanupToken,
                        now,
                        now.plusMinutes(CLAIM_LEASE_MINUTES),
                        uploadCutoff)
                != 1) {
            return Optional.empty();
        }
        finalizeAttemptRepository.markActiveAttemptsForCleanup(asset.getId(), now);
        return Optional.of(new FinalizeCleanupClaim(asset, cleanupToken));
    }

    @Transactional
    public boolean markExpiredCleanupComplete(FinalizeCleanupClaim claim) {
        LocalDateTime now = LocalDateTime.now();
        return mediaAssetRepository.markExpiredFinalizeDeleted(
                        claim.asset().getId(), claim.token(), now, now.plusMinutes(CLAIM_LEASE_MINUTES))
                == 1;
    }

    @Transactional
    public Optional<FinalizeCleanupClaim> claimDeletedTombstoneForCleanup(
            MediaAsset asset,
            LocalDateTime now) {
        if (asset.getFinalizeToken() == null) {
            return Optional.empty();
        }
        String cleanupToken = UUID.randomUUID().toString();
        if (mediaAssetRepository.claimDeletedFinalizeForCleanup(
                        asset.getId(),
                        asset.getFinalizeToken(),
                        cleanupToken,
                        now,
                        now.plusMinutes(CLAIM_LEASE_MINUTES))
                != 1) {
            return Optional.empty();
        }
        return Optional.of(new FinalizeCleanupClaim(asset, cleanupToken));
    }

    @Transactional
    public Optional<FinalizeCleanupClaim> claimOwnedAssetForAccountDeletion(
            MediaAsset asset,
            Long ownerUserId) {
        LocalDateTime now = LocalDateTime.now();
        String cleanupToken = UUID.randomUUID().toString();
        if (mediaAssetRepository.claimOwnedAssetForAccountDeletion(
                        asset.getId(),
                        ownerUserId,
                        cleanupToken,
                        now,
                        cleanupLeaseUntil(asset, now))
                != 1) {
            return Optional.empty();
        }
        finalizeAttemptRepository.markActiveAttemptsForCleanup(asset.getId(), now);
        return mediaAssetRepository.findById(asset.getId())
                .filter(claimed -> cleanupToken.equals(claimed.getFinalizeToken()))
                .map(claimed -> new FinalizeCleanupClaim(claimed, cleanupToken));
    }

    @Transactional
    public Optional<FinalizeCleanupClaim> claimOwnedAssetForDeletion(
            MediaAsset asset,
            Long ownerUserId) {
        if (mediaAssetRepository.findByIdForUpdate(asset.getId()).isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        String cleanupToken = UUID.randomUUID().toString();
        if (mediaAssetRepository.claimOwnedAssetForDeletion(
                        asset.getId(),
                        ownerUserId,
                        cleanupToken,
                        now,
                        cleanupLeaseUntil(asset, now))
                != 1) {
            return Optional.empty();
        }
        finalizeAttemptRepository.markActiveAttemptsForCleanup(asset.getId(), now);
        return mediaAssetRepository.findById(asset.getId())
                .filter(claimed -> cleanupToken.equals(claimed.getFinalizeToken()))
                .map(claimed -> new FinalizeCleanupClaim(claimed, cleanupToken));
    }

    /** Commits the non-linkable tombstone before orphan cleanup performs storage I/O. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<FinalizeCleanupClaim> claimUnlinkedMediaForCleanup(
            Long assetId, Long ownerId, MediaDomain domain, LocalDateTime cutoff) {
        if (domain == null) throw new IllegalArgumentException("A media domain is required.");
        MediaAsset locked = mediaAssetRepository.findByIdForUpdate(assetId).orElse(null);
        if (locked == null || !OwnerIdentity.isSameOwner(locked.getOwnerUserId(), ownerId)) return Optional.empty();
        LocalDateTime now = LocalDateTime.now();
        String token = UUID.randomUUID().toString();
        if (mediaAssetRepository.claimUnlinkedMediaForCleanup(
                assetId, domain.name(), cutoff, token, now, cleanupLeaseUntil(locked, now)) != 1) {
            return Optional.empty();
        }
        finalizeAttemptRepository.markActiveAttemptsForCleanup(assetId, now);
        return mediaAssetRepository.findById(assetId)
                .filter(claimed -> token.equals(claimed.getFinalizeToken()))
                .map(claimed -> new FinalizeCleanupClaim(claimed, token));
    }

    @Transactional
    public boolean clearDeletedTombstone(FinalizeCleanupClaim claim) {
        LocalDateTime now = LocalDateTime.now();
        return mediaAssetRepository.clearDeletedFinalizeTombstone(
                        claim.asset().getId(),
                        claim.token(),
                        MediaAsset.CLEARED_STAGING_OBJECT_KEY,
                        now,
                        now.minusMinutes(CLAIM_LEASE_MINUTES))
                == 1;
    }

    @Transactional
    public boolean updateDeletedTombstoneVerification(
            FinalizeCleanupClaim claim,
            LocalDateTime verifiedAt) {
        LocalDateTime now = LocalDateTime.now();
        return mediaAssetRepository.updateDeletedFinalizeVerification(
                        claim.asset().getId(),
                        claim.token(),
                        verifiedAt,
                        cleanupLeaseUntil(claim.asset(), now),
                        now)
                == 1;
    }

    @Transactional
    public boolean markAttemptCleanupRequired(FinalizeClaim claim) {
        return finalizeAttemptRepository.markAttemptCleanupRequired(claim.token(), LocalDateTime.now()) == 1;
    }

    @Transactional(readOnly = true)
    public List<String> findDueAttemptCleanupIds(LocalDateTime now) {
        return finalizeAttemptRepository.findDueCleanupAttemptIds(now, PageRequest.of(0, CLEANUP_BATCH_SIZE));
    }

    @Transactional(readOnly = true)
    public List<String> findAttemptCleanupIdsForAsset(Long assetId, LocalDateTime now, int page) {
        return finalizeAttemptRepository.findCleanupAttemptIdsForAsset(
                assetId, now, PageRequest.of(Math.max(0, page), CLEANUP_BATCH_SIZE));
    }

    @Transactional
    public Optional<FinalizeAttemptCleanupClaim> claimDueAttemptCleanup(String attemptId, LocalDateTime now) {
        return claimAttemptCleanup(attemptId, now, false);
    }

    @Transactional
    public Optional<FinalizeAttemptCleanupClaim> claimAttemptCleanupForAsset(String attemptId, LocalDateTime now) {
        return claimAttemptCleanup(attemptId, now, true);
    }

    private Optional<FinalizeAttemptCleanupClaim> claimAttemptCleanup(
            String attemptId,
            LocalDateTime now,
            boolean force) {
        String token = UUID.randomUUID().toString();
        int claimed = force
                ? finalizeAttemptRepository.claimCleanupForAsset(
                        attemptId, token, now, now.plusMinutes(CLAIM_LEASE_MINUTES))
                : finalizeAttemptRepository.claimDueCleanup(
                        attemptId, token, now, now.plusMinutes(CLAIM_LEASE_MINUTES));
        if (claimed != 1) {
            return Optional.empty();
        }
        return finalizeAttemptRepository.findById(attemptId)
                .filter(attempt -> token.equals(attempt.getCleanupToken()))
                .map(attempt -> new FinalizeAttemptCleanupClaim(attempt, token));
    }

    @Transactional
    public boolean finishAttemptCleanup(
            FinalizeAttemptCleanupClaim claim,
            boolean verifiedAbsent,
            LocalDateTime now) {
        if (verifiedAbsent) {
            return finalizeAttemptRepository.markCleanupVerified(
                            claim.attempt().getAttemptId(), claim.token(), now, now.plusDays(1))
                    == 1;
        }
        return finalizeAttemptRepository.markCleanupRetryable(
                        claim.attempt().getAttemptId(), claim.token(), now, now.plusMinutes(15))
                == 1;
    }

    @Transactional(readOnly = true)
    public boolean hasOutstandingAttemptCleanup(Long assetId) {
        return finalizeAttemptRepository.existsByMediaAssetIdAndStatusIn(
                assetId,
                List.of(
                        MediaFinalizeAttemptStatus.ACTIVE,
                        MediaFinalizeAttemptStatus.CLEANUP_REQUIRED,
                        MediaFinalizeAttemptStatus.CLEANUP_PROCESSING));
    }

    private LocalDateTime cleanupLeaseUntil(MediaAsset asset, LocalDateTime now) {
        LocalDateTime minimumLease = now.plusMinutes(CLAIM_LEASE_MINUTES);
        if (asset.getUploadExpiresAt() == null) {
            return laterOf(minimumLease, afterExistingFinalizeLease(asset));
        }
        LocalDateTime uploadQuiescenceLease = asset.getUploadExpiresAt().plusMinutes(CLAIM_LEASE_MINUTES);
        return laterOf(laterOf(minimumLease, uploadQuiescenceLease), afterExistingFinalizeLease(asset));
    }

    private LocalDateTime afterExistingFinalizeLease(MediaAsset asset) {
        return asset.getFinalizeLeaseUntil() == null
                ? LocalDateTime.MIN
                : asset.getFinalizeLeaseUntil().plusMinutes(CLAIM_LEASE_MINUTES);
    }

    private LocalDateTime laterOf(LocalDateTime first, LocalDateTime second) {
        return first.isAfter(second) ? first : second;
    }

    public record FinalizeClaim(MediaAsset asset, String token) {}

    public record FinalizeCleanupClaim(MediaAsset asset, String token) {}

    public record FinalizeAttemptCleanupClaim(MediaFinalizeAttempt attempt, String token) {}

    public record FinalizedObject(String objectKey, String contentType, long bytes, int width, int height) {}
}
