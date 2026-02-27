package com.example.mate.service.payout;

import org.springframework.http.HttpStatusCode;

public interface PayoutGateway {

    String getProviderCode();

    PayoutResult requestPayout(PayoutRequest request);

    default PayoutStatusResult getPayoutStatus(String providerRef) {
        return new PayoutStatusResult(providerRef, "UNKNOWN", null, null);
    }

    default SellerRegistrationResult registerSeller(SellerRegistrationRequest request) {
        throw new UnsupportedOperationException("판매자 등록을 지원하지 않는 provider입니다.");
    }

    record PayoutRequest(
            Long paymentTransactionId,
            String orderId,
            Long sellerUserId,
            String providerSellerId,
            Integer amount,
            String currency) {
    }

    record PayoutResult(String providerRef, String rawStatus) {
    }

    record PayoutStatusResult(
            String providerRef,
            String rawStatus,
            String failureCode,
            String failureMessage) {
    }

    record SellerRegistrationRequest(
            Long userId,
            String provider,
            String providerSellerId,
            String kycStatus,
            String metadataJson) {
    }

    record SellerRegistrationResult(String providerSellerId, String rawStatus) {
    }

    class PayoutGatewayException extends RuntimeException {
        private final String failureCode;
        private final HttpStatusCode statusCode;

        public PayoutGatewayException(String message, String failureCode, HttpStatusCode statusCode) {
            super(message);
            this.failureCode = failureCode;
            this.statusCode = statusCode;
        }

        public String getFailureCode() {
            return failureCode;
        }

        public HttpStatusCode getStatusCode() {
            return statusCode;
        }
    }
}
