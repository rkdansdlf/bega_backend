package com.example.mate.service;

import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.entity.MateLinkReconciliationReasonCode;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PayoutTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyLifecycleMutationServiceTest {

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private PartyApplicationRepository applicationRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PayoutTransactionRepository payoutTransactionRepository;

    @Mock
    private MateLinkReconciliationCaseRecorder reconciliationCaseRecorder;

    @InjectMocks
    private PartyLifecycleMutationService service;

    @Test
    void expirePendingParty_rejectsOnlyPendingApplicationsWithPartyExpiry() {
        LocalDate today = LocalDate.of(2026, 9, 30);
        Party party = Party.builder()
                .id(10L)
                .status(Party.PartyStatus.PENDING)
                .gameDate(today.minusDays(1))
                .build();
        PartyApplication unpaidApplication = PartyApplication.builder()
                .id(20L)
                .partyId(10L)
                .isApproved(false)
                .isRejected(false)
                .isPaid(false)
                .build();
        PartyApplication paidApplication = PartyApplication.builder()
                .id(21L)
                .partyId(10L)
                .isApproved(false)
                .isRejected(false)
                .isPaid(true)
                .responseDeadline(Instant.now().plusSeconds(48 * 60 * 60))
                .build();
        unpaidApplication.setResponseDeadline(Instant.now().plusSeconds(48 * 60 * 60));
        when(partyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(party));
        when(applicationRepository.findByPartyIdAndIsApprovedFalseAndIsRejectedFalse(10L))
                .thenReturn(List.of(unpaidApplication, paidApplication));
        when(partyRepository.save(party)).thenReturn(party);

        Party expired = service.expirePendingParty(10L, today);

        assertThat(expired.getStatus()).isEqualTo(Party.PartyStatus.FAILED);
        assertThat(unpaidApplication.getIsRejected()).isTrue();
        assertThat(paidApplication.getIsRejected()).isTrue();
        assertThat(unpaidApplication.getRejectedAt()).isNotNull();
        assertThat(paidApplication.getRejectedAt()).isNotNull();
        assertThat(paidApplication.getResponseDeadline()).isAfter(Instant.now());
        assertThat(paidApplication.getRejectedAt()).isEqualTo(unpaidApplication.getRejectedAt());
        verify(applicationRepository).save(unpaidApplication);
        verify(applicationRepository).save(paidApplication);
    }

    @Test
    void expirePendingParty_holdsApprovedPaidPayoutForManualReconciliation() {
        LocalDate today = LocalDate.of(2026, 9, 30);
        Party party = Party.builder()
                .id(12L)
                .hostId(70L)
                .status(Party.PartyStatus.PENDING)
                .gameDate(today.minusDays(1))
                .build();
        PartyApplication application = PartyApplication.builder()
                .id(22L)
                .partyId(12L)
                .applicantId(32L)
                .orderId("ORDER-12")
                .isPaid(true)
                .isApproved(true)
                .isRejected(false)
                .build();
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(42L)
                .partyId(12L)
                .applicationId(22L)
                .buyerUserId(32L)
                .sellerUserId(70L)
                .orderId("ORDER-12")
                .netAmount(15000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build();

        when(partyRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(party));
        when(applicationRepository.findApprovedPaidApplicationsByPartyIdForUpdate(12L))
                .thenReturn(List.of(application));
        when(paymentTransactionRepository.findByOrderIdForUpdate("ORDER-12"))
                .thenReturn(Optional.of(payment));
        when(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(42L))
                .thenReturn(Optional.empty());
        when(payoutTransactionRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(partyRepository.save(party)).thenReturn(party);

        Party expired = service.expirePendingParty(12L, today);

        assertThat(expired.getStatus()).isEqualTo(Party.PartyStatus.FAILED);
        assertThat(application.getIsApproved()).isTrue();
        assertThat(application.getIsRejected()).isFalse();
        assertThat(payment.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        ArgumentCaptor<com.example.mate.entity.PayoutTransaction> payoutCaptor =
                ArgumentCaptor.forClass(com.example.mate.entity.PayoutTransaction.class);
        verify(payoutTransactionRepository).saveAndFlush(payoutCaptor.capture());
        assertThat(payoutCaptor.getValue().getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(payoutCaptor.getValue().getFailureCode())
                .isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(payoutCaptor.getValue().getNextRetryAt()).isNull();
        assertThat(payoutCaptor.getValue().getRequestedAmount()).isEqualTo(15000);
    }

    @Test
    void expirePendingParty_recordsMissingPaymentLinkWithoutChangingApprovedPaidFacts() {
        LocalDate today = LocalDate.of(2026, 9, 30);
        Party party = Party.builder()
                .id(120L)
                .status(Party.PartyStatus.PENDING)
                .gameDate(today.minusDays(1))
                .build();
        PartyApplication application = PartyApplication.builder()
                .id(220L)
                .partyId(120L)
                .applicantId(320L)
                .orderId("ORDER-MISSING-PAYMENT")
                .isPaid(true)
                .isApproved(true)
                .isRejected(false)
                .build();
        when(partyRepository.findByIdForUpdate(120L)).thenReturn(Optional.of(party));
        when(applicationRepository.findApprovedPaidApplicationsByPartyIdForUpdate(120L))
                .thenReturn(List.of(application));
        when(paymentTransactionRepository.findByOrderIdForUpdate("ORDER-MISSING-PAYMENT"))
                .thenReturn(Optional.empty());
        when(partyRepository.save(party)).thenReturn(party);

        Party expired = service.expirePendingParty(120L, today);

        assertThat(expired.getStatus()).isEqualTo(Party.PartyStatus.FAILED);
        assertThat(application.getIsApproved()).isTrue();
        assertThat(application.getIsPaid()).isTrue();
        assertThat(application.getIsRejected()).isFalse();
        verify(reconciliationCaseRecorder).record(
                120L,
                220L,
                null,
                MateLinkReconciliationReasonCode.PAYMENT_TRANSACTION_MISSING);
    }

    @Test
    void expirePendingParty_preservesTerminalPayoutFacts() {
        LocalDate today = LocalDate.of(2026, 9, 30);
        Party party = Party.builder()
                .id(13L)
                .status(Party.PartyStatus.PENDING)
                .gameDate(today.minusDays(1))
                .build();
        PartyApplication application = PartyApplication.builder()
                .id(23L)
                .partyId(13L)
                .applicantId(33L)
                .orderId("ORDER-13")
                .isPaid(true)
                .isApproved(true)
                .isRejected(false)
                .build();
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(43L)
                .partyId(13L)
                .applicationId(23L)
                .buyerUserId(33L)
                .sellerUserId(71L)
                .orderId("ORDER-13")
                .netAmount(9000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.COMPLETED)
                .build();
        com.example.mate.entity.PayoutTransaction payout =
                com.example.mate.entity.PayoutTransaction.builder()
                        .id(53L)
                        .paymentTransactionId(43L)
                        .sellerId(71L)
                        .requestedAmount(9000)
                        .status(SettlementStatus.COMPLETED)
                        .providerRef("settled-13")
                        .failureCode("PAYOUT_COMPLETION_VERIFIED")
                        .failReason("provider confirmed completion")
                        .build();

        when(partyRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(party));
        when(applicationRepository.findApprovedPaidApplicationsByPartyIdForUpdate(13L))
                .thenReturn(List.of(application));
        when(paymentTransactionRepository.findByOrderIdForUpdate("ORDER-13"))
                .thenReturn(Optional.of(payment));
        when(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(43L))
                .thenReturn(Optional.of(payout));
        when(partyRepository.save(party)).thenReturn(party);

        Party expired = service.expirePendingParty(13L, today);

        assertThat(expired.getStatus()).isEqualTo(Party.PartyStatus.FAILED);
        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(payout.getProviderRef()).isEqualTo("settled-13");
        assertThat(payout.getFailureCode()).isEqualTo("PAYOUT_COMPLETION_VERIFIED");
        assertThat(payout.getFailReason()).isEqualTo("provider confirmed completion");
        verify(payoutTransactionRepository, org.mockito.Mockito.never()).saveAndFlush(payout);
        verify(paymentTransactionRepository, org.mockito.Mockito.never()).save(payment);
    }

    @Test
    void rejectExpiredApplication_locksPartyBeforeApplicationAndClaimsState() {
        Instant now = Instant.now();
        Party party = Party.builder().id(10L).build();
        PartyApplication application = PartyApplication.builder()
                .id(20L)
                .partyId(10L)
                .applicantId(30L)
                .isApproved(false)
                .isRejected(false)
                .isPaid(true)
                .responseDeadline(now.minusSeconds(1))
                .build();

        when(partyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(party));
        when(applicationRepository.findByIdAndApplicantIdForUpdate(20L, 30L))
                .thenReturn(Optional.of(application));
        when(applicationRepository.save(application)).thenReturn(application);

        PartyLifecycleMutationService.ExpiredApplicationMutation result =
                service.rejectExpiredApplication(10L, 20L, 30L, now);

        assertThat(result).isNotNull();
        assertThat(result.application().getIsRejected()).isTrue();
        assertThat(result.application().getRejectedAt()).isEqualTo(now);
        InOrder order = inOrder(partyRepository, applicationRepository);
        order.verify(partyRepository).findByIdForUpdate(10L);
        order.verify(applicationRepository).findByIdAndApplicantIdForUpdate(20L, 30L);
    }

    @Test
    void markApplicationUnpaidAfterRefund_preservesPartyFirstLockOrder() {
        PartyApplication application = PartyApplication.builder()
                .id(20L)
                .partyId(10L)
                .applicantId(30L)
                .isRejected(true)
                .isPaid(true)
                .build();

        when(partyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(Party.builder().id(10L).build()));
        when(applicationRepository.findByIdAndApplicantIdForUpdate(20L, 30L))
                .thenReturn(Optional.of(application));

        service.markApplicationUnpaidAfterRefund(10L, 20L, 30L);

        assertThat(application.getIsPaid()).isFalse();
        verify(applicationRepository).save(application);
        InOrder order = inOrder(partyRepository, applicationRepository);
        order.verify(partyRepository).findByIdForUpdate(10L);
        order.verify(applicationRepository).findByIdAndApplicantIdForUpdate(20L, 30L);
    }

    @Test
    void claimRefundAttempt_marksTransactionRequestedBeforeExternalCall() {
        Instant now = Instant.parse("2026-07-15T12:00:00Z");
        Party party = Party.builder().id(10L).build();
        PartyApplication application = PartyApplication.builder()
                .id(20L)
                .partyId(10L)
                .applicantId(30L)
                .isApproved(false)
                .isRejected(true)
                .isPaid(true)
                .orderId("MATE-10-30")
                .build();
        PaymentTransaction transaction = PaymentTransaction.builder()
                .orderId("MATE-10-30")
                .paymentStatus(PaymentStatus.REFUND_FAILED)
                .updatedAt(now.minusSeconds(3600))
                .build();

        when(partyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(party));
        when(applicationRepository.findByIdAndApplicantIdForUpdate(20L, 30L))
                .thenReturn(Optional.of(application));
        when(paymentTransactionRepository.findByOrderIdForUpdate("MATE-10-30"))
                .thenReturn(Optional.of(transaction));

        PartyLifecycleMutationService.RefundAttemptClaim result =
                service.claimRefundAttempt(10L, 20L, 30L, now.minusSeconds(900));

        assertThat(result).isNotNull();
        assertThat(result.application()).isSameAs(application);
        assertThat(result.alreadyCanceled()).isFalse();
        assertThat(transaction.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_REQUESTED);
        InOrder order = inOrder(partyRepository, applicationRepository, paymentTransactionRepository);
        order.verify(partyRepository).findByIdForUpdate(10L);
        order.verify(applicationRepository).findByIdAndApplicantIdForUpdate(20L, 30L);
        order.verify(paymentTransactionRepository).findByOrderIdForUpdate("MATE-10-30");
        verify(paymentTransactionRepository).save(transaction);
    }

    @Test
    void claimRefundAttempt_skipsRecentInFlightClaim() {
        Instant now = Instant.parse("2026-07-15T12:00:00Z");
        PartyApplication application = PartyApplication.builder()
                .id(20L)
                .partyId(10L)
                .applicantId(30L)
                .isRejected(true)
                .isPaid(true)
                .orderId("MATE-10-30")
                .build();
        PaymentTransaction transaction = PaymentTransaction.builder()
                .orderId("MATE-10-30")
                .paymentStatus(PaymentStatus.REFUND_REQUESTED)
                .updatedAt(now.minusSeconds(60))
                .build();
        when(partyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(Party.builder().id(10L).build()));
        when(applicationRepository.findByIdAndApplicantIdForUpdate(20L, 30L))
                .thenReturn(Optional.of(application));
        when(paymentTransactionRepository.findByOrderIdForUpdate("MATE-10-30"))
                .thenReturn(Optional.of(transaction));

        PartyLifecycleMutationService.RefundAttemptClaim result =
                service.claimRefundAttempt(10L, 20L, 30L, now.minusSeconds(900));

        assertThat(result).isNull();
        verify(paymentTransactionRepository, org.mockito.Mockito.never()).save(transaction);
    }

    @Test
    void claimRefundAttempt_refreshesStaleInFlightLease() {
        Instant claimStartedAt = Instant.now();
        Instant retryBefore = claimStartedAt.minusSeconds(900);
        PartyApplication application = PartyApplication.builder()
                .id(21L)
                .partyId(11L)
                .applicantId(31L)
                .isRejected(true)
                .isPaid(true)
                .orderId("MATE-11-31")
                .build();
        PaymentTransaction transaction = PaymentTransaction.builder()
                .orderId("MATE-11-31")
                .paymentStatus(PaymentStatus.REFUND_REQUESTED)
                .updatedAt(retryBefore.minusSeconds(60))
                .build();
        when(partyRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(Party.builder().id(11L).build()));
        when(applicationRepository.findByIdAndApplicantIdForUpdate(21L, 31L))
                .thenReturn(Optional.of(application));
        when(paymentTransactionRepository.findByOrderIdForUpdate("MATE-11-31"))
                .thenReturn(Optional.of(transaction));

        PartyLifecycleMutationService.RefundAttemptClaim result =
                service.claimRefundAttempt(11L, 21L, 31L, retryBefore);

        assertThat(result).isNotNull();
        assertThat(transaction.getUpdatedAt()).isAfterOrEqualTo(claimStartedAt);
        verify(paymentTransactionRepository).save(transaction);
    }

    @Test
    void claimRefundAttempt_recoversMissingPaymentTransactionFromPaidApplication() {
        Party party = Party.builder().id(12L).hostId(99L).build();
        PartyApplication application = PartyApplication.builder()
                .id(22L)
                .partyId(12L)
                .applicantId(32L)
                .isRejected(true)
                .isPaid(true)
                .paymentType(PartyApplication.PaymentType.FULL)
                .depositAmount(45000)
                .orderId("MATE-12-32")
                .paymentKey("payment-key-12-32")
                .build();
        when(partyRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(party));
        when(applicationRepository.findByIdAndApplicantIdForUpdate(22L, 32L))
                .thenReturn(Optional.of(application));
        when(paymentTransactionRepository.findByOrderIdForUpdate("MATE-12-32"))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.save(org.mockito.ArgumentMatchers.any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PartyLifecycleMutationService.RefundAttemptClaim result =
                service.claimRefundAttempt(12L, 22L, 32L, Instant.now().minusSeconds(900));

        assertThat(result).isNotNull();
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(captor.capture());
        PaymentTransaction transaction = captor.getValue();
        assertThat(transaction.getPartyId()).isEqualTo(12L);
        assertThat(transaction.getApplicationId()).isEqualTo(22L);
        assertThat(transaction.getBuyerUserId()).isEqualTo(32L);
        assertThat(transaction.getSellerUserId()).isEqualTo(99L);
        assertThat(transaction.getFlowType()).isEqualTo(PaymentFlowType.SELLING_FULL);
        assertThat(transaction.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_REQUESTED);
        assertThat(transaction.getSettlementStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(transaction.getGrossAmount()).isEqualTo(45000);
    }

    @Test
    void recordRefundFailure_commitsRetryablePaymentState() {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .orderId("MATE-10-30")
                .paymentStatus(PaymentStatus.PAID)
                .build();
        when(paymentTransactionRepository.findByOrderIdForUpdate("MATE-10-30"))
                .thenReturn(Optional.of(transaction));

        service.recordRefundFailure("MATE-10-30");

        assertThat(transaction.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
        verify(paymentTransactionRepository).save(transaction);
    }

    @Test
    void expirePendingParty_recordsEveryExpiryLinkReasonCode() {
        List<ExpiryReasonScenario> scenarios = List.of(
                new ExpiryReasonScenario(
                        MateLinkReconciliationReasonCode.APPLICATION_LINK_INCOMPLETE,
                        false,
                        application -> application.setApplicantId(null),
                        null),
                new ExpiryReasonScenario(
                        MateLinkReconciliationReasonCode.PARTY_LINK_MISMATCH,
                        false,
                        application -> application.setPartyId(13L),
                        null),
                new ExpiryReasonScenario(
                        MateLinkReconciliationReasonCode.PAYMENT_TRANSACTION_MISSING,
                        true,
                        application -> { },
                        null),
                new ExpiryReasonScenario(
                        MateLinkReconciliationReasonCode.PAYMENT_LINK_INCOMPLETE,
                        false,
                        application -> { },
                        payment -> payment.setSellerUserId(null)),
                new ExpiryReasonScenario(
                        MateLinkReconciliationReasonCode.PAYMENT_APPLICATION_LINK_MISMATCH,
                        false,
                        application -> { },
                        payment -> payment.setApplicationId(999L)),
                new ExpiryReasonScenario(
                        MateLinkReconciliationReasonCode.PARTY_LINK_MISSING,
                        false,
                        application -> { },
                        payment -> payment.setPartyId(null)),
                new ExpiryReasonScenario(
                        MateLinkReconciliationReasonCode.PAYMENT_PARTY_LINK_MISMATCH,
                        false,
                        application -> { },
                        payment -> payment.setPartyId(13L)),
                new ExpiryReasonScenario(
                        MateLinkReconciliationReasonCode.PAYMENT_BUYER_LINK_MISMATCH,
                        false,
                        application -> { },
                        payment -> payment.setBuyerUserId(999L)),
                new ExpiryReasonScenario(
                        MateLinkReconciliationReasonCode.PAYMENT_ORDER_LINK_MISMATCH,
                        false,
                        application -> { },
                        payment -> payment.setOrderId("ORDER-OTHER")),
                new ExpiryReasonScenario(
                        MateLinkReconciliationReasonCode.PAYMENT_SELLER_LINK_MISMATCH,
                        false,
                        application -> { },
                        payment -> payment.setSellerUserId(71L)));

        for (ExpiryReasonScenario scenario : scenarios) {
            reset(
                    partyRepository,
                    applicationRepository,
                    paymentTransactionRepository,
                    payoutTransactionRepository,
                    reconciliationCaseRecorder);

            LocalDate today = LocalDate.of(2026, 9, 30);
            Party party = Party.builder()
                    .id(12L)
                    .hostId(70L)
                    .status(Party.PartyStatus.PENDING)
                    .gameDate(today.minusDays(1))
                    .build();
            PartyApplication application = PartyApplication.builder()
                    .id(22L)
                    .partyId(12L)
                    .applicantId(32L)
                    .orderId("ORDER-22")
                    .isPaid(true)
                    .isApproved(true)
                    .isRejected(false)
                    .build();
            scenario.applicationMutation().accept(application);

            PaymentTransaction payment = scenario.paymentMissing()
                    ? null
                    : PaymentTransaction.builder()
                            .id(42L)
                            .partyId(12L)
                            .applicationId(22L)
                            .buyerUserId(32L)
                            .sellerUserId(70L)
                            .orderId("ORDER-22")
                            .netAmount(15000)
                            .paymentStatus(PaymentStatus.PAID)
                            .settlementStatus(SettlementStatus.REQUESTED)
                            .build();
            if (payment != null && scenario.paymentMutation() != null) {
                scenario.paymentMutation().accept(payment);
            }

            when(partyRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(party));
            when(applicationRepository.findApprovedPaidApplicationsByPartyIdForUpdate(12L))
                    .thenReturn(List.of(application));
            when(partyRepository.save(party)).thenReturn(party);
            if (application.getOrderId() != null && !application.getOrderId().isBlank()) {
                when(paymentTransactionRepository.findByOrderIdForUpdate(application.getOrderId()))
                        .thenReturn(Optional.ofNullable(payment));
            }

            Party expired = service.expirePendingParty(12L, today);

            assertThat(expired.getStatus()).isEqualTo(Party.PartyStatus.FAILED);
            verify(reconciliationCaseRecorder).record(
                    eq(12L),
                    eq(22L),
                    eq(payment == null ? null : 42L),
                    eq(scenario.expectedReason()));
        }
    }

    private record ExpiryReasonScenario(
            MateLinkReconciliationReasonCode expectedReason,
            boolean paymentMissing,
            Consumer<PartyApplication> applicationMutation,
            Consumer<PaymentTransaction> paymentMutation) {
    }
}
