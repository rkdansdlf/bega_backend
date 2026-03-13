package com.example.mate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "payment_intents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 100)
    private String orderId;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Column(name = "expected_amount", nullable = false)
    private Integer expectedAmount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false, length = 30)
    private PaymentFlowType flowType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PartyApplication.PaymentType paymentType;

    @Column(name = "cancel_policy_version", length = 50)
    private String cancelPolicyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_mode", nullable = false, length = 20)
    private IntentMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IntentStatus status;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (currency == null) {
            currency = "KRW";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum IntentMode {
        PREPARED,
        LEGACY
    }

    public enum IntentStatus {
        PREPARED,
        CONFIRMED,
        APPLICATION_CREATED,
        CANCEL_REQUESTED,
        CANCELED,
        CANCEL_FAILED,
        EXPIRED
    }
}
