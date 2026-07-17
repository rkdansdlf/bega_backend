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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "payout_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_payout_payment_tx",
                columnNames = "payment_transaction_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_transaction_id", nullable = false)
    private Long paymentTransactionId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "requested_amount", nullable = false)
    private Integer requestedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SettlementStatus status;

    @Column(name = "provider_ref", length = 200)
    private String providerRef;

    @Column(name = "provider_code", length = 30)
    private String providerCode;

    @Column(name = "provider_seller_id", length = 200)
    private String providerSellerId;

    @Column(name = "claim_protocol", length = 30)
    private String claimProtocol;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "last_retry_at")
    private Instant lastRetryAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "fail_reason", length = 1000)
    private String failReason;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "recovery_offset_amount", nullable = false)
    private Integer recoveryOffsetAmount;

    @Column(name = "recovery_offset_reserved_at")
    private Instant recoveryOffsetReservedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = SettlementStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (recoveryOffsetAmount == null) {
            recoveryOffsetAmount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
