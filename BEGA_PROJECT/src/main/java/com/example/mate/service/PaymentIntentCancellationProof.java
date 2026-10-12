package com.example.mate.service;

import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.exception.TossPaymentException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Objects;

final class PaymentIntentCancellationProof {

    private PaymentIntentCancellationProof() {}

    static void requireFullCancellation(
            String expectedPaymentKey,
            Integer expectedAmount,
            TossPaymentDTO.CancelResponse response) {
        if (expectedPaymentKey == null || expectedPaymentKey.isBlank()
                || expectedAmount == null || expectedAmount <= 0
                || response == null
                || !Objects.equals(expectedPaymentKey, response.getPaymentKey())
                || !Objects.equals(expectedAmount, response.getTotalAmount())) {
            throw unverifiedCancellation();
        }

        long canceledAmount = completedCancellationAmount(response.getCancels());
        if (canceledAmount != expectedAmount) {
            throw unverifiedCancellation();
        }
    }

    private static long completedCancellationAmount(List<TossPaymentDTO.CancelDetail> cancels) {
        if (cancels == null) {
            return 0;
        }
        return cancels.stream()
                .filter(Objects::nonNull)
                .filter(cancel -> isCompleted(cancel.getCancelStatus()))
                .map(TossPaymentDTO.CancelDetail::getCancelAmount)
                .filter(Objects::nonNull)
                .filter(amount -> amount > 0)
                .mapToLong(Integer::longValue)
                .sum();
    }

    private static boolean isCompleted(String status) {
        return "DONE".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status);
    }

    private static TossPaymentException unverifiedCancellation() {
        return new TossPaymentException(
                "TOSS_CANCEL_RESPONSE_UNVERIFIED",
                "결제 취소 응답에서 결제 키와 전체 취소 완료를 확인할 수 없습니다.",
                HttpStatus.BAD_GATEWAY);
    }
}
