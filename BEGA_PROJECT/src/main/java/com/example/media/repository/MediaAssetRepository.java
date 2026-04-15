package com.example.media.repository;

import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    Optional<MediaAsset> findByObjectKey(String objectKey);

    List<MediaAsset> findByObjectKeyIn(Collection<String> objectKeys);

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
            """)
    List<MediaAsset> findUnlinkedAssetsOlderThan(
            @Param("status") MediaAssetStatus status,
            @Param("cutoff") LocalDateTime cutoff);
}
