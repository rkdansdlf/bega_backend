package com.example.mate.service;

import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.exception.TossPaymentException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentIntentCancellationProofTest {

    @Test
    void acceptsCompletedCancellationWhenPaymentKeyTotalAndAmountMatch() {
        TossPaymentDTO.CancelResponse response = response("pay-fixture", 24000, null);
        response.setCancels(List.of(new TossPaymentDTO.CancelDetail(24000, "DONE", "fixture-time", "tx-fixture")));

        assertThatCode(() -> PaymentIntentCancellationProof.requireFullCancellation(
                "pay-fixture", 24000, response)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullOrIncompleteCancellationProof() {
        TossPaymentDTO.CancelResponse mismatchedPayment = response("other-payment", 24000, 0);
        TossPaymentDTO.CancelResponse mismatchedTotal = response("pay-fixture", 23000, 0);
        TossPaymentDTO.CancelResponse partialCancellation = response("pay-fixture", 24000, 1000);
        partialCancellation.setCancels(List.of(new TossPaymentDTO.CancelDetail(
                23000, "COMPLETED", "fixture-time", "tx-fixture")));
        TossPaymentDTO.CancelResponse uncompletedCancellation = response("pay-fixture", 24000, null);
        uncompletedCancellation.setCancels(List.of(new TossPaymentDTO.CancelDetail(
                24000, "IN_PROGRESS", "fixture-time", "tx-fixture")));

        for (TossPaymentDTO.CancelResponse candidate : List.of(
                mismatchedPayment, mismatchedTotal, partialCancellation, uncompletedCancellation)) {
            assertUnverified(candidate);
        }
        assertUnverified(null);
    }

    @Test
    void rejectsZeroBalanceWithoutCompletedCancellationDetails() {
        TossPaymentDTO.CancelResponse response = response("pay-fixture", 24000, 0);

        assertUnverified(response);
    }

    private static TossPaymentDTO.CancelResponse response(String paymentKey, Integer totalAmount, Integer balanceAmount) {
        TossPaymentDTO.CancelResponse response = new TossPaymentDTO.CancelResponse(
                paymentKey, "CANCELED", totalAmount);
        response.setBalanceAmount(balanceAmount);
        return response;
    }

    private static void assertUnverified(TossPaymentDTO.CancelResponse response) {
        assertThatThrownBy(() -> PaymentIntentCancellationProof.requireFullCancellation(
                "pay-fixture", 24000, response))
                .isInstanceOf(TossPaymentException.class)
                .extracting("code")
                .isEqualTo("TOSS_CANCEL_RESPONSE_UNVERIFIED");
    }
}
