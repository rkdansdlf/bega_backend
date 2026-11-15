package com.example.auth.repository;

import com.example.auth.entity.OAuthEmailChallenge;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OAuthEmailChallengeRepository extends JpaRepository<OAuthEmailChallenge, Long> {

    void deleteByProviderAndProviderId(String provider, String providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OAuthEmailChallenge c where c.tokenDigest = :tokenDigest")
    Optional<OAuthEmailChallenge> findByTokenDigestForUpdate(@Param("tokenDigest") String tokenDigest);

    Optional<OAuthEmailChallenge> findByChallengeId(String challengeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OAuthEmailChallenge c where c.challengeId = :challengeId")
    Optional<OAuthEmailChallenge> findByChallengeIdForUpdate(@Param("challengeId") String challengeId);
}
