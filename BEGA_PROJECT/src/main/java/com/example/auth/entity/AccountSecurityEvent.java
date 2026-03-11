package com.example.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "account_security_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountSecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private EventType eventType;

    @Column(name = "device_label", length = 255)
    private String deviceLabel;

    @Column(name = "device_type", length = 32)
    private String deviceType;

    @Column(length = 64)
    private String browser;

    @Column(length = 64)
    private String os;

    @Column(length = 64)
    private String ip;

    @Column(nullable = false, length = 500)
    private String message;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }

    public enum EventType {
        LOGIN_SUCCESS,
        NEW_DEVICE_LOGIN,
        PASSWORD_CHANGED,
        PROVIDER_LINKED,
        PROVIDER_UNLINKED,
        SESSION_REVOKED,
        OTHER_SESSIONS_REVOKED,
        ACCOUNT_DELETION_SCHEDULED,
        ACCOUNT_DELETION_CANCELLED,
        TRUSTED_DEVICE_REMOVED
    }
}
