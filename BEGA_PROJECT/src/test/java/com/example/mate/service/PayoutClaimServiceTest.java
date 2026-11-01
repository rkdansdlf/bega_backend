package com.example.mate.service;

import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.auth.service.AccountDeletionFinancialService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PayoutClaimServiceTest {

    @Mock
    private PayoutTransactionRepository payoutTransactionRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private PartyApplicationRepository partyApplicationRepository;
    @Mock
    private PartyRepository partyRepository;
    @Mock
    private SellerRecoveryService sellerRecoveryService;
    @Mock
    private PaymentMetricsService metricsService;

    @Mock
    private AccountDeletionFinancialService accountDeletionFinancialService;

    @InjectMocks
    private PayoutClaimService claimService;

    @BeforeEach
    void allowPayoutForActiveSyntheticPartyByDefault() {
        lenient().when(partyRepository.findById(50L)).thenReturn(Optional.of(
                Party.builder().id(50L).status(Party.PartyStatus.PENDING).build()));
        lenient().when(partyRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(
                Party.builder().id(50L).status(Party.PartyStatus.PENDING).build()));
    }

    @Test
    void claimInitialDurablyPersistsRequestedStateBeforeReturningProviderAction() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(10L)
                .applicationId(40L)
                .partyId(50L)
                .buyerUserId(60L)
                .orderId("ORDER-10")
                .sellerUserId(20L)
                .netAmount(15000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build();
        given(partyApplicationRepository.findById(40L))
                .willReturn(Optional.of(application(40L, 50L, 60L, "ORDER-10", true, false)));
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(11L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();
        givenPayment(payment);
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
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                paymentTransactionRepository, accountDeletionFinancialService, partyRepository,
                payoutTransactionRepository);
        order.verify(paymentTransactionRepository).findById(10L);
        order.verify(accountDeletionFinancialService).lockSellerForPayoutClaim(20L);
        order.verify(partyRepository).findByIdForUpdate(50L);
        order.verify(paymentTransactionRepository).findByIdForUpdate(10L);
        order.verify(payoutTransactionRepository)
                .findTopByPaymentTransactionIdForUpdateOrderByIdDesc(10L);
    }

    @Test
    void recoveryOffsetCompletionReleasesDeletionFallbackWhenNoClaimRemains() {
        PaymentTransaction payment = payablePayment();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(11L).paymentTransactionId(10L).sellerId(20L).status(SettlementStatus.PENDING)
                .retryCount(0).build();
        givenPayment(payment);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(10L))
                .willReturn(Optional.of(payout));
        given(partyApplicationRepository.findById(40L))
                .willReturn(Optional.of(application(40L, 50L, 60L, "ORDER-10", true, false)));
        given(sellerRecoveryService.reserveOffset(payout, 20L, 15000))
                .willReturn(new SellerRecoveryService.RecoveryOffsetResult(15000));

        PayoutClaimService.ClaimedPayout claimed = claimService.claimInitial(10L, true);

        assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.NONE);
        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(payment.getSettlementStatus()).isEqualTo(SettlementStatus.COMPLETED);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                payoutTransactionRepository, accountDeletionFinancialService);
        order.verify(payoutTransactionRepository, org.mockito.Mockito.times(2)).save(payout);
        order.verify(accountDeletionFinancialService).clearFallbackIfNoUnboundPayouts(20L);
    }

    @Test
    void bindingPayoutSnapshotTriggersCleanupOfFinalizedSellerFallback() {
        PaymentTransaction payment = payablePayment();
        payment.setSellerUserId(20L);
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(11L).paymentTransactionId(10L).sellerId(20L).status(SettlementStatus.REQUESTED)
                .retryCount(0).build();
        givenPayment(payment);
        given(payoutTransactionRepository.findByIdForUpdate(11L)).willReturn(Optional.of(payout));
        given(payoutTransactionRepository.saveAndFlush(payout)).willReturn(payout);

        claimService.bindProviderPayload(10L, 11L, "TOSS", "seller-snapshot");

        assertThat(payout.getProviderSellerId()).isEqualTo("seller-snapshot");
        assertThat(payout.getLastRetryAt()).isNotNull();
        assertThat(payout.getNextRetryAt()).isAfter(Instant.now());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                paymentTransactionRepository, accountDeletionFinancialService, partyRepository,
                payoutTransactionRepository);
        order.verify(paymentTransactionRepository).findById(10L);
        order.verify(accountDeletionFinancialService).lockSellerForPayoutClaim(20L);
        order.verify(partyRepository).findByIdForUpdate(50L);
        order.verify(paymentTransactionRepository).findByIdForUpdate(10L);
        order.verify(payoutTransactionRepository).findByIdForUpdate(11L);
        order.verify(payoutTransactionRepository).saveAndFlush(payout);
        order.verify(accountDeletionFinancialService).clearFallbackIfNoUnboundPayouts(20L);
    }

    @Test
    void claimInitialRejectsMissingApplicationBeforeCreatingPayoutClaim() {
        PaymentTransaction payment = payablePayment();
        givenPayment(payment);
        given(partyApplicationRepository.findById(40L)).willReturn(Optional.empty());

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                PayoutClaimService.PayoutEligibilityException.class,
                () -> claimService.claimInitial(10L, true)).getMessage())
                .contains("신청");

        verify(payoutTransactionRepository, org.mockito.Mockito.never())
                .saveAndFlush(org.mockito.ArgumentMatchers.any(PayoutTransaction.class));
        verify(paymentTransactionRepository, org.mockito.Mockito.never())
                .saveAndFlush(org.mockito.ArgumentMatchers.any(PaymentTransaction.class));
        verify(sellerRecoveryService, org.mockito.Mockito.never()).reserveOffset(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void claimInitialRejectsUnapprovedAndRejectedApplications() {
        assertIneligibleApplication(application(40L, 50L, 60L, "ORDER-10", false, false));
        assertIneligibleApplication(application(40L, 50L, 60L, "ORDER-10", true, true));

        verify(payoutTransactionRepository, org.mockito.Mockito.never())
                .saveAndFlush(org.mockito.ArgumentMatchers.any(PayoutTransaction.class));
        verify(paymentTransactionRepository, org.mockito.Mockito.never())
                .saveAndFlush(org.mockito.ArgumentMatchers.any(PaymentTransaction.class));
    }

    @Test
    void claimInitialRejectsApplicationWithMismatchedPaymentIdentity() {
        assertIneligibleApplication(application(40L, 51L, 61L, "ORDER-OTHER", true, false));
        assertIneligibleApplication(application(41L, 50L, 60L, "ORDER-10", true, false));

        verify(payoutTransactionRepository, org.mockito.Mockito.never())
                .saveAndFlush(org.mockito.ArgumentMatchers.any(PayoutTransaction.class));
        verify(paymentTransactionRepository, org.mockito.Mockito.never())
                .saveAndFlush(org.mockito.ArgumentMatchers.any(PaymentTransaction.class));
    }

    private void assertIneligibleApplication(PartyApplication application) {
        PaymentTransaction payment = payablePayment();
        givenPayment(payment);
        given(partyApplicationRepository.findById(40L)).willReturn(Optional.of(application));

        org.junit.jupiter.api.Assertions.assertThrows(
                PayoutClaimService.PayoutEligibilityException.class,
                () -> claimService.claimInitial(10L, true));
        verify(sellerRecoveryService, org.mockito.Mockito.never()).reserveOffset(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private static PaymentTransaction payablePayment() {
        return PaymentTransaction.builder()
                .id(10L)
                .applicationId(40L)
                .partyId(50L)
                .buyerUserId(60L)
                .orderId("ORDER-10")
                .sellerUserId(20L)
                .netAmount(15000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build();
    }

    private void givenPayment(PaymentTransaction payment) {
        lenient().when(paymentTransactionRepository.findById(payment.getId()))
                .thenReturn(Optional.of(payment));
        lenient().when(paymentTransactionRepository.findByIdForUpdate(payment.getId()))
                .thenReturn(Optional.of(payment));
    }

    private static PartyApplication application(
            Long id,
            Long partyId,
            Long applicantId,
            String orderId,
            boolean approved,
            boolean rejected) {
        return PartyApplication.builder()
                .id(id)
                .partyId(partyId)
                .applicantId(applicantId)
                .orderId(orderId)
                .isApproved(approved)
                .isRejected(rejected)
                .build();
    }

    @Test
    void legacyRequestedPayoutWithoutSnapshotIsQuarantinedWithoutProviderAction() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(30L)
                .partyId(50L)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(31L)
                .paymentTransactionId(30L)
                .status(SettlementStatus.REQUESTED)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();
        given(payoutTransactionRepository.findById(31L)).willReturn(Optional.of(payout));
        givenPayment(payment);
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
    void manualReconciliationRequestedPayoutCannotBeClaimedForAutomaticRetry() {
        PaymentTransaction payment = payablePayment();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(32L)
                .paymentTransactionId(10L)
                .status(SettlementStatus.REQUESTED)
                .failureCode("PAYOUT_MANUAL_RECONCILIATION_REQUIRED")
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(0)
                .build();
        given(payoutTransactionRepository.findById(32L)).willReturn(Optional.of(payout));
        givenPayment(payment);
        given(payoutTransactionRepository.findByIdForUpdate(32L)).willReturn(Optional.of(payout));

        PayoutClaimService.ClaimedPayout claimed = claimService.claimRetry(32L, true);

        assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.NONE);
        assertThat(payout.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(payout.getNextRetryAt()).isNull();
        assertThat(payout.getRequestedAt()).isNull();
        verify(sellerRecoveryService, never()).reserveOffset(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
        verify(payoutTransactionRepository, never()).saveAndFlush(payout);
    }

    @Test
    void manualReconciliationRequestedPayoutWithProviderRefCannotBeClaimedForPolling() {
        PaymentTransaction payment = payablePayment();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(33L)
                .paymentTransactionId(10L)
                .status(SettlementStatus.REQUESTED)
                .failureCode("PAYOUT_MANUAL_RECONCILIATION_REQUIRED")
                .claimProtocol("SNAPSHOT_V1")
                .providerRef("provider-ref-33")
                .retryCount(0)
                .build();
        given(payoutTransactionRepository.findById(33L)).willReturn(Optional.of(payout));
        givenPayment(payment);
        given(payoutTransactionRepository.findByIdForUpdate(33L)).willReturn(Optional.of(payout));

        PayoutClaimService.ClaimedPayout claimed = claimService.claimRetry(33L, true);

        assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.NONE);
        assertThat(payout.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(payout.getLastRetryAt()).isNull();
        assertThat(payout.getNextRetryAt()).isNull();
        verify(payoutTransactionRepository, never()).saveAndFlush(payout);
        verify(sellerRecoveryService, never()).reserveOffset(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void claimInitialOnFailedPartyPersistsManualHoldWithoutReservingOrDispatching() {
        PaymentTransaction payment = payablePayment();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(34L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .requestedAmount(15000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();
        givenPayment(payment);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(10L))
                .willReturn(Optional.of(payout));
        given(partyApplicationRepository.findById(40L))
                .willReturn(Optional.of(application(40L, 50L, 60L, "ORDER-10", true, false)));
        given(partyRepository.findByIdForUpdate(50L)).willReturn(Optional.of(
                Party.builder().id(50L).status(Party.PartyStatus.FAILED).build()));

        PayoutClaimService.ClaimedPayout claimed = claimService.claimInitial(10L, true);

        assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.NONE);
        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(payout.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(payout.getNextRetryAt()).isNull();
        assertThat(payment.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        verify(sellerRecoveryService, never()).reserveOffset(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                paymentTransactionRepository, accountDeletionFinancialService, partyRepository,
                payoutTransactionRepository);
        order.verify(paymentTransactionRepository).findById(10L);
        order.verify(accountDeletionFinancialService).lockSellerForPayoutClaim(20L);
        order.verify(partyRepository).findByIdForUpdate(50L);
        order.verify(paymentTransactionRepository).findByIdForUpdate(10L);
        order.verify(payoutTransactionRepository)
                .findTopByPaymentTransactionIdForUpdateOrderByIdDesc(10L);
    }

    @Test
    void claimRetryOnFailedPartyTurnsDuePayoutIntoManualHold() {
        PaymentTransaction payment = payablePayment();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(35L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .requestedAmount(15000)
                .status(SettlementStatus.REQUESTED)
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(0)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();
        given(payoutTransactionRepository.findById(35L)).willReturn(Optional.of(payout));
        givenPayment(payment);
        given(payoutTransactionRepository.findByIdForUpdate(35L)).willReturn(Optional.of(payout));
        given(partyRepository.findByIdForUpdate(50L)).willReturn(Optional.of(
                Party.builder().id(50L).status(Party.PartyStatus.FAILED).build()));

        PayoutClaimService.ClaimedPayout claimed = claimService.claimRetry(35L, true);

        assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.NONE);
        assertThat(payout.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(payout.getNextRetryAt()).isNull();
        verify(payoutTransactionRepository).saveAndFlush(payout);
        verify(sellerRecoveryService, never()).reserveOffset(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                payoutTransactionRepository, paymentTransactionRepository,
                accountDeletionFinancialService, partyRepository);
        order.verify(payoutTransactionRepository).findById(35L);
        order.verify(paymentTransactionRepository).findById(10L);
        order.verify(accountDeletionFinancialService).lockSellerForPayoutClaim(20L);
        order.verify(partyRepository).findByIdForUpdate(50L);
        order.verify(paymentTransactionRepository).findByIdForUpdate(10L);
        order.verify(payoutTransactionRepository).findByIdForUpdate(35L);
    }

    @Test
    void claimRetryRejectsPayoutWhoseLockedPaymentLinkChangedBeforeMutation() {
        PaymentTransaction payment = payablePayment();
        PayoutTransaction candidate = PayoutTransaction.builder()
                .id(41L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .requestedAmount(15000)
                .status(SettlementStatus.REQUESTED)
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(0)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();
        PayoutTransaction lockedPayout = PayoutTransaction.builder()
                .id(41L)
                .paymentTransactionId(99L)
                .sellerId(20L)
                .requestedAmount(15000)
                .status(SettlementStatus.REQUESTED)
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(0)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();
        given(payoutTransactionRepository.findById(41L)).willReturn(Optional.of(candidate));
        givenPayment(payment);
        given(payoutTransactionRepository.findByIdForUpdate(41L)).willReturn(Optional.of(lockedPayout));

        org.junit.jupiter.api.Assertions.assertThrows(
                PayoutClaimService.PayoutEligibilityException.class,
                () -> claimService.claimRetry(41L, true));

        assertThat(lockedPayout.getNextRetryAt()).isBefore(Instant.now());
        verify(payoutTransactionRepository, never()).saveAndFlush(lockedPayout);
        verify(paymentTransactionRepository, never()).saveAndFlush(payment);
        verify(sellerRecoveryService, never()).reserveOffset(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
        verify(accountDeletionFinancialService, never()).clearFallbackIfNoUnboundPayouts(20L);
    }

    @Test
    void claimRetryWithMissingPartyTurnsDuePayoutIntoManualHold() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(10L)
                .applicationId(40L)
                .partyId(null)
                .buyerUserId(60L)
                .orderId("ORDER-10")
                .sellerUserId(20L)
                .netAmount(15000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(37L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .requestedAmount(15000)
                .status(SettlementStatus.REQUESTED)
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(0)
                .recoveryOffsetAmount(0)
                .recoveryOffsetReservedAt(Instant.now())
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();
        given(payoutTransactionRepository.findById(37L)).willReturn(Optional.of(payout));
        givenPayment(payment);
        given(payoutTransactionRepository.findByIdForUpdate(37L)).willReturn(Optional.of(payout));

        PayoutClaimService.ClaimedPayout claimed = claimService.claimRetry(37L, true);

        assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.NONE);
        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(payout.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(payout.getNextRetryAt()).isNull();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        verify(payoutTransactionRepository).saveAndFlush(payout);
        verify(partyRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(sellerRecoveryService, never()).reserveOffset(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void providerPayloadCannotBeBoundAfterExpirationManualHold() {
        PaymentTransaction payment = payablePayment();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(36L)
                .paymentTransactionId(10L)
                .status(SettlementStatus.REQUESTED)
                .failureCode("PAYOUT_MANUAL_RECONCILIATION_REQUIRED")
                .build();
        givenPayment(payment);
        given(payoutTransactionRepository.findByIdForUpdate(36L)).willReturn(Optional.of(payout));

        org.junit.jupiter.api.Assertions.assertThrows(
                PayoutClaimService.PayoutEligibilityException.class,
                () -> claimService.bindProviderPayload(10L, 36L, "SIM", null));

        verify(payoutTransactionRepository, never()).saveAndFlush(payout);
    }

    @Test
    void providerPayloadCannotBeBoundWhenExpiryWinsDispatchReservation() {
        PaymentTransaction payment = payablePayment();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(38L)
                .paymentTransactionId(10L)
                .status(SettlementStatus.REQUESTED)
                .build();
        givenPayment(payment);
        given(payoutTransactionRepository.findByIdForUpdate(38L)).willReturn(Optional.of(payout));
        given(partyRepository.findByIdForUpdate(50L)).willReturn(Optional.of(
                Party.builder().id(50L).status(Party.PartyStatus.FAILED).build()));

        org.junit.jupiter.api.Assertions.assertThrows(
                PayoutClaimService.PayoutEligibilityException.class,
                () -> claimService.bindProviderPayload(10L, 38L, "SIM", null));

        assertThat(payout.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(payout.getNextRetryAt()).isNull();
        verify(payoutTransactionRepository).saveAndFlush(payout);
        verify(paymentTransactionRepository).saveAndFlush(payment);
    }

    @Test
    void providerPayloadBindingRejectsPayoutWhosePaymentLinkDoesNotMatchBeforeMutation() {
        PaymentTransaction payment = payablePayment();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(42L)
                .paymentTransactionId(99L)
                .sellerId(20L)
                .status(SettlementStatus.REQUESTED)
                .build();
        givenPayment(payment);
        given(payoutTransactionRepository.findByIdForUpdate(42L)).willReturn(Optional.of(payout));

        org.junit.jupiter.api.Assertions.assertThrows(
                PayoutClaimService.PayoutEligibilityException.class,
                () -> claimService.bindProviderPayload(10L, 42L, "SIM", null));

        assertThat(payout.getProviderCode()).isNull();
        assertThat(payout.getProviderSellerId()).isNull();
        verify(payoutTransactionRepository, never()).saveAndFlush(payout);
        verify(accountDeletionFinancialService, never()).clearFallbackIfNoUnboundPayouts(20L);
    }

    @Test
    void providerPayloadIsDurablyBoundOnce() {
        PaymentTransaction payment = PaymentTransaction.builder().id(10L).partyId(50L).build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(11L)
                .paymentTransactionId(10L)
                .status(SettlementStatus.REQUESTED)
                .build();
        givenPayment(payment);
        given(payoutTransactionRepository.findByIdForUpdate(11L)).willReturn(Optional.of(payout));
        given(payoutTransactionRepository.saveAndFlush(payout)).willReturn(payout);

        PayoutTransaction bound = claimService.bindProviderPayload(
                10L, 11L, "TOSS", "seller-toss-20");

        assertThat(bound.getProviderCode()).isEqualTo("TOSS");
        assertThat(bound.getProviderSellerId()).isEqualTo("seller-toss-20");
        assertThat(bound.getLastRetryAt()).isNotNull();
        assertThat(bound.getNextRetryAt()).isAfter(Instant.now());
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
        assertRequiresNew(PayoutClaimService.class.getMethod(
                "claimProviderDispatchIntent", Long.class, Long.class));
    }

    @Test
    void dispatchIntentLocksSellerThenPartyAndPersistsBeforeAllowingProviderCall() {
        PaymentTransaction payment = payablePayment();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(39L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .requestedAmount(15000)
                .status(SettlementStatus.REQUESTED)
                .providerCode("SIM")
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(0)
                .build();
        PartyApplication paidApplication = application(40L, 50L, 60L, "ORDER-10", true, false);
        paidApplication.setIsPaid(true);
        givenPayment(payment);
        given(partyApplicationRepository.findByIdAndApplicantIdForUpdate(40L, 60L))
                .willReturn(Optional.of(paidApplication));
        given(payoutTransactionRepository.findByIdForUpdate(39L)).willReturn(Optional.of(payout));
        given(payoutTransactionRepository.saveAndFlush(payout)).willReturn(payout);

        PayoutClaimService.DispatchIntentClaim claim = claimService.claimProviderDispatchIntent(10L, 39L);

        assertThat(claim.dispatchAllowed()).isTrue();
        assertThat(claim.payout()).isSameAs(payout);
        assertThat(payout.getProviderDispatchIntentAt()).isNotNull();
        verify(payoutTransactionRepository).saveAndFlush(payout);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                paymentTransactionRepository, accountDeletionFinancialService, partyRepository,
                partyApplicationRepository, payoutTransactionRepository);
        order.verify(paymentTransactionRepository).findById(10L);
        order.verify(accountDeletionFinancialService).lockSellerForPayoutClaim(20L);
        order.verify(partyRepository).findByIdForUpdate(50L);
        order.verify(partyApplicationRepository).findByIdAndApplicantIdForUpdate(40L, 60L);
        order.verify(paymentTransactionRepository).findByIdForUpdate(10L);
        order.verify(payoutTransactionRepository).findByIdForUpdate(39L);
        order.verify(payoutTransactionRepository).saveAndFlush(payout);
        order.verify(accountDeletionFinancialService).clearFallbackIfNoUnboundPayouts(20L);
    }

    @Test
    void dispatchIntentAfterPartyExpiryPersistsManualHoldAndDeniesProviderCall() {
        PaymentTransaction payment = payablePayment();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(40L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .requestedAmount(15000)
                .status(SettlementStatus.REQUESTED)
                .providerCode("SIM")
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(0)
                .build();
        PartyApplication paidApplication = application(40L, 50L, 60L, "ORDER-10", true, false);
        paidApplication.setIsPaid(true);
        givenPayment(payment);
        given(partyApplicationRepository.findByIdAndApplicantIdForUpdate(40L, 60L))
                .willReturn(Optional.of(paidApplication));
        given(payoutTransactionRepository.findByIdForUpdate(40L)).willReturn(Optional.of(payout));
        given(partyRepository.findByIdForUpdate(50L)).willReturn(Optional.of(
                Party.builder().id(50L).status(Party.PartyStatus.FAILED).build()));
        given(payoutTransactionRepository.saveAndFlush(payout)).willReturn(payout);

        PayoutClaimService.DispatchIntentClaim claim = claimService.claimProviderDispatchIntent(10L, 40L);

        assertThat(claim.dispatchAllowed()).isFalse();
        assertThat(claim.payout().getFailureCode())
                .isEqualTo(PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE);
        assertThat(claim.payout().getProviderDispatchIntentAt()).isNull();
        assertThat(claim.payout().getNextRetryAt()).isNull();
        verify(payoutTransactionRepository).saveAndFlush(payout);
        verify(paymentTransactionRepository).saveAndFlush(payment);
        verify(accountDeletionFinancialService, never()).clearFallbackIfNoUnboundPayouts(20L);
    }

    private void assertRequiresNew(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

}
