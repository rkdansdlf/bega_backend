package com.example.mate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "payment_intent_reconciliation_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentIntentReconciliationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_intent_id", nullable = false)
    private Long paymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private EventType eventType;

    @Column(name = "requested_payment_key", length = 200)
    private String requestedPaymentKey;

    @Column(name = "candidate_payment_key", length = 200)
    private String candidatePaymentKey;

    @Column(name = "candidate_order_id", length = 100)
    private String candidateOrderId;

    @Column(name = "candidate_amount")
    private Integer candidateAmount;

    @Column(name = "candidate_status", length = 30)
    private String candidateStatus;

    @Column(name = "verified_payment_key", length = 200)
    private String verifiedPaymentKey;

    @Column(name = "verified_order_id", length = 100)
    private String verifiedOrderId;

    @Column(name = "verified_amount")
    private Integer verifiedAmount;

    @Column(name = "verified_status", length = 30)
    private String verifiedStatus;

    @Column(name = "evidence_reference", length = 300)
    private String evidenceReference;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public enum EventType {
        IDENTITY_MISMATCH_HOLD,
        VERIFIED_PAYMENT_PROPOSED,
        VERIFIED_CANCELED_PROPOSED,
        VERIFIED_REFUNDED_PROPOSED,
        VERIFIED_PAYMENT_BOUND,
        VERIFIED_CANCELED,
        VERIFIED_REFUNDED
    }
}
