package com.example.mate.dto;

import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class TossPaymentDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PrepareClientRequest {
        private Long partyId;
        @Builder.Default
        private PaymentFlowType flowType = PaymentFlowType.DEPOSIT;
        private String cancelPolicyVersion;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PrepareResponse {
        private Long intentId;
        private String orderId;
        private Integer amount;
        private String currency;
        private String orderName;
        private PaymentFlowType flowType;
        private String cancelPolicyVersion;
        private PartyApplication.PaymentType paymentType;
    }

    /** Toss API에 전송하는 결제 승인 요청 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConfirmRequest {
        private String paymentKey;
        private String orderId;
        private Integer amount;
    }

    /** Toss API로부터 받는 결제 승인 응답 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmResponse {
        private String paymentKey;
        private String orderId;
        private String status;       // e.g. "DONE"
        private Integer totalAmount;
        private String method;       // e.g. "카드"
    }

    /** Toss API에 전송하는 결제 취소 요청 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CancelRequest {
        private String cancelReason;
        private Integer cancelAmount;
    }

    /** Toss API 결제 취소 응답 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelResponse {
        private String paymentKey;
        private String status;
        private Integer totalAmount;
    }

    /** Toss API 오류 응답 파싱용 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private String code;
        private String message;
    }

    /**
     * 프론트엔드가 /api/payments/toss/confirm 으로 전송하는 요청.
     * 결제 승인 정보 + 파티 신청 데이터를 하나의 요청으로 처리.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClientConfirmRequest {
        // Toss 결제 승인 정보
        private String paymentKey;
        private String orderId;
        private Long intentId;
        private PaymentFlowType flowType;
        private String cancelPolicyVersion;

        // 파티 신청 데이터
        private Long partyId;
        private String message;
        private String verificationToken;
        private Boolean ticketVerified;
        private String ticketImageUrl;
        private PartyApplication.PaymentType paymentType;
    }
}
