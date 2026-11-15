package com.example.mate.entity;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "mate_link_recon_cases", uniqueConstraints = {
        @UniqueConstraint(name = "uq_mlrc_case_key", columnNames = "case_key")
}, indexes = {
        @Index(name = "idx_mlrc_observed_id", columnList = "observed_at,id")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MateLinkReconciliationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_key", nullable = false, length = 140)
    private String caseKey;

    @Column(name = "party_id")
    private Long partyId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "payment_transaction_id")
    private Long paymentTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 48)
    private MateLinkReconciliationReasonCode reasonCode;

    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt;

    @PrePersist
    protected void onCreate() {
        if (observedAt == null) {
            observedAt = Instant.now();
        }
    }
}
