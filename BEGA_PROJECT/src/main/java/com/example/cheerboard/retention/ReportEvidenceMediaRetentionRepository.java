package com.example.cheerboard.retention;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportEvidenceMediaRetentionRepository extends JpaRepository<ReportEvidenceMediaRetention, Long> {

    @Query("SELECT r.id FROM ReportEvidenceMediaRetention r WHERE r.status = :status ORDER BY r.id")
    List<Long> findIdsByStatus(@Param("status") ReportEvidenceMediaStatus status);

    /** True when a row for this object exists that is not closed out by owner restoration. */
    boolean existsByStoragePathAndStatusNot(String storagePath, ReportEvidenceMediaStatus status);

    long countByStatusAndCreatedAtBefore(ReportEvidenceMediaStatus status, LocalDateTime cutoff);

    /**
     * Reported post images with no live retention row, whose author is pending deletion with a first deletion
     * request. A row closed as RESOLVED_RESTORED does not count as live, so a later request registers again.
     * Columns: post_image id, post id, owner id, storage path, first deletion request time (TIMESTAMP),
     * current deletion cycle start (TIMESTAMP), earliest report time on the post (TIMESTAMPTZ or TIMESTAMP).
     */
    @Query(value = """
                    SELECT pi.id, p.id, u.id, pi.storage_path, u.first_deletion_requested_at,
                           u.deletion_cycle_started_at,
                           (SELECT MIN(r.createdat) FROM cheer_post_reports r WHERE r.post_id = p.id)
                      FROM post_images pi
                      JOIN cheer_post p ON p.id = pi.post_id
                      JOIN users u ON u.id = p.author_id
                     WHERE u.first_deletion_requested_at IS NOT NULL
                       AND LOWER(TRIM(CAST(u.pending_deletion AS CHAR(5)))) IN ('1','t','true')
                       AND EXISTS (SELECT 1 FROM cheer_post_reports r WHERE r.post_id = p.id)
                       AND NOT EXISTS (SELECT 1 FROM report_evidence_media_retention x
                                        WHERE x.storage_path = pi.storage_path
                                          AND x.status <> 'RESOLVED_RESTORED')
                     ORDER BY pi.id
                    """, nativeQuery = true)
    List<Object[]> findUnregisteredReportedImagesWithBase();

    /**
     * Reported post images of an author who is pending deletion but has no first deletion request. These cannot be
     * dated automatically, so they go to manual review instead of expiring. Disabled accounts that are not pending
     * deletion are not registered: their posts stay live under the normal rules.
     * Columns: post_image id, post id, owner id, storage path.
     */
    @Query(value = """
                    SELECT pi.id, p.id, u.id, pi.storage_path
                      FROM post_images pi
                      JOIN cheer_post p ON p.id = pi.post_id
                      JOIN users u ON u.id = p.author_id
                     WHERE u.first_deletion_requested_at IS NULL
                       AND LOWER(TRIM(CAST(u.pending_deletion AS CHAR(5)))) IN ('1','t','true')
                       AND EXISTS (SELECT 1 FROM cheer_post_reports r WHERE r.post_id = p.id)
                       AND NOT EXISTS (SELECT 1 FROM report_evidence_media_retention x
                                        WHERE x.storage_path = pi.storage_path
                                          AND x.status <> 'RESOLVED_RESTORED')
                     ORDER BY pi.id
                    """, nativeQuery = true)
    List<Object[]> findUnregisteredReportedImagesWithoutBase();

    /**
     * Owner deletion state: GRACE while the 7-day recovery window is open, FINALIZED after finalization
     * (pending with no scheduled time), NOT_PENDING when recovered. Empty when the owner row is gone.
     */
    @Query(value = """
                    SELECT CASE
                             WHEN LOWER(TRIM(CAST(u.pending_deletion AS CHAR(5)))) NOT IN ('1','t','true') THEN 'NOT_PENDING'
                             WHEN u.deletion_scheduled_for IS NULL THEN 'FINALIZED'
                             ELSE 'GRACE'
                           END
                      FROM users u
                     WHERE u.id = :ownerId
                    """, nativeQuery = true)
    List<String> findOwnerDeletionState(@Param("ownerId") Long ownerId);

    /**
     * Other holders of the same storage object, counted over every spelling of its key ({@code keys}): other post
     * images, media assets on those keys that are not linked to this post, and legacy holds for other images.
     * The image's own asset (linked to this post) and its own legacy hold are not counted.
     */
    @Query(value = """
                    SELECT (SELECT COUNT(*) FROM post_images pi
                             WHERE pi.storage_path IN (:keys) AND pi.id <> :postImageId)
                         + (SELECT COUNT(*) FROM media_assets a
                             WHERE a.object_key IN (:keys)
                               AND a.status <> 'DELETED'
                               AND NOT EXISTS (SELECT 1 FROM media_asset_links l
                                                WHERE l.asset_id = a.id
                                                  AND l.domain = 'CHEER'
                                                  AND l.entity_id = :postId))
                         + (SELECT COUNT(*) FROM legacy_post_image_holds h
                             WHERE h.storage_path IN (:keys) AND h.post_image_id <> :postImageId)
                    """, nativeQuery = true)
    Long countOtherReferences(@Param("keys") Collection<String> keys, @Param("postImageId") Long postImageId,
                              @Param("postId") Long postId);

    /** Locks the row until the surrounding transaction ends, so a hold or extension cannot slip in before deletion. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReportEvidenceMediaRetention r WHERE r.id = :id")
    Optional<ReportEvidenceMediaRetention> findByIdForUpdate(@Param("id") Long id);

    /** True while the post still has a report or appeal awaiting a decision. */
    @Query(value = """
                    SELECT COUNT(*) FROM cheer_post_reports r
                     WHERE r.post_id = :postId
                       AND (r.status IN ('PENDING','IN_REVIEW')
                            OR r.appeal_status IN ('REQUESTED','REVIEWING'))
                    """, nativeQuery = true)
    Long countOpenCases(@Param("postId") Long postId);

    /** True while the post still has a report or appeal awaiting a decision. */
    default boolean hasOpenCase(Long postId) {
        Long open = countOpenCases(postId);
        return open != null && open > 0;
    }

    /** Ids of the reports on this post that are still awaiting a decision. An extension must name one of them. */
    @Query(value = """
                    SELECT r.id FROM cheer_post_reports r
                     WHERE r.post_id = :postId
                       AND (r.status IN ('PENDING','IN_REVIEW')
                            OR r.appeal_status IN ('REQUESTED','REVIEWING'))
                     ORDER BY r.id
                    """, nativeQuery = true)
    List<Long> findOpenReportIds(@Param("postId") Long postId);

    /** Earliest report time on the post, or null when it has none. Used to date evidence against deletion cycles. */
    @Query(value = "SELECT MIN(r.createdat) FROM cheer_post_reports r WHERE r.post_id = :postId", nativeQuery = true)
    Object findEarliestReportAtRaw(@Param("postId") Long postId);

    default LocalDateTime findEarliestReportAt(Long postId) {
        return ReportEvidenceRetentionPolicy.toLocalDateTime(findEarliestReportAtRaw(postId));
    }
}
