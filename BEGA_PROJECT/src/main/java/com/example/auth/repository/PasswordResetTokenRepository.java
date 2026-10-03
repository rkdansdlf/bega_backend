package com.example.auth.repository;

import com.example.auth.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select resetToken from PasswordResetToken resetToken where resetToken.token = :token")
    Optional<PasswordResetToken> findByTokenForUpdate(@Param("token") String token);

    void deleteByUserId(Long userId);
}
