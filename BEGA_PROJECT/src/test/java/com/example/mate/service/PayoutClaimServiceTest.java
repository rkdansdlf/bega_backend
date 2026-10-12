package com.example.mate.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.MateLinkReconciliationReasonCode;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
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
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PayoutClaimServiceTest {

    @Mock
    private PayoutTransactionRepository payoutTransactionRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PartyRepository partyRepository;
    @Mock
    private PartyApplicationRepository partyApplicationRepository;
    @Mock
    private MateLinkReconciliationCaseRecorder reconciliationCaseRecorder;
    @Mock
    private SellerRecoveryService sellerRecoveryService;
    @Mock
    private PaymentMetricsService metricsService;

    @InjectMocks
    private PayoutClaimService claimService;

    @Test
    void claimInitialDurablyPersistsRequestedStateBeforeReturningProviderAction() {
        PaymentTransaction payment = linkedPayment();
        payment.setNetAmount(15000);
        payment.setSettlementStatus(SettlementStatus.PENDING);
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(11L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();
        given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(20L)).willReturn(Optional.of(activeSeller()));
        given(partyRepository.findByIdForUpdate(40L)).willReturn(Optional.of(activeParty()));
        given(partyApplicationRepository.findByOrderIdForUpdate("order-10"))
                .willReturn(Optional.of(approvedApplication()));
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
    void claimInitialDoesNotReserveRecoveryOrRequestProviderForNonPaidPayment() {
        PaymentTransaction payment = linkedPayment();
        payment.setPaymentStatus(PaymentStatus.CANCELED);
        payment.setSettlementStatus(SettlementStatus.PENDING);
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(11L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();
        given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(20L)).willReturn(Optional.of(activeSeller()));
        given(partyRepository.findByIdForUpdate(40L)).willReturn(Optional.of(activeParty()));
        given(partyApplicationRepository.findByOrderIdForUpdate("order-10"))
                .willReturn(Optional.of(approvedApplication()));
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(10L))
                .willReturn(Optional.of(payout));

        PayoutClaimService.ClaimedPayout claimed = claimService.claimInitial(10L, true);

        assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.NONE);
        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.SKIPPED);
        assertThat(payout.getFailureCode()).isEqualTo("PAYMENT_NOT_PAYABLE");
        assertThat(payment.getSettlementStatus()).isEqualTo(SettlementStatus.PENDING);
        verify(payoutTransactionRepository).save(payout);
        verify(sellerRecoveryService, never()).reserveOffset(payout, 20L, 15000);
    }

    @Test
    void claimInitialRejectsUnapprovedApplicationBeforeCreatingPayoutOrReservingOffset() {
        PaymentTransaction payment = linkedPayment();
        payment.setNetAmount(15000);
        payment.setSettlementStatus(SettlementStatus.PENDING);
        PartyApplication application = approvedApplication();
        application.setIsApproved(false);
        given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(20L)).willReturn(Optional.of(activeSeller()));
        given(partyRepository.findByIdForUpdate(40L)).willReturn(Optional.of(activeParty()));
        given(partyApplicationRepository.findByOrderIdForUpdate("order-10"))
                .willReturn(Optional.of(application));
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                InvalidApplicationStatusException.class,
                () -> claimService.claimInitial(10L, true)))
                .hasMessageContaining("승인");

        verifyNoInteractions(payoutTransactionRepository, sellerRecoveryService);
    }

    @Test
    void claimInitialRejectsRejectedApplicationBeforeCreatingPayoutOrReservingOffset() {
        PaymentTransaction payment = linkedPayment();
        payment.setNetAmount(15000);
        payment.setSettlementStatus(SettlementStatus.PENDING);
        PartyApplication application = approvedApplication();
        application.setIsRejected(true);
        given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(20L)).willReturn(Optional.of(activeSeller()));
        given(partyRepository.findByIdForUpdate(40L)).willReturn(Optional.of(activeParty()));
        given(partyApplicationRepository.findByOrderIdForUpdate("order-10"))
                .willReturn(Optional.of(application));
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                InvalidApplicationStatusException.class,
                () -> claimService.claimInitial(10L, true)))
                .hasMessageContaining("승인");

        verifyNoInteractions(payoutTransactionRepository, sellerRecoveryService);
    }

    @Test
    void claimInitialRejectsMismatchedApplicationLinkBeforePayoutMutation() {
        PaymentTransaction payment = linkedPayment();
        payment.setNetAmount(15000);
        payment.setSettlementStatus(SettlementStatus.PENDING);
        PartyApplication application = approvedApplication();
        application.setId(31L);
        given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(20L)).willReturn(Optional.of(activeSeller()));
        given(partyRepository.findByIdForUpdate(40L)).willReturn(Optional.of(activeParty()));
        given(partyApplicationRepository.findByOrderIdForUpdate("order-10"))
                .willReturn(Optional.of(application));
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                InvalidApplicationStatusException.class,
                () -> claimService.claimInitial(10L, true)))
                .hasMessageContaining("연결");

        verify(reconciliationCaseRecorder).record(
                40L,
                31L,
                10L,
                MateLinkReconciliationReasonCode.PAYMENT_APPLICATION_LINK_MISMATCH);
        verifyNoInteractions(payoutTransactionRepository, sellerRecoveryService);
    }

    @Test
    void claimInitialRejectsMismatchedPaymentKeyBeforePayoutMutation() {
        PaymentTransaction payment = linkedPayment();
        PartyApplication application = approvedApplication();
        application.setPaymentKey("different-payment-key");
        given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(20L)).willReturn(Optional.of(activeSeller()));
        given(partyRepository.findByIdForUpdate(40L)).willReturn(Optional.of(activeParty()));
        given(partyApplicationRepository.findByOrderIdForUpdate("order-10"))
                .willReturn(Optional.of(application));
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                InvalidApplicationStatusException.class,
                () -> claimService.claimInitial(10L, true)))
                .hasMessageContaining("연결");

        verify(reconciliationCaseRecorder).record(
                40L,
                30L,
                10L,
                MateLinkReconciliationReasonCode.PAYMENT_APPLICATION_LINK_MISMATCH);
        verifyNoInteractions(payoutTransactionRepository, sellerRecoveryService);
    }

    @Test
    void claimInitialRejectsMismatchedGrossAmountBeforePayoutMutation() {
        PaymentTransaction payment = linkedPayment();
        PartyApplication application = approvedApplication();
        application.setDepositAmount(14000);
        given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(20L)).willReturn(Optional.of(activeSeller()));
        given(partyRepository.findByIdForUpdate(40L)).willReturn(Optional.of(activeParty()));
        given(partyApplicationRepository.findByOrderIdForUpdate("order-10"))
                .willReturn(Optional.of(application));
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                InvalidApplicationStatusException.class,
                () -> claimService.claimInitial(10L, true)))
                .hasMessageContaining("연결");

        verify(reconciliationCaseRecorder).record(
                40L,
                30L,
                10L,
                MateLinkReconciliationReasonCode.PAYMENT_APPLICATION_LINK_MISMATCH);
        verifyNoInteractions(payoutTransactionRepository, sellerRecoveryService);
    }

    @Test
    void reenableClaimsOnlyPaidApprovedUnexpiredDisabledSkipWithoutProviderEvidence() {
        PaymentTransaction payment = linkedPayment();
        payment.setNetAmount(12000);
        payment.setSettlementStatus(SettlementStatus.SKIPPED);
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(11L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .requestedAmount(12000)
                .status(SettlementStatus.SKIPPED)
                .failureCode("PAYMENT_PAYOUT_DISABLED")
                .retryCount(0)
                .recoveryOffsetAmount(0)
                .build();
        given(payoutTransactionRepository.findById(11L)).willReturn(Optional.of(payout));
        given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(20L)).willReturn(Optional.of(activeSeller()));
        given(partyRepository.findByIdForUpdate(40L)).willReturn(Optional.of(activeParty()));
        given(partyApplicationRepository.findByOrderIdForUpdate("order-10"))
                .willReturn(Optional.of(approvedApplication()));
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(11L)).willReturn(Optional.of(payout));
        given(sellerRecoveryService.reserveOffset(payout, 20L, 12000))
                .willReturn(new SellerRecoveryService.RecoveryOffsetResult(0));
        given(payoutTransactionRepository.saveAndFlush(payout)).willReturn(payout);
        given(paymentTransactionRepository.saveAndFlush(payment)).willReturn(payment);

        PayoutClaimService.ClaimedPayout claimed = claimService.resumeDisabledPayout(11L, true);

        assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.CALL_PROVIDER);
        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(payout.getFailureCode()).isNull();
        assertThat(payout.getClaimProtocol()).isEqualTo("SNAPSHOT_V1");
        assertThat(payout.getProviderDispatchIntentAt()).isNull();
        assertThat(payment.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        verify(sellerRecoveryService).reserveOffset(payout, 20L, 12000);
    }

    @Test
    void reenableMovesExpiredPartyClaimToManualHoldWithoutReservingOrDispatching() {
        PaymentTransaction payment = linkedPayment();
        payment.setNetAmount(12000);
        payment.setSettlementStatus(SettlementStatus.SKIPPED);
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(11L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .requestedAmount(12000)
                .status(SettlementStatus.SKIPPED)
                .failureCode("PAYMENT_PAYOUT_DISABLED")
                .retryCount(0)
                .recoveryOffsetAmount(0)
                .build();
        Party expiredParty = activeParty();
        expiredParty.setGameDate(LocalDate.now().minusDays(1));
        given(payoutTransactionRepository.findById(11L)).willReturn(Optional.of(payout));
        given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(20L)).willReturn(Optional.of(activeSeller()));
        given(partyRepository.findByIdForUpdate(40L)).willReturn(Optional.of(expiredParty));
        given(partyApplicationRepository.findByOrderIdForUpdate("order-10"))
                .willReturn(Optional.of(approvedApplication()));
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(11L)).willReturn(Optional.of(payout));
        given(payoutTransactionRepository.saveAndFlush(payout)).willReturn(payout);
        given(paymentTransactionRepository.saveAndFlush(payment)).willReturn(payment);

        PayoutClaimService.ClaimedPayout claimed = claimService.resumeDisabledPayout(11L, true);

        assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.NONE);
        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(payout.getFailureCode()).isEqualTo(PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE);
        assertThat(payout.getNextRetryAt()).isNull();
        assertThat(payment.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        verify(sellerRecoveryService, never()).reserveOffset(payout, 20L, 12000);
        verify(payoutTransactionRepository).saveAndFlush(payout);
    }

    @Test
    void reenableDoesNotClaimSkippedRowsWithProviderEvidence() {
        PaymentTransaction payment = linkedPayment();
        payment.setNetAmount(12000);
        payment.setSettlementStatus(SettlementStatus.SKIPPED);
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(11L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .requestedAmount(12000)
                .status(SettlementStatus.SKIPPED)
                .failureCode("PAYMENT_PAYOUT_DISABLED")
                .providerRef("existing-provider-reference")
                .retryCount(0)
                .recoveryOffsetAmount(0)
                .build();
        given(payoutTransactionRepository.findById(11L)).willReturn(Optional.of(payout));
        given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(20L)).willReturn(Optional.of(activeSeller()));
        given(partyRepository.findByIdForUpdate(40L)).willReturn(Optional.of(activeParty()));
        given(partyApplicationRepository.findByOrderIdForUpdate("order-10"))
                .willReturn(Optional.of(approvedApplication()));
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(11L)).willReturn(Optional.of(payout));
        given(payoutTransactionRepository.saveAndFlush(payout)).willReturn(payout);
        given(paymentTransactionRepository.saveAndFlush(payment)).willReturn(payment);

        PayoutClaimService.ClaimedPayout claimed = claimService.resumeDisabledPayout(11L, true);

        assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.NONE);
        assertThat(payout.getFailureCode()).isEqualTo(PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE);
        verify(sellerRecoveryService, never()).reserveOffset(payout, 20L, 12000);
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
    void claimRetryWithMissingPartyTurnsDuePayoutIntoManualHoldBeforeReservingOffset() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(40L)
                .sellerUserId(20L)
                .partyId(null)
                .netAmount(12000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(41L)
                .paymentTransactionId(40L)
                .sellerId(20L)
                .status(SettlementStatus.REQUESTED)
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(0)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();
        given(payoutTransactionRepository.findById(41L)).willReturn(Optional.of(payout));
        given(paymentTransactionRepository.findByIdForUpdate(40L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(41L)).willReturn(Optional.of(payout));

        PayoutClaimService.ClaimedPayout claimed = claimService.claimRetry(41L, true);

        assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.NONE);
        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(payout.getFailureCode())
                .isEqualTo(PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE);
        assertThat(payout.getNextRetryAt()).isNull();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        verify(partyRepository, never()).findById(null);
        verify(sellerRecoveryService, never()).reserveOffset(payout, 20L, 12000);
        verify(reconciliationCaseRecorder).record(
                null,
                null,
                40L,
                MateLinkReconciliationReasonCode.PARTY_LINK_MISSING);
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
        assertRequiresNew(PayoutClaimService.class.getMethod(
                "claimProviderDispatchIntent", Long.class, Long.class));
        assertRequiresNew(PayoutClaimService.class.getMethod(
                "claimResumedPayoutDispatchIntent", Long.class, Long.class));
        assertRequiresNew(PayoutClaimService.class.getMethod(
                "resumeDisabledPayout", Long.class, boolean.class));
    }

    @Test
    void dispatchIntentIsPersistedOnlyAfterSellerPartyApplicationPaymentAndPayoutAreValidated() {
        UserEntity seller = activeSeller();
        Party party = activeParty();
        PartyApplication application = approvedApplication();
        PaymentTransaction payment = linkedPayment();
        PayoutTransaction payout = requestedPayout();
        given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(20L)).willReturn(Optional.of(seller));
        given(partyRepository.findByIdForUpdate(40L)).willReturn(Optional.of(party));
        given(partyApplicationRepository.findByOrderIdForUpdate("order-10"))
                .willReturn(Optional.of(application));
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(11L)).willReturn(Optional.of(payout));
        given(payoutTransactionRepository.saveAndFlush(payout)).willReturn(payout);

        boolean claimed = claimService.claimProviderDispatchIntent(10L, 11L);

        assertThat(claimed).isTrue();
        assertThat(payout.getProviderDispatchIntentAt()).isNotNull();
        verify(payoutTransactionRepository).saveAndFlush(payout);
    }

    @Test
    void expiredPartyLeavesPayoutOnManualHoldAndDeniesDispatch() {
        UserEntity seller = activeSeller();
        Party party = activeParty();
        party.setStatus(Party.PartyStatus.FAILED);
        PaymentTransaction payment = linkedPayment();
        PayoutTransaction payout = requestedPayout();
        given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(20L)).willReturn(Optional.of(seller));
        given(partyRepository.findByIdForUpdate(40L)).willReturn(Optional.of(party));
        given(partyApplicationRepository.findByOrderIdForUpdate("order-10"))
                .willReturn(Optional.of(approvedApplication()));
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(11L)).willReturn(Optional.of(payout));
        given(payoutTransactionRepository.saveAndFlush(payout)).willReturn(payout);

        boolean claimed = claimService.claimProviderDispatchIntent(10L, 11L);

        assertThat(claimed).isFalse();
        assertThat(payout.getProviderDispatchIntentAt()).isNull();
        assertThat(payout.getFailureCode())
                .isEqualTo(PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE);
        assertThat(payout.getNextRetryAt()).isNull();
        verify(metricsService).recordPayout("manual_reconciliation");
    }

    @Test
    void resumedPayoutRechecksGameDateAtDispatchIntentBoundary() {
        UserEntity seller = activeSeller();
        Party party = activeParty();
        party.setGameDate(LocalDate.now().minusDays(1));
        PaymentTransaction payment = linkedPayment();
        PayoutTransaction payout = requestedPayout();
        given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(20L)).willReturn(Optional.of(seller));
        given(partyRepository.findByIdForUpdate(40L)).willReturn(Optional.of(party));
        given(partyApplicationRepository.findByOrderIdForUpdate("order-10"))
                .willReturn(Optional.of(approvedApplication()));
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(11L)).willReturn(Optional.of(payout));
        given(payoutTransactionRepository.saveAndFlush(payout)).willReturn(payout);
        given(paymentTransactionRepository.saveAndFlush(payment)).willReturn(payment);

        boolean claimed = claimService.claimResumedPayoutDispatchIntent(10L, 11L);

        assertThat(claimed).isFalse();
        assertThat(payout.getProviderDispatchIntentAt()).isNull();
        assertThat(payout.getFailureCode())
                .isEqualTo(PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE);
        assertThat(payout.getNextRetryAt()).isNull();
    }

    @Test
    void missingApplicationLinkIsRecordedBeforeDispatchIsHeld() {
        UserEntity seller = activeSeller();
        Party party = activeParty();
        PaymentTransaction payment = linkedPayment();
        PayoutTransaction payout = requestedPayout();
        PartyApplication application = approvedApplication();
        application.setId(31L);
        given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(20L)).willReturn(Optional.of(seller));
        given(partyRepository.findByIdForUpdate(40L)).willReturn(Optional.of(party));
        given(partyApplicationRepository.findByOrderIdForUpdate("order-10"))
                .willReturn(Optional.of(application));
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(11L)).willReturn(Optional.of(payout));
        given(payoutTransactionRepository.saveAndFlush(payout)).willReturn(payout);

        boolean claimed = claimService.claimProviderDispatchIntent(10L, 11L);

        assertThat(claimed).isFalse();
        assertThat(payout.getFailureCode())
                .isEqualTo(PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE);
        verify(reconciliationCaseRecorder).record(
                40L,
                31L,
                10L,
                MateLinkReconciliationReasonCode.PAYMENT_APPLICATION_LINK_MISMATCH);
    }

    @Test
    void manualHoldCannotBeClaimedAgainByRetryWorker() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(10L)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(11L)
                .paymentTransactionId(10L)
                .status(SettlementStatus.REQUESTED)
                .failureCode(PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE)
                .nextRetryAt(null)
                .build();
        given(payoutTransactionRepository.findById(11L)).willReturn(Optional.of(payout));
        given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(11L)).willReturn(Optional.of(payout));

        PayoutClaimService.ClaimedPayout claimed = claimService.claimRetry(11L, true);

        assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.NONE);
        verify(payoutTransactionRepository, never()).saveAndFlush(payout);
    }

    @Test
    void dispatchRejectsAndRecordsEveryDefinedReconciliationReasonCode() {
        for (MateLinkReconciliationReasonCode expected : MateLinkReconciliationReasonCode.values()) {
            reset(
                    payoutTransactionRepository,
                    paymentTransactionRepository,
                    userRepository,
                    partyRepository,
                    partyApplicationRepository,
                    reconciliationCaseRecorder,
                    sellerRecoveryService,
                    metricsService);

            PaymentTransaction candidate = linkedPayment();
            PaymentTransaction payment = linkedPayment();
            PartyApplication application = approvedApplication();
            Party party = activeParty();
            PayoutTransaction payout = requestedPayout();

            switch (expected) {
                case PAYMENT_TRANSACTION_MISSING -> {
                    given(paymentTransactionRepository.findById(10L)).willReturn(Optional.empty());
                }
                case PAYMENT_LINK_INCOMPLETE -> payment.setBuyerUserId(null);
                case APPLICATION_LINK_MISSING -> { }
                case APPLICATION_LINK_INCOMPLETE -> application.setApplicantId(null);
                case PARTY_LINK_MISSING -> { }
                case PARTY_LINK_MISMATCH -> application.setPartyId(41L);
                case PAYMENT_APPLICATION_LINK_MISMATCH -> candidate.setApplicationId(31L);
                case PAYMENT_PARTY_LINK_MISMATCH -> candidate.setPartyId(41L);
                case PAYMENT_BUYER_LINK_MISMATCH -> application.setApplicantId(51L);
                case PAYMENT_ORDER_LINK_MISMATCH -> candidate.setOrderId("different-order");
                case PAYMENT_SELLER_LINK_MISMATCH -> candidate.setSellerUserId(21L);
                case PAYOUT_TRANSACTION_MISSING -> { }
                case PAYOUT_PAYMENT_LINK_MISMATCH -> payout.setPaymentTransactionId(12L);
                case PAYOUT_SELLER_LINK_MISMATCH -> payout.setSellerId(21L);
            }

            if (expected != MateLinkReconciliationReasonCode.PAYMENT_TRANSACTION_MISSING) {
                given(paymentTransactionRepository.findById(10L)).willReturn(Optional.of(candidate));
                given(userRepository.findByIdForWrite(candidate.getSellerUserId()))
                        .willReturn(Optional.of(activeSeller()));
                given(partyRepository.findByIdForUpdate(candidate.getPartyId()))
                        .willReturn(expected == MateLinkReconciliationReasonCode.PARTY_LINK_MISSING
                                ? Optional.empty()
                                : Optional.of(party));
                given(partyApplicationRepository.findByOrderIdForUpdate(candidate.getOrderId()))
                        .willReturn(expected == MateLinkReconciliationReasonCode.APPLICATION_LINK_MISSING
                                ? Optional.empty()
                                : Optional.of(application));
                given(paymentTransactionRepository.findByIdForUpdate(10L)).willReturn(Optional.of(payment));
                given(payoutTransactionRepository.findByIdForUpdate(11L))
                        .willReturn(expected == MateLinkReconciliationReasonCode.PAYOUT_TRANSACTION_MISSING
                                ? Optional.empty()
                                : Optional.of(payout));
            }

            assertThat(claimService.claimProviderDispatchIntent(10L, 11L))
                    .as("dispatch intent must stay closed for %s", expected)
                    .isFalse();
            assertThat(payout.getProviderDispatchIntentAt())
                    .as("no provider intent may be persisted for %s", expected)
                    .isNull();
            verify(reconciliationCaseRecorder).record(
                    nullable(Long.class), nullable(Long.class), nullable(Long.class), eq(expected));
        }
    }

    private UserEntity activeSeller() {
        return UserEntity.builder().id(20L).enabled(true).pendingDeletion(false).build();
    }

    private Party activeParty() {
        return Party.builder().id(40L).hostId(20L).status(Party.PartyStatus.SELLING)
                .gameDate(LocalDate.now().plusDays(1)).build();
    }

    private PartyApplication approvedApplication() {
        return PartyApplication.builder().id(30L).partyId(40L).applicantId(50L)
                .orderId("order-10").paymentKey("payment-key-10").depositAmount(15000)
                .isApproved(true).isPaid(true).isRejected(false).build();
    }

    private PaymentTransaction linkedPayment() {
        return PaymentTransaction.builder().id(10L).sellerUserId(20L).partyId(40L)
                .applicationId(30L).buyerUserId(50L).orderId("order-10")
                .paymentKey("payment-key-10").grossAmount(15000)
                .paymentStatus(PaymentStatus.PAID).settlementStatus(SettlementStatus.REQUESTED).build();
    }

    private PayoutTransaction requestedPayout() {
        return PayoutTransaction.builder().id(11L).paymentTransactionId(10L).sellerId(20L)
                .status(SettlementStatus.REQUESTED).nextRetryAt(Instant.now()).build();
    }

    private void assertRequiresNew(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
