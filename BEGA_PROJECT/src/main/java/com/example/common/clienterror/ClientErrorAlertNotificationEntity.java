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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "client_error_alert_notifications", indexes = {
        @Index(name = "idx_client_error_alert_notifications_fingerprint", columnList = "fingerprint"),
        @Index(name = "idx_client_error_alert_notifications_notified_at", columnList = "notified_at")
})
public class ClientErrorAlertNotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientErrorBucket bucket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ClientErrorSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_channel", nullable = false, length = 20)
    private ClientErrorAlertChannel channel;

    @Column(nullable = false, length = 500)
    private String route;

    @Column(name = "status_group", nullable = false, length = 8)
    private String statusGroup;

    @Column(name = "observed_count", nullable = false)
    private Long observedCount;

    @Column(name = "threshold_count", nullable = false)
    private Integer thresholdCount;

    @Column(name = "window_minutes", nullable = false)
    private Integer windowMinutes;

    @Column(name = "latest_event_id", length = 64)
    private String latestEventId;

    @Column(name = "latest_message", length = 1000)
    private String latestMessage;

    @Column(name = "latest_occurred_at")
    private LocalDateTime latestOccurredAt;

    @Column(name = "notified_at", nullable = false)
    private LocalDateTime notifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private ClientErrorAlertDeliveryStatus deliveryStatus;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @PrePersist
    void onPersist() {
        if (notifiedAt == null) {
            notifiedAt = LocalDateTime.now(ClientErrorSupport.UTC);
        }
    }
}
