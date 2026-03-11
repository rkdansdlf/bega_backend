package com.example.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "trusted_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrustedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 128)
    private String fingerprint;

    @Column(name = "device_label", nullable = false, length = 255)
    private String deviceLabel;

    @Column(name = "device_type", nullable = false, length = 32)
    private String deviceType;

    @Column(nullable = false, length = 64)
    private String browser;

    @Column(nullable = false, length = 64)
    private String os;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "last_login_at", nullable = false)
    private LocalDateTime lastLoginAt;

    @Column(name = "last_ip", length = 64)
    private String lastIp;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (firstSeenAt == null) {
            firstSeenAt = now;
        }
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
        if (lastLoginAt == null) {
            lastLoginAt = now;
        }
    }
}
