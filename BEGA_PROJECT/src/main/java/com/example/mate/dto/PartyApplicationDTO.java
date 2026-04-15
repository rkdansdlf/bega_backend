package com.example.mate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.CancelReasonType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.SettlementStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

public class PartyApplicationDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotNull(message = "파티 ID는 필수입니다.")
        private Long partyId;

        @Size(max = 500, message = "신청 메시지는 500자 이하여야 합니다.")
        private String message;
        // DIRECT_TRADE: 거래 기준 금액 스냅샷, TOSS_TEST: 보증금/결제 금액

        @Min(value = 0, message = "결제 금액은 0원 이상이어야 합니다.")
        private Integer depositAmount;
        private PartyApplication.PaymentType paymentType;
        private Boolean ticketVerified; // Client-side flag (ignored for verification, used for UI)

        @Size(max = 2048, message = "티켓 이미지 URL은 2048자 이하여야 합니다.")
        private String ticketImageUrl;

        @Size(max = 128, message = "예매 인증 토큰은 128자 이하여야 합니다.")
        private String verificationToken; // Server-side proof from TicketVerificationTokenStore
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long partyId;
        private String applicantHandle;
        private String applicantName;
        private Party.BadgeType applicantBadge;
        private Double applicantRating;
        private String message;
        // DIRECT_TRADE: 거래 기준 금액 스냅샷, TOSS_TEST: 보증금/결제 금액
        private Integer depositAmount;
        @JsonProperty("isPaid")
        private Boolean isPaid;
        @JsonProperty("isApproved")
        private Boolean isApproved;
        @JsonProperty("isRejected")
        private Boolean isRejected;
        private PartyApplication.PaymentType paymentType;
        private Boolean ticketVerified;
        private String ticketImageUrl;
        private String paymentKey;
        private String orderId;
        private Integer feeAmount;
        private Integer netSettlementAmount;
        private PaymentStatus paymentStatus;
        private SettlementStatus settlementStatus;
        private Instant createdAt;
        private Instant approvedAt;
        private Instant rejectedAt;
        private Instant responseDeadline;

        public static Response from(PartyApplication application) {
            return Response.builder()
                    .id(application.getId())
                    .partyId(application.getPartyId())
                    .applicantHandle(null)
                    .applicantName(application.getApplicantName())
                    .applicantBadge(application.getApplicantBadge())
                    .applicantRating(application.getApplicantRating())
                    .message(application.getMessage())
                    .depositAmount(application.getDepositAmount())
                    .isPaid(application.getIsPaid())
                    .isApproved(application.getIsApproved())
                    .isRejected(application.getIsRejected())
                    .paymentType(application.getPaymentType())
                    .ticketVerified(application.getTicketVerified())
                    .ticketImageUrl(application.getTicketImageUrl())
                    .paymentKey(null)
                    .orderId(null)
                    .feeAmount(0)
                    .netSettlementAmount(application.getDepositAmount())
                    .paymentStatus(null)
                    .settlementStatus(null)
                    .createdAt(application.getCreatedAt())
                    .approvedAt(application.getApprovedAt())
                    .rejectedAt(application.getRejectedAt())
                    .responseDeadline(application.getResponseDeadline())
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CancelRequest {
        @Builder.Default
        private CancelReasonType cancelReasonType = CancelReasonType.BUYER_CHANGED_MIND;
        private String cancelMemo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CancelResponse {
        private Long applicationId;
        private Integer refundAmount;
        private Integer feeCharged;
        private String refundPolicyApplied;
        private PaymentStatus paymentStatus;
        private SettlementStatus settlementStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApprovalRequest {
        private Boolean approve; // true: 승인, false: 거절
    }
}
