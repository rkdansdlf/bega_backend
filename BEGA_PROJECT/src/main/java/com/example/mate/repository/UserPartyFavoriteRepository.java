package com.example.mate.repository;

import com.example.mate.entity.UserPartyFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPartyFavoriteRepository extends JpaRepository<UserPartyFavorite, Long> {

    List<UserPartyFavorite> findByUserId(Long userId);

    Optional<UserPartyFavorite> findByUserIdAndPartyId(Long userId, Long partyId);

    boolean existsByUserIdAndPartyId(Long userId, Long partyId);

    void deleteByUserIdAndPartyId(Long userId, Long partyId);
}
