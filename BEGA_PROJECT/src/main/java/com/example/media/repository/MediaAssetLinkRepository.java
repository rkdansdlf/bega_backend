package com.example.media.repository;

import com.example.media.entity.MediaAssetLink;
import com.example.media.entity.MediaDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface MediaAssetLinkRepository extends JpaRepository<MediaAssetLink, Long> {

    List<MediaAssetLink> findByDomainAndEntityId(MediaDomain domain, Long entityId);

    boolean existsByAssetId(Long assetId);

    @Transactional
    void deleteByDomainAndEntityId(MediaDomain domain, Long entityId);
}
