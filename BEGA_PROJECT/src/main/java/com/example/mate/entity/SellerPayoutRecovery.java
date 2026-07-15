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
@Table(name = "seller_payout_recoveries", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_seller_recovery_source_payment",
                columnNames = "source_payment_transaction_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerPayoutRecovery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_payment_transaction_id", nullable = false)
    private Long sourcePaymentTransactionId;

    @Column(name = "payout_transaction_id")
    private Long payoutTransactionId;

    @Column(name = "seller_user_id", nullable = false)
    private Long sellerUserId;

    @Column(name = "original_paid_amount", nullable = false)
    private Integer originalPaidAmount;

    @Column(name = "target_net_amount", nullable = false)
    private Integer targetNetAmount;

    @Column(name = "recovery_amount", nullable = false)
    private Integer recoveryAmount;

    @Column(name = "recovered_amount", nullable = false)
    private Integer recoveredAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SellerRecoveryStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (recoveredAmount == null) {
            recoveredAmount = 0;
        }
        if (status == null) {
            status = recoveryAmount != null && recoveryAmount == 0
                    ? SellerRecoveryStatus.RECOVERED
                    : SellerRecoveryStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
