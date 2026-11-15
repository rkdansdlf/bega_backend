package com.example.media.repository;

import com.example.media.entity.MediaAssetLink;
import com.example.media.entity.MediaDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MediaAssetLinkRepository extends JpaRepository<MediaAssetLink, Long> {

    List<MediaAssetLink> findByDomainAndEntityId(MediaDomain domain, Long entityId);

    boolean existsByAssetId(Long assetId);

    @Query("SELECT COUNT(l) FROM MediaAssetLink l WHERE l.assetId = :assetId "
            + "AND l.domain = com.example.media.entity.MediaDomain.CHEER "
            + "AND l.role = com.example.media.entity.MediaLinkRole.CHEER_IMAGE AND l.entityId = :postId")
    long countCheerPostLink(@Param("assetId") Long assetId, @Param("postId") Long postId);

    @Query("SELECT DISTINCT a.ownerUserId FROM MediaAssetLink l JOIN l.asset a "
            + "WHERE l.domain = com.example.media.entity.MediaDomain.CHEER AND l.entityId = :postId "
            + "AND a.ownerUserId IS NOT NULL")
    List<Long> findCheerAssetOwnerIds(@Param("postId") Long postId);

    @Query("SELECT l.assetId FROM MediaAssetLink l WHERE l.domain = com.example.media.entity.MediaDomain.CHEER "
            + "AND l.entityId = :postId ORDER BY l.assetId")
    List<Long> findCheerAssetIds(@Param("postId") Long postId);

    @Transactional
    void deleteByAssetId(Long assetId);

    @Transactional
    void deleteByDomainAndEntityId(MediaDomain domain, Long entityId);
}
