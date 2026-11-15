package com.example.media.repository;

import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    Optional<MediaAsset> findByObjectKey(String objectKey);

    List<MediaAsset> findByObjectKeyIn(Collection<String> objectKeys);

    @Query("SELECT a.id FROM MediaAsset a WHERE a.objectKey IN :keys ORDER BY a.id")
    List<Long> findIdsByObjectKeyInOrderById(@Param("keys") Collection<String> keys);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM MediaAsset a WHERE a.id = :id")
    Optional<MediaAsset> findByIdForUpdate(@Param("id") Long id);

    Optional<MediaAsset> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    boolean existsByOwnerUserIdAndStatus(Long ownerUserId, MediaAssetStatus status);

    List<MediaAsset> findByOwnerUserIdAndIdGreaterThanOrderByIdAsc(
            Long ownerUserId,
            Long afterId,
            Pageable pageable);

    Optional<MediaAsset> findByDerivedFrom_Id(Long assetId);

    List<MediaAsset> findByDomainAndStatusOrderByIdDesc(MediaDomain domain, MediaAssetStatus status, Pageable pageable);

    List<MediaAsset> findByDomainAndStatusAndDerivedFromIsNullOrderByIdDesc(
            MediaDomain domain,
            MediaAssetStatus status,
            Pageable pageable);

    long countByOwnerUserIdAndDomainAndCreatedAtGreaterThanEqualAndStatusNot(
            Long ownerUserId,
            MediaDomain domain,
            LocalDateTime createdAt,
            MediaAssetStatus excludedStatus);

    List<MediaAsset> findByStatusAndUploadExpiresAtBefore(MediaAssetStatus status, LocalDateTime cutoff);

    List<MediaAsset> findByStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAscIdAsc(
            MediaAssetStatus status,
            LocalDateTime cutoff,
            Pageable pageable);

    List<MediaAsset> findByStatusAndFinalizeLeaseUntilBeforeAndUploadExpiresAtBeforeOrderByFinalizeLeaseUntilAscUploadExpiresAtAscIdAsc(
            MediaAssetStatus status,
            LocalDateTime leaseCutoff,
            LocalDateTime uploadCutoff,
            Pageable pageable);

    List<MediaAsset> findByStatusAndStagingObjectKeyIsNotNullAndStagingObjectKeyNotAndUploadExpiresAtBeforeAndIdGreaterThanOrderByIdAsc(
            MediaAssetStatus status,
            String emptyKey,
            LocalDateTime uploadCutoff,
            Long afterId,
            Pageable pageable);

    List<MediaAsset> findByStatusAndFinalizeTokenIsNotNullAndFinalizeLeaseUntilBeforeOrderByFinalizeLeaseUntilAscIdAsc(
            MediaAssetStatus status,
            LocalDateTime leaseCutoff,
            Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set staging_object_key = :clearedKey,
                   updated_at = :now
             where id = :assetId
               and status = 'READY'
               and staging_object_key = :expectedKey
               and upload_expires_at <= :eligibleBefore
            """, nativeQuery = true)
    int clearReadyStagingObjectKey(
            @Param("assetId") Long assetId,
            @Param("expectedKey") String expectedKey,
            @Param("clearedKey") String clearedKey,
            @Param("now") LocalDateTime now,
            @Param("eligibleBefore") LocalDateTime eligibleBefore);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set status = 'FINALIZING',
                   finalize_token = :cleanupToken,
                   finalize_lease_until = :leaseUntil,
                   updated_at = :now
             where id = :assetId
               and status = 'PENDING'
               and upload_expires_at <= :uploadCutoff
            """, nativeQuery = true)
    int claimExpiredPendingForCleanup(
            @Param("assetId") Long assetId,
            @Param("cleanupToken") String cleanupToken,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("uploadCutoff") LocalDateTime uploadCutoff);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set status = 'FINALIZING',
                   finalize_token = :token,
                   finalize_lease_until = :leaseUntil,
                   updated_at = :now
             where id = :assetId
               and owner_user_id = :ownerUserId
               and (status = 'PENDING'
                    or (status = 'FINALIZING' and finalize_lease_until <= :now))
            """, nativeQuery = true)
    int claimFinalize(
            @Param("assetId") Long assetId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("token") String token,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set status = 'DELETED',
                   owner_user_id = null,
                   original_file_name = null,
                   declared_content_type = 'application/octet-stream',
                   declared_bytes = 0,
                   declared_width = 0,
                   declared_height = 0,
                   stored_content_type = null,
                   stored_bytes = null,
                   stored_width = null,
                   stored_height = null,
                   derived_from_asset_id = null,
                   finalize_token = :cleanupToken,
                   finalize_lease_until = :leaseUntil,
                   finalize_cleanup_verified_at = null,
                   updated_at = :now
             where id = :assetId
               and owner_user_id = :ownerUserId
               and status <> 'CHEER_PURGE_PENDING'
            """, nativeQuery = true)
    int claimOwnedAssetForAccountDeletion(
            @Param("assetId") Long assetId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("cleanupToken") String cleanupToken,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set status = 'DELETED',
                   finalize_token = :cleanupToken,
                   finalize_lease_until = :leaseUntil,
                   finalize_cleanup_verified_at = null,
                   updated_at = :now
             where id = :assetId
               and owner_user_id = :ownerUserId
               and status <> 'CHEER_PURGE_PENDING'
               and not exists (
                    select 1 from media_asset_links l join media_assets linked on linked.id = l.asset_id
                     where l.asset_id = :assetId or linked.derived_from_asset_id = :assetId
                        or linked.id = (select derived_from_asset_id from media_assets where id = :assetId)
               )
               and (status <> 'DELETED'
                    or finalize_token is null
                    or finalize_lease_until <= :now)
            """, nativeQuery = true)
    int claimOwnedAssetForDeletion(
            @Param("assetId") Long assetId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("cleanupToken") String cleanupToken,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update media_assets
               set status = 'DELETED', finalize_token = :cleanupToken,
                   finalize_lease_until = :leaseUntil, finalize_cleanup_verified_at = null,
                   updated_at = :now
             where id = :assetId
               and domain = :domain and status = 'READY' and created_at <= :cutoff
               and not exists (
                    select 1 from media_asset_links l join media_assets linked on linked.id = l.asset_id
                     where l.asset_id = :assetId or linked.derived_from_asset_id = :assetId
                        or linked.id = (select derived_from_asset_id from media_assets where id = :assetId)
               )
            """, nativeQuery = true)
    int claimUnlinkedMediaForCleanup(@Param("assetId") Long assetId,
            @Param("domain") String domain, @Param("cutoff") LocalDateTime cutoff,
            @Param("cleanupToken") String cleanupToken, @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set finalize_lease_until = :leaseUntil,
                   updated_at = :now
             where id = :assetId
               and status = 'FINALIZING'
               and finalize_token = :token
               and finalize_lease_until > :now
            """, nativeQuery = true)
    int renewFinalizeClaim(
            @Param("assetId") Long assetId,
            @Param("token") String token,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set finalize_object_key = :objectKey,
                   finalize_feed_object_key = :feedObjectKey,
                   updated_at = :now
             where id = :assetId
               and status = 'FINALIZING'
               and finalize_token = :token
               and finalize_lease_until > :now
            """, nativeQuery = true)
    int recordFinalizeObjectKeys(
            @Param("assetId") Long assetId,
            @Param("token") String token,
            @Param("objectKey") String objectKey,
            @Param("feedObjectKey") String feedObjectKey,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set finalize_object_key = null,
                   finalize_feed_object_key = null,
                   updated_at = :now
             where id = :assetId
               and status = 'FINALIZING'
               and finalize_token = :token
               and finalize_lease_until > :now
            """, nativeQuery = true)
    int clearFinalizeObjectKeys(
            @Param("assetId") Long assetId,
            @Param("token") String token,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set status = 'PENDING',
                   finalize_token = null,
                   finalize_lease_until = null,
                   updated_at = :now
             where id = :assetId
               and status = 'FINALIZING'
               and finalize_token = :token
            """, nativeQuery = true)
    int releaseFinalizeClaim(
            @Param("assetId") Long assetId,
            @Param("token") String token,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set status = 'READY',
                   object_key = :objectKey,
                   stored_content_type = :contentType,
                   stored_bytes = :storedBytes,
                   stored_width = :storedWidth,
                   stored_height = :storedHeight,
                   finalized_at = :now,
                   finalize_token = null,
                   finalize_lease_until = null,
                   finalize_object_key = null,
                   finalize_feed_object_key = null,
                   updated_at = :now
             where id = :assetId
               and owner_user_id = :ownerUserId
               and status = 'FINALIZING'
               and finalize_token = :token
               and finalize_lease_until > :now
            """, nativeQuery = true)
    int completeFinalizeClaim(
            @Param("assetId") Long assetId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("token") String token,
            @Param("objectKey") String objectKey,
            @Param("contentType") String contentType,
            @Param("storedBytes") long storedBytes,
            @Param("storedWidth") int storedWidth,
            @Param("storedHeight") int storedHeight,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set finalize_token = :cleanupToken,
                   finalize_lease_until = :leaseUntil,
                   updated_at = :now
             where id = :assetId
               and status = 'FINALIZING'
               and finalize_token = :previousToken
               and finalize_lease_until <= :now
               and upload_expires_at <= :uploadCutoff
            """, nativeQuery = true)
    int claimExpiredFinalizeForCleanup(
            @Param("assetId") Long assetId,
            @Param("previousToken") String previousToken,
            @Param("cleanupToken") String cleanupToken,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("uploadCutoff") LocalDateTime uploadCutoff);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set status = 'DELETED',
                   finalize_lease_until = :leaseUntil,
                   finalize_cleanup_verified_at = null,
                   updated_at = :now
             where id = :assetId
               and status = 'FINALIZING'
               and finalize_token = :cleanupToken
            """, nativeQuery = true)
    int markExpiredFinalizeDeleted(
            @Param("assetId") Long assetId,
            @Param("cleanupToken") String cleanupToken,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set finalize_cleanup_verified_at = :verifiedAt,
                   finalize_lease_until = :leaseUntil,
                   updated_at = :now
             where id = :assetId
               and status = 'DELETED'
               and finalize_token = :cleanupToken
            """, nativeQuery = true)
    int updateDeletedFinalizeVerification(
            @Param("assetId") Long assetId,
            @Param("cleanupToken") String cleanupToken,
            @Param("verifiedAt") LocalDateTime verifiedAt,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set finalize_token = :cleanupToken,
                   finalize_lease_until = :leaseUntil,
                   updated_at = :now
             where id = :assetId
               and status = 'DELETED'
               and finalize_token = :previousToken
               and finalize_lease_until <= :now
            """, nativeQuery = true)
    int claimDeletedFinalizeForCleanup(
            @Param("assetId") Long assetId,
            @Param("previousToken") String previousToken,
            @Param("cleanupToken") String cleanupToken,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            update media_assets
               set staging_object_key = :clearedKey,
                   object_key = null,
                   finalize_token = null,
                   finalize_lease_until = null,
                   finalize_object_key = null,
                   finalize_feed_object_key = null,
                   finalize_cleanup_verified_at = null,
                   updated_at = :now
             where id = :assetId
               and status = 'DELETED'
               and finalize_token = :cleanupToken
               and finalize_cleanup_verified_at <= :verifiedBefore
            """, nativeQuery = true)
    int clearDeletedFinalizeTombstone(
            @Param("assetId") Long assetId,
            @Param("cleanupToken") String cleanupToken,
            @Param("clearedKey") String clearedKey,
            @Param("now") LocalDateTime now,
            @Param("verifiedBefore") LocalDateTime verifiedBefore);

    @Query("""
            select coalesce(sum(coalesce(a.storedBytes, a.declaredBytes)), 0)
            from MediaAsset a
            where a.ownerUserId = :ownerUserId
              and a.createdAt >= :since
              and a.status <> :deletedStatus
            """)
    Long sumDailyUsageBytes(
            @Param("ownerUserId") Long ownerUserId,
            @Param("since") LocalDateTime since,
            @Param("deletedStatus") MediaAssetStatus deletedStatus);

    @Query("""
            select a
            from MediaAsset a
            where a.status = :status
              and a.createdAt <= :cutoff
              and not exists (
                  select 1
                  from MediaAssetLink l
                  where l.assetId = a.id
              )
            order by a.createdAt asc, a.id asc
            """)
    List<MediaAsset> findUnlinkedAssetsOlderThan(
            @Param("status") MediaAssetStatus status,
            @Param("cutoff") LocalDateTime cutoff);

    @Query("""
            select a
            from MediaAsset a
            where a.status = :status
              and a.createdAt <= :cutoff
              and not exists (
                  select 1
                  from MediaAssetLink l
                  where l.assetId = a.id
              )
            """)
    List<MediaAsset> findUnlinkedAssetsOlderThan(
            @Param("status") MediaAssetStatus status,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);
}
