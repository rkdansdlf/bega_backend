package com.example.common.clienterror;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
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
@Table(name = "client_error_feedback", indexes = {
        @Index(name = "idx_client_error_feedback_event_id", columnList = "event_id"),
        @Index(name = "idx_client_error_feedback_occurred_at", columnList = "occurred_at")
})
public class ClientErrorFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String comment;

    @Column(name = "action_taken", nullable = false, length = 64)
    private String actionTaken;

    @Column(nullable = false, length = 500)
    private String route;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void onPersist() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now(ClientErrorSupport.UTC);
        }
    }
}
