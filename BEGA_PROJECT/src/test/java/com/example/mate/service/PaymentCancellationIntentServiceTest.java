package com.example.mate.service;

import com.example.mate.entity.CancelReasonType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCancellationIntentServiceTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PartyRepository partyRepository;

    @InjectMocks
    private PaymentCancellationIntentService intentService;

    @Test
    void prepareCommitsNewImmutableIntentBeforeProviderCall() {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(10L)
                .grossAmount(20000)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        PaymentCancellationIntentService.CancellationIntent proposed =
                new PaymentCancellationIntentService.CancellationIntent(
                        CancelReasonType.BUYER_CHANGED_MIND,
                        "최초 요청",
                        18000,
                        2000,
                        "PARTIAL_REFUND_WITH_FEE",
                        false);
        given(paymentTransactionRepository.findByIdForUpdate(10L))
                .willReturn(Optional.of(transaction));
        given(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PaymentCancellationIntentService.PreparedCancellation prepared =
                intentService.prepare(transaction, proposed);

        assertThat(prepared.transaction()).isSameAs(transaction);
        assertThat(prepared.intent().existing()).isFalse();
        assertThat(transaction.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_REQUESTED);
        assertThat(transaction.getRequestedRefundAmount()).isEqualTo(18000);
        assertThat(transaction.getRequestedFeeAmount()).isEqualTo(2000);
        assertThat(transaction.getCancellationRequestedAt()).isNotNull();
        verify(paymentTransactionRepository).saveAndFlush(transaction);
    }

    @Test
    void prepareReusesExistingIntentWhenConcurrentRequestProposesDifferentValues() {
        Instant requestedAt = Instant.parse("2026-07-15T00:00:00Z");
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(11L)
                .grossAmount(20000)
                .paymentStatus(PaymentStatus.REFUND_FAILED)
                .cancelReasonType(CancelReasonType.BUYER_CHANGED_MIND)
                .cancelMemo("원본 메모")
                .requestedRefundAmount(18000)
                .requestedFeeAmount(2000)
                .refundPolicyApplied("PARTIAL_REFUND_WITH_FEE")
                .cancellationRequestedAt(requestedAt)
                .build();
        PaymentCancellationIntentService.CancellationIntent proposed =
                new PaymentCancellationIntentService.CancellationIntent(
                        CancelReasonType.SYSTEM,
                        "바꾸려는 메모",
                        20000,
                        0,
                        "FULL_REFUND",
                        false);
        given(paymentTransactionRepository.findByIdForUpdate(11L))
                .willReturn(Optional.of(transaction));
        given(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PaymentCancellationIntentService.PreparedCancellation prepared =
                intentService.prepare(transaction, proposed);

        assertThat(prepared.intent().reasonType()).isEqualTo(CancelReasonType.BUYER_CHANGED_MIND);
        assertThat(prepared.intent().memo()).isEqualTo("원본 메모");
        assertThat(prepared.intent().refundAmount()).isEqualTo(18000);
        assertThat(prepared.intent().existing()).isTrue();
        assertThat(transaction.getCancellationRequestedAt()).isEqualTo(requestedAt);
    }

    @Test
    void prepareUsesRequiresNewTransaction() throws Exception {
        Method method = PaymentCancellationIntentService.class
                .getMethod(
                        "prepare",
                        PaymentTransaction.class,
                        PaymentCancellationIntentService.CancellationIntent.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
