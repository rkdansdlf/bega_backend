package com.example.kbo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * 서버 사이드 티켓 검증 토큰 저장 엔티티.
 * OCR 분석 후 생성되어 신청 시 소비(consume)됩니다.
 */
@Entity
@Table(name = "ticket_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketVerification {

    @Id
    @Column(name = "token", nullable = false, length = 36)
    private String token;

    @Column(name = "ticket_date")
    private String ticketDate;

    @Column(name = "ticket_stadium")
    private String ticketStadium;

    @Column(name = "home_team")
    private String homeTeam;

    @Column(name = "away_team")
    private String awayTeam;

    @Column(name = "game_id")
    private Long gameId;

    @Column(name = "consumed", nullable = false)
    @Builder.Default
    private Boolean consumed = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

}
