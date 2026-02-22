package com.example.mate.dto;

import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.CancelReasonType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.SettlementStatus;
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
        private Long partyId;
        private Long applicantId;
        private String applicantName;
        private Party.BadgeType applicantBadge;
        private Double applicantRating;
        private String message;
        private Integer depositAmount;
        private PartyApplication.PaymentType paymentType;
        private Boolean ticketVerified; // Client-side flag (ignored for verification, used for UI)
        private String ticketImageUrl;
        private String verificationToken; // Server-side proof from TicketVerificationTokenStore
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long partyId;
        private Long applicantId;
        private String applicantName;
        private Party.BadgeType applicantBadge;
        private Double applicantRating;
        private String message;
        private Integer depositAmount;
        private Boolean isPaid;
        private Boolean isApproved;
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
                    .applicantId(application.getApplicantId())
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
                    .paymentKey(application.getPaymentKey())
                    .orderId(application.getOrderId())
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
