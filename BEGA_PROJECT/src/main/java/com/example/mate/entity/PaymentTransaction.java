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
@Table(name = "payment_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "buyer_user_id", nullable = false)
    private Long buyerUserId;

    @Column(name = "seller_user_id", nullable = false)
    private Long sellerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false, length = 30)
    private PaymentFlowType flowType;

    @Column(name = "order_id", nullable = false, length = 100, unique = true)
    private String orderId;

    @Column(name = "payment_key", nullable = false, length = 200, unique = true)
    private String paymentKey;

    @Column(name = "gross_amount", nullable = false)
    private Integer grossAmount;

    @Column(name = "fee_amount", nullable = false)
    private Integer feeAmount;

    @Column(name = "refund_amount", nullable = false)
    private Integer refundAmount;

    @Column(name = "net_amount", nullable = false)
    private Integer netAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 30)
    private SettlementStatus settlementStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason_type", length = 30)
    private CancelReasonType cancelReasonType;

    @Column(name = "cancel_memo", length = 1000)
    private String cancelMemo;

    @Column(name = "refund_policy_applied", length = 100)
    private String refundPolicyApplied;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (feeAmount == null) {
            feeAmount = 0;
        }
        if (refundAmount == null) {
            refundAmount = 0;
        }
        if (paymentStatus == null) {
            paymentStatus = PaymentStatus.PAID;
        }
        if (settlementStatus == null) {
            settlementStatus = SettlementStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
