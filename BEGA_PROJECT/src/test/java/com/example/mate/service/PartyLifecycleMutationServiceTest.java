package com.example.mate.service;

import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
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

    @InjectMocks
    private PartyLifecycleMutationService service;

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
}
