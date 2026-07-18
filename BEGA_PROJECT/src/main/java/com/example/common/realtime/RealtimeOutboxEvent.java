package com.example.common.realtime;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "realtime_outbox_events")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RealtimeOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "envelope_version", nullable = false)
    private int envelopeVersion;

    @Column(name = "event_id", nullable = false, length = 128, unique = true)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false, length = 20)
    private RealtimeMessageEnvelope.Target target;

    @Column(name = "destination", nullable = false, length = 255)
    private String destination;

    @Column(name = "user_id", length = 128)
    private String userId;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RealtimeOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "locked_by", length = 128)
    private String lockedBy;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public static RealtimeOutboxEvent pending(
            RealtimeMessageEnvelope envelope,
            String serializedPayload,
            Instant now) {
        return RealtimeOutboxEvent.builder()
                .envelopeVersion(envelope.version())
                .eventId(envelope.eventId())
                .target(envelope.target())
                .destination(envelope.destination())
                .userId(envelope.userId())
                .payload(serializedPayload)
                .status(RealtimeOutboxStatus.PENDING)
                .attemptCount(0)
                .availableAt(now)
                .createdAt(now)
                .build();
    }
}
