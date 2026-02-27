package com.example.mate.dto;

import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SettlementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class InternalSettlementDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PayoutResponse {
        private Long payoutId;
        private SettlementStatus status;
        private String providerRef;
        private String failureCode;
        private String failReason;
        private Instant requestedAt;
        private Instant completedAt;

        public static PayoutResponse from(PayoutTransaction payoutTransaction) {
            return PayoutResponse.builder()
                    .payoutId(payoutTransaction.getId())
                    .status(payoutTransaction.getStatus())
                    .providerRef(payoutTransaction.getProviderRef())
                    .failureCode(payoutTransaction.getFailureCode())
                    .failReason(payoutTransaction.getFailReason())
                    .requestedAt(payoutTransaction.getRequestedAt())
                    .completedAt(payoutTransaction.getCompletedAt())
                    .build();
        }
    }
}

