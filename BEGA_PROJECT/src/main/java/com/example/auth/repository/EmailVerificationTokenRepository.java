package com.example.auth.repository;

import com.example.auth.entity.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EmailVerificationToken t join fetch t.user where t.tokenDigest = :tokenDigest")
    Optional<EmailVerificationToken> findByTokenDigestForUpdate(@Param("tokenDigest") String tokenDigest);

    @Modifying
    @Query("delete from EmailVerificationToken t where t.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from EmailVerificationToken t where t.user.id = :userId and t.id <> :keepTokenId")
    int deleteOtherTokensOfUser(@Param("userId") Long userId, @Param("keepTokenId") Long keepTokenId);
}
