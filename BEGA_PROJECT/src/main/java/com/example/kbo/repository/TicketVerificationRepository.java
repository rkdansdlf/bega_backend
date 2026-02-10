package com.example.kbo.repository;

import com.example.kbo.entity.TicketVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface TicketVerificationRepository extends JpaRepository<TicketVerification, String> {

    /**
     * 미소비 & 미만료 토큰 조회
     */
    Optional<TicketVerification> findByTokenAndConsumedFalseAndExpiresAtAfter(String token, Instant now);

    /**
     * 원자적 토큰 소비: 단일 UPDATE로 consumed=true 설정.
     * 동시 요청 시 하나만 성공 (affected rows = 1).
     */
    @Modifying
    @Query("UPDATE TicketVerification tv SET tv.consumed = true WHERE tv.token = :token AND tv.consumed = false AND tv.expiresAt > :now")
    int consumeByToken(@org.springframework.data.repository.query.Param("token") String token,
            @org.springframework.data.repository.query.Param("now") Instant now);

    /**
     * 만료된 토큰 정리
     */
    @Modifying
    @Query("DELETE FROM TicketVerification tv WHERE tv.expiresAt < :now")
    void deleteExpiredTokens(Instant now);
}
