package com.example.mate.service;

import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PayoutTransactionRepository;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PayoutClaimServiceTest {

    @Mock
    private PayoutTransactionRepository payoutTransactionRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private SellerRecoveryService sellerRecoveryService;
    @Mock
    private PaymentMetricsService metricsService;

    @InjectMocks
    private PayoutClaimService claimService;

    @Test
    void claimInitialDurablyPersistsRequestedStateBeforeReturningProviderAction() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(10L)
                .sellerUserId(20L)
                .netAmount(15000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(11L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(10L))
                .willReturn(Optional.of(payout));

        PayoutClaimService.ClaimedPayout claimed = claimService.claimInitial(10L, true);

        assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.CALL_PROVIDER);
        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(payout.getClaimProtocol()).isEqualTo("SNAPSHOT_V1");
        assertThat(payout.getNextRetryAt()).isAfter(Instant.now().plusSeconds(120));
        assertThat(payment.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        verify(payoutTransactionRepository).saveAndFlush(payout);
        verify(paymentTransactionRepository).saveAndFlush(payment);
    }

    @Test
    void legacyRequestedPayoutWithoutSnapshotIsQuarantinedWithoutProviderAction() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(30L)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(31L)
                .paymentTransactionId(30L)
                .status(SettlementStatus.REQUESTED)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();
        given(payoutTransactionRepository.findById(31L)).willReturn(Optional.of(payout));
        given(paymentTransactionRepository.findByIdForUpdate(30L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(31L)).willReturn(Optional.of(payout));
        given(payoutTransactionRepository.saveAndFlush(payout)).willReturn(payout);

        PayoutClaimService.ClaimedPayout claimed = claimService.claimRetry(31L, true);

        assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.NONE);
        assertThat(payout.getFailureCode())
                .isEqualTo("PAYOUT_LEGACY_SNAPSHOT_RECONCILIATION_REQUIRED");
        assertThat(payout.getNextRetryAt()).isNull();
        verify(metricsService).recordPayout("manual_reconciliation");
    }

    @Test
    void providerPayloadIsDurablyBoundOnce() {
        PaymentTransaction payment = PaymentTransaction.builder().id(10L).build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(11L)
                .paymentTransactionId(10L)
                .build();
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(11L)).willReturn(Optional.of(payout));
        given(payoutTransactionRepository.saveAndFlush(payout)).willReturn(payout);

        PayoutTransaction bound = claimService.bindProviderPayload(
                10L, 11L, "TOSS", "seller-toss-20");

        assertThat(bound.getProviderCode()).isEqualTo("TOSS");
        assertThat(bound.getProviderSellerId()).isEqualTo("seller-toss-20");
        verify(payoutTransactionRepository).saveAndFlush(payout);
    }

    @Test
    void providerCallClaimsUseIndependentTransactions() throws Exception {
        assertRequiresNew(PayoutClaimService.class.getMethod(
                "claimInitial", Long.class, boolean.class));
        assertRequiresNew(PayoutClaimService.class.getMethod(
                "claimRetry", Long.class, boolean.class));
        assertRequiresNew(PayoutClaimService.class.getMethod(
                "bindProviderPayload", Long.class, Long.class, String.class, String.class));
    }

    private void assertRequiresNew(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
