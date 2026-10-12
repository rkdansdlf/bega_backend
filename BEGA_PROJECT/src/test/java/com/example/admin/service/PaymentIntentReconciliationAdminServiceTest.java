package com.example.admin.service;

import com.example.admin.dto.PaymentIntentReconciliationResolutionRequest;
import com.example.admin.dto.PaymentIntentReconciliationResolutionResultDto;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.entity.PaymentIntentReconciliationEvent;
import com.example.mate.entity.PaymentIntentReconciliationEvent.EventType;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.repository.PaymentIntentReconciliationEventRepository;
import com.example.mate.repository.PaymentIntentRepository;
import com.example.common.jobs.JobSubmissionGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentIntentReconciliationAdminServiceTest {

    @Mock
    private PaymentIntentRepository paymentIntentRepository;
    @Mock
    private PaymentIntentReconciliationEventRepository eventRepository;
    @Mock
    private JobSubmissionGateway jobScheduler;

    @InjectMocks
    private PaymentIntentReconciliationAdminService service;

    @Test
    void verifiedPaymentNeedsExactProposalAndIndependentAdministratorApproval() {
        PaymentIntent intent = heldIntent();
        PaymentIntentReconciliationEvent hold = holdEvent();
        PaymentIntentReconciliationResolutionRequest request = verifiedRequest(
                PaymentIntentReconciliationResolutionRequest.Outcome.VERIFIED_PAYMENT, "DONE");
        PaymentIntentReconciliationEvent proposal = proposalEvent(
                request, EventType.VERIFIED_PAYMENT_PROPOSED, 72L);
        given(paymentIntentRepository.findByIdForUpdate(410L)).willReturn(Optional.of(intent));
        given(eventRepository.findByPaymentIntentIdOrderByCreatedAtAscIdAsc(410L))
                .willReturn(List.of(hold), List.of(hold, proposal));

        PaymentIntentReconciliationResolutionResultDto proposed = service.resolve(410L, 72L, request);

        assertThat(proposed.awaitingIndependentReview()).isTrue();
        assertThat(proposed.outcome()).isEqualTo("VERIFIED_PAYMENT");
        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED);
        assertThat(intent.getFailureCode()).isEqualTo("PROVIDER_IDENTITY_MISMATCH_REVIEW_PENDING");
        assertThat(intent.getPaymentKey()).isNull();

        PaymentIntentReconciliationResolutionResultDto approved = service.resolve(410L, 73L, request);

        assertThat(approved.awaitingIndependentReview()).isFalse();
        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED);
        assertThat(intent.getPaymentKey()).isEqualTo("synthetic-verified-key");
        assertThat(intent.getFailureCode()).isEqualTo("VERIFIED_PAYMENT_PENDING_APPLICATION");
        ArgumentCaptor<PaymentIntentReconciliationEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentIntentReconciliationEvent.class);
        verify(eventRepository, org.mockito.Mockito.times(2)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(PaymentIntentReconciliationEvent::getEventType)
                .containsExactly(EventType.VERIFIED_PAYMENT_PROPOSED, EventType.VERIFIED_PAYMENT_BOUND);
        assertThat(eventCaptor.getAllValues()).extracting(PaymentIntentReconciliationEvent::getRecordedBy)
                .containsExactly(72L, 73L);
        assertThat(eventCaptor.getAllValues())
                .extracting(PaymentIntentReconciliationEvent::getEvidenceReference)
                .containsExactly("case-410-provider-console", "case-410-provider-console");
        assertThat(eventCaptor.getAllValues())
                .extracting(PaymentIntentReconciliationEvent::getVerifiedPaymentKey)
                .containsExactly("synthetic-verified-key", "synthetic-verified-key");
        verify(paymentIntentRepository, org.mockito.Mockito.times(2)).saveAndFlush(intent);
    }

    @Test
    void sameAdministratorCannotApproveTheirOwnProposal() {
        PaymentIntent intent = heldIntent();
        PaymentIntentReconciliationEvent hold = holdEvent();
        PaymentIntentReconciliationResolutionRequest request = verifiedRequest(
                PaymentIntentReconciliationResolutionRequest.Outcome.VERIFIED_PAYMENT, "DONE");
        PaymentIntentReconciliationEvent proposal = proposalEvent(
                request, EventType.VERIFIED_PAYMENT_PROPOSED, 72L);
        given(paymentIntentRepository.findByIdForUpdate(410L)).willReturn(Optional.of(intent));
        given(eventRepository.findByPaymentIntentIdOrderByCreatedAtAscIdAsc(410L))
                .willReturn(List.of(hold), List.of(hold, proposal));

        service.resolve(410L, 72L, request);

        assertThatThrownBy(() -> service.resolve(410L, 72L, request))
                .isInstanceOf(TossPaymentException.class)
                .hasMessageContaining("서로 다른 관리자");
        assertThat(intent.getFailureCode()).isEqualTo("PROVIDER_IDENTITY_MISMATCH_REVIEW_PENDING");
        assertThat(intent.getPaymentKey()).isNull();
        verify(eventRepository).save(any(PaymentIntentReconciliationEvent.class));
        verify(paymentIntentRepository).saveAndFlush(intent);
    }

    @Test
    void changedEvidenceOrTupleReplacesProposalAndRequiresAnotherIndependentApproval() {
        PaymentIntent intent = heldIntent();
        PaymentIntentReconciliationEvent hold = holdEvent();
        PaymentIntentReconciliationResolutionRequest original = verifiedRequest(
                PaymentIntentReconciliationResolutionRequest.Outcome.VERIFIED_PAYMENT, "DONE");
        PaymentIntentReconciliationEvent firstProposal = proposalEvent(
                original, EventType.VERIFIED_PAYMENT_PROPOSED, 72L);
        PaymentIntentReconciliationResolutionRequest changed = verifiedRequest(
                PaymentIntentReconciliationResolutionRequest.Outcome.VERIFIED_PAYMENT, "DONE");
        changed.setEvidenceReference("case-410-independent-provider-record");
        PaymentIntentReconciliationEvent replacementProposal = proposalEvent(
                changed, EventType.VERIFIED_PAYMENT_PROPOSED, 73L);
        given(paymentIntentRepository.findByIdForUpdate(410L)).willReturn(Optional.of(intent));
        given(eventRepository.findByPaymentIntentIdOrderByCreatedAtAscIdAsc(410L))
                .willReturn(List.of(hold), List.of(hold, firstProposal), List.of(hold, firstProposal, replacementProposal));

        assertThat(service.resolve(410L, 72L, original).awaitingIndependentReview()).isTrue();
        PaymentIntentReconciliationResolutionResultDto replaced = service.resolve(410L, 73L, changed);
        assertThat(replaced.awaitingIndependentReview()).isTrue();
        assertThat(intent.getPaymentKey()).isNull();
        assertThat(intent.getFailureCode()).isEqualTo("PROVIDER_IDENTITY_MISMATCH_REVIEW_PENDING");

        PaymentIntentReconciliationResolutionResultDto independentlyApproved = service.resolve(410L, 74L, changed);
        assertThat(independentlyApproved.awaitingIndependentReview()).isFalse();
        assertThat(intent.getFailureCode()).isEqualTo("VERIFIED_PAYMENT_PENDING_APPLICATION");
        ArgumentCaptor<PaymentIntentReconciliationEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentIntentReconciliationEvent.class);
        verify(eventRepository, org.mockito.Mockito.times(3)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(PaymentIntentReconciliationEvent::getEventType)
                .containsExactly(
                        EventType.VERIFIED_PAYMENT_PROPOSED,
                        EventType.VERIFIED_PAYMENT_PROPOSED,
                        EventType.VERIFIED_PAYMENT_BOUND);
        assertThat(eventCaptor.getAllValues()).extracting(PaymentIntentReconciliationEvent::getRecordedBy)
                .containsExactly(72L, 73L, 74L);
    }

    @Test
    void resolveRejectsStatusOnlyOrMismatchedProviderEvidence() {
        PaymentIntent intent = heldIntent();
        given(paymentIntentRepository.findByIdForUpdate(410L)).willReturn(Optional.of(intent));
        given(eventRepository.findByPaymentIntentIdOrderByCreatedAtAscIdAsc(410L)).willReturn(List.of(holdEvent()));
        PaymentIntentReconciliationResolutionRequest statusOnly = PaymentIntentReconciliationResolutionRequest.builder()
                .outcome(PaymentIntentReconciliationResolutionRequest.Outcome.VERIFIED_PAYMENT)
                .evidenceReference("case-410-provider-console")
                .verifiedStatus("DONE")
                .build();

        assertThatThrownBy(() -> service.resolve(410L, 72L, statusOnly))
                .isInstanceOf(TossPaymentException.class)
                .hasMessageContaining("결제 키");

        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED);
        verify(eventRepository, never()).save(any(PaymentIntentReconciliationEvent.class));
        verify(paymentIntentRepository, never()).saveAndFlush(any(PaymentIntent.class));
    }

    @Test
    void verifiedCancellationRequiresTwoAdministratorsBeforeTerminalTransition() {
        PaymentIntent intent = heldIntent();
        PaymentIntentReconciliationEvent hold = holdEvent();
        PaymentIntentReconciliationResolutionRequest request = verifiedRequest(
                PaymentIntentReconciliationResolutionRequest.Outcome.VERIFIED_REFUNDED, "CANCELED");
        PaymentIntentReconciliationEvent proposal = proposalEvent(
                request, EventType.VERIFIED_REFUNDED_PROPOSED, 72L);
        given(paymentIntentRepository.findByIdForUpdate(410L)).willReturn(Optional.of(intent));
        given(eventRepository.findByPaymentIntentIdOrderByCreatedAtAscIdAsc(410L))
                .willReturn(List.of(hold), List.of(hold, proposal));

        PaymentIntentReconciliationResolutionResultDto proposed = service.resolve(410L, 72L, request);
        assertThat(proposed.awaitingIndependentReview()).isTrue();
        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED);

        PaymentIntentReconciliationResolutionResultDto approved = service.resolve(410L, 73L, request);
        assertThat(approved.awaitingIndependentReview()).isFalse();
        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCELED);
        ArgumentCaptor<PaymentIntentReconciliationEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentIntentReconciliationEvent.class);
        verify(eventRepository, org.mockito.Mockito.times(2)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(PaymentIntentReconciliationEvent::getEventType)
                .containsExactly(EventType.VERIFIED_REFUNDED_PROPOSED, EventType.VERIFIED_REFUNDED);
        verify(paymentIntentRepository, org.mockito.Mockito.times(2)).saveAndFlush(intent);
    }

    private PaymentIntent heldIntent() {
        return PaymentIntent.builder()
                .id(410L)
                .orderId("MATE-10-20-identity-mismatch")
                .expectedAmount(35000)
                .currency("KRW")
                .failureCode("PROVIDER_IDENTITY_MISMATCH")
                .status(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED)
                .build();
    }

    private PaymentIntentReconciliationEvent holdEvent() {
        return PaymentIntentReconciliationEvent.builder()
                .paymentIntentId(410L)
                .eventType(EventType.IDENTITY_MISMATCH_HOLD)
                .requestedPaymentKey("synthetic-request-key")
                .build();
    }

    private PaymentIntentReconciliationEvent proposalEvent(
            PaymentIntentReconciliationResolutionRequest request,
            EventType eventType,
            Long administratorId) {
        return PaymentIntentReconciliationEvent.builder()
                .paymentIntentId(410L)
                .eventType(eventType)
                .verifiedPaymentKey(request.getVerifiedPaymentKey())
                .verifiedOrderId(request.getVerifiedOrderId())
                .verifiedAmount(request.getVerifiedAmount())
                .verifiedStatus(request.getVerifiedStatus())
                .evidenceReference(request.getEvidenceReference())
                .recordedBy(administratorId)
                .build();
    }

    private PaymentIntentReconciliationResolutionRequest verifiedRequest(
            PaymentIntentReconciliationResolutionRequest.Outcome outcome,
            String status) {
        return PaymentIntentReconciliationResolutionRequest.builder()
                .outcome(outcome)
                .evidenceReference("case-410-provider-console")
                .verifiedPaymentKey("synthetic-verified-key")
                .verifiedOrderId("MATE-10-20-identity-mismatch")
                .verifiedAmount(35000)
                .verifiedStatus(status)
                .build();
    }
}
