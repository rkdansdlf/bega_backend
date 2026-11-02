package com.example.auth.repository;

import com.example.auth.entity.LegacyPostImageHold;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegacyPostImageHoldRepository extends JpaRepository<LegacyPostImageHold, Long> {
    boolean existsByOwnerUserId(Long ownerUserId);

    List<LegacyPostImageHold> findByOwnerUserIdOrderByPostImageIdAsc(Long ownerUserId, Pageable pageable);
}
