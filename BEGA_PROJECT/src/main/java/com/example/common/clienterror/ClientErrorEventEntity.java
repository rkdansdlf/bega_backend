package com.example.common.clienterror;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "client_error_events", indexes = {
        @Index(name = "idx_client_error_events_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_client_error_events_bucket_occurred_at", columnList = "bucket, occurred_at"),
        @Index(name = "idx_client_error_events_fingerprint", columnList = "fingerprint"),
        @Index(name = "idx_client_error_events_normalized_route", columnList = "normalized_route")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_client_error_events_event_id", columnNames = "event_id")
})
public class ClientErrorEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientErrorBucket bucket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ClientErrorSource source;

    @Column(nullable = false, length = 1000)
    private String message;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "stack_trace")
    private String stack;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "component_stack")
    private String componentStack;

    @Column(nullable = false, length = 500)
    private String route;

    @Column(name = "normalized_route", nullable = false, length = 500)
    private String normalizedRoute;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "status_group", nullable = false, length = 8)
    private String statusGroup;

    @Column(name = "response_code", length = 64)
    private String responseCode;

    @Column(length = 16)
    private String method;

    @Column(length = 500)
    private String endpoint;

    @Column(name = "normalized_endpoint", length = 500)
    private String normalizedEndpoint;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "feedback_count", nullable = false)
    private Integer feedbackCount;

    @PrePersist
    void onPersist() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now(ClientErrorSupport.UTC);
        }
        if (feedbackCount == null) {
            feedbackCount = 0;
        }
    }
}
