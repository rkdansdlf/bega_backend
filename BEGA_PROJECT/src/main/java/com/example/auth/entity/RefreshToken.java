package com.example.auth.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 토큰 소유자를 식별용 이메일
    private String email;

    // HMAC-SHA256 digest only. Raw refresh bearer tokens must never be persisted.
    @Column(name = "token_digest", nullable = false, unique = true, length = 64)
    private String tokenDigest;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    // 만료 시간
    @jakarta.persistence.Column(name = "expirydate")
    private LocalDateTime expiryDate;

    @Column(name = "device_type", length = 32)
    private String deviceType;

    @Column(name = "device_label", length = 255)
    private String deviceLabel;

    @Column(name = "browser", length = 64)
    private String browser;

    @Column(name = "os", length = 64)
    private String os;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}
