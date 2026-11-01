package com.example.media.repository;

import com.example.media.entity.MediaFinalizeAttempt;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MediaFinalizeAttemptRepository extends JpaRepository<MediaFinalizeAttempt, String> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_finalize_attempts
               set state = 'CLEANUP_REQUIRED',
                   next_cleanup_at = :now,
                   updated_at = :now
             where media_asset_id = :assetId
               and state = 'ACTIVE'
            """, nativeQuery = true)
    int markActiveAttemptsForCleanup(
            @Param("assetId") Long assetId,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_finalize_attempts
               set lease_expires_at = :leaseUntil,
                   updated_at = :now
             where attempt_id = :attemptId
               and state = 'ACTIVE'
            """, nativeQuery = true)
    int renewActiveAttempt(
            @Param("attemptId") String attemptId,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_finalize_attempts
               set object_key = :objectKey,
                   feed_object_key = :feedObjectKey,
                   updated_at = :now
             where attempt_id = :attemptId
               and state = 'ACTIVE'
               and object_key is null
            """, nativeQuery = true)
    int recordAttemptObjectKeys(
            @Param("attemptId") String attemptId,
            @Param("objectKey") String objectKey,
            @Param("feedObjectKey") String feedObjectKey,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_finalize_attempts
               set state = 'PUBLISHED',
                   next_cleanup_at = null,
                   updated_at = :now
             where attempt_id = :attemptId
               and state = 'ACTIVE'
            """, nativeQuery = true)
    int markAttemptPublished(
            @Param("attemptId") String attemptId,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_finalize_attempts
               set state = 'CLEANUP_REQUIRED',
                   next_cleanup_at = :now,
                   updated_at = :now
             where attempt_id = :attemptId
               and state in ('ACTIVE', 'CLEANUP_REQUIRED')
            """, nativeQuery = true)
    int markAttemptCleanupRequired(
            @Param("attemptId") String attemptId,
            @Param("now") LocalDateTime now);

    @Query(value = """
            select attempt_id
              from media_finalize_attempts
             where (state in ('CLEANUP_REQUIRED', 'CLEANED') and next_cleanup_at <= :now)
                or (state = 'CLEANUP_PROCESSING' and cleanup_lease_until <= :now)
             order by next_cleanup_at, attempt_id
            """, nativeQuery = true)
    List<String> findDueCleanupAttemptIds(
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query(value = """
            select attempt_id
              from media_finalize_attempts
             where media_asset_id = :assetId
               and (state in ('CLEANUP_REQUIRED', 'CLEANED')
                    or (state = 'CLEANUP_PROCESSING' and cleanup_lease_until <= :now))
             order by created_at, attempt_id
            """, nativeQuery = true)
    List<String> findCleanupAttemptIdsForAsset(
            @Param("assetId") Long assetId,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_finalize_attempts
               set state = 'CLEANUP_PROCESSING',
                   cleanup_token = :cleanupToken,
                   cleanup_lease_until = :leaseUntil,
                   updated_at = :now
             where attempt_id = :attemptId
               and ((state in ('CLEANUP_REQUIRED', 'CLEANED') and next_cleanup_at <= :now)
                    or (state = 'CLEANUP_PROCESSING' and cleanup_lease_until <= :now))
            """, nativeQuery = true)
    int claimDueCleanup(
            @Param("attemptId") String attemptId,
            @Param("cleanupToken") String cleanupToken,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_finalize_attempts
               set state = 'CLEANUP_PROCESSING',
                   cleanup_token = :cleanupToken,
                   cleanup_lease_until = :leaseUntil,
                   updated_at = :now
             where attempt_id = :attemptId
               and ((state in ('CLEANUP_REQUIRED', 'CLEANED'))
                    or (state = 'CLEANUP_PROCESSING' and cleanup_lease_until <= :now))
            """, nativeQuery = true)
    int claimCleanupForAsset(
            @Param("attemptId") String attemptId,
            @Param("cleanupToken") String cleanupToken,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_finalize_attempts
               set state = 'CLEANED',
                   cleanup_token = null,
                   cleanup_lease_until = null,
                   cleanup_attempt_count = cleanup_attempt_count + 1,
                   next_cleanup_at = :nextCheckAt,
                   updated_at = :now
             where attempt_id = :attemptId
               and state = 'CLEANUP_PROCESSING'
               and cleanup_token = :cleanupToken
            """, nativeQuery = true)
    int markCleanupVerified(
            @Param("attemptId") String attemptId,
            @Param("cleanupToken") String cleanupToken,
            @Param("now") LocalDateTime now,
            @Param("nextCheckAt") LocalDateTime nextCheckAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_finalize_attempts
               set state = 'CLEANUP_REQUIRED',
                   cleanup_token = null,
                   cleanup_lease_until = null,
                   cleanup_attempt_count = cleanup_attempt_count + 1,
                   next_cleanup_at = :retryAt,
                   updated_at = :now
             where attempt_id = :attemptId
               and state = 'CLEANUP_PROCESSING'
               and cleanup_token = :cleanupToken
            """, nativeQuery = true)
    int markCleanupRetryable(
            @Param("attemptId") String attemptId,
            @Param("cleanupToken") String cleanupToken,
            @Param("now") LocalDateTime now,
            @Param("retryAt") LocalDateTime retryAt);

    boolean existsByMediaAssetIdAndStatusIn(
            Long mediaAssetId,
            java.util.Collection<com.example.media.entity.MediaFinalizeAttemptStatus> statuses);
}
