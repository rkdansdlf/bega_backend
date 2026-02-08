package com.example.mate.dto;

import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
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
    public static class ApprovalRequest {
        private Boolean approve; // true: 승인, false: 거절
    }
}