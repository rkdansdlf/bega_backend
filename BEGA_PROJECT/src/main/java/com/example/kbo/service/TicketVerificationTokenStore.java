package com.example.kbo.service;

import com.example.kbo.dto.TicketInfo;
import com.example.kbo.entity.TicketVerification;
import com.example.kbo.repository.TicketVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * DB-backed store for ticket verification tokens.
 * When a ticket is successfully analyzed via OCR, a token is generated
 * and persisted. The client submits this token with their party
 * application to prove the server verified the ticket.
 */
@Component
@RequiredArgsConstructor
public class TicketVerificationTokenStore {

    private static final long TOKEN_TTL_MINUTES = 30;

    private final TicketVerificationRepository verificationRepository;

    @Transactional
    public String generateToken(TicketInfo ticketInfo) {
        String token = UUID.randomUUID().toString();

        TicketVerification entity = TicketVerification.builder()
                .token(token)
                .ticketDate(ticketInfo.getDate())
                .ticketStadium(ticketInfo.getStadium())
                .homeTeam(ticketInfo.getHomeTeam())
                .awayTeam(ticketInfo.getAwayTeam())
                .gameId(ticketInfo.getGameId())
                .consumed(false)
                .expiresAt(Instant.now().plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES))
                .build();

        verificationRepository.save(entity);
        return token;
    }

    /**
     * Atomically consumes a token and returns the ticket info.
     * Uses a single UPDATE query to prevent double-consume under concurrency.
     * Returns null if token is invalid, expired, or already consumed.
     */
    @Transactional
    public TicketInfo consumeToken(String token) {
        if (token == null || token.isBlank())
            return null;

        // Step 1: Atomic consume — only one concurrent request can succeed
        int affected = verificationRepository.consumeByToken(token, Instant.now());
        if (affected == 0) {
            return null; // Token not found, expired, or already consumed
        }

        // Step 2: Read the (now-consumed) entity to extract ticket data
        return verificationRepository.findById(token)
                .map(entity -> TicketInfo.builder()
                        .date(entity.getTicketDate())
                        .stadium(entity.getTicketStadium())
                        .homeTeam(entity.getHomeTeam())
                        .awayTeam(entity.getAwayTeam())
                        .gameId(entity.getGameId())
                        .build())
                .orElse(null);
    }

    /**
     * 만료된 토큰 정리 (15분마다 실행)
     */
    @Scheduled(fixedRate = 900_000)
    @Transactional
    public void cleanupExpiredTokens() {
        verificationRepository.deleteExpiredTokens(Instant.now());
    }
}
