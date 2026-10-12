package com.example.admin.service;

import com.example.admin.dto.PaymentIntentReconciliationPageDto;
import com.example.admin.dto.PaymentIntentReconciliationResolutionRequest;
import com.example.admin.dto.PaymentIntentReconciliationResolutionResultDto;
import com.example.admin.dto.PaymentIntentReconciliationReviewDto;
import com.example.common.jobs.JobSubmissionGateway;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.entity.PaymentIntentReconciliationEvent;
import com.example.mate.entity.PaymentIntentReconciliationEvent.EventType;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.repository.PaymentIntentReconciliationEventRepository;
import com.example.mate.repository.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentIntentReconciliationAdminService {

    private static final String HOLD_CODE = "PROVIDER_IDENTITY_MISMATCH";
    private static final String REVIEW_PENDING_CODE = "PROVIDER_IDENTITY_MISMATCH_REVIEW_PENDING";
    private static final List<String> REVIEWABLE_FAILURE_CODES = List.of(HOLD_CODE, REVIEW_PENDING_CODE);
    private static final int MAX_PAGE_SIZE = 100;
    private static final String EVIDENCE_REFERENCE_PATTERN = "[A-Za-z0-9][A-Za-z0-9._:/-]{0,299}";

    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentIntentReconciliationEventRepository eventRepository;
    private final JobSubmissionGateway jobScheduler;

    @Transactional(readOnly = true)
    public PaymentIntentReconciliationPageDto listPending(int requestedPage, int requestedSize) {
        int pageNumber = Math.max(0, requestedPage);
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedSize));
        Page<PaymentIntent> page = paymentIntentRepository.findByStatusAndFailureCodeInOrderByCreatedAtAsc(
                PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED,
                REVIEWABLE_FAILURE_CODES,
                PageRequest.of(pageNumber, pageSize));
        List<PaymentIntentReconciliationReviewDto> items = page.getContent().stream()
                .map(this::toReviewDto)
                .toList();
        return new PaymentIntentReconciliationPageDto(
                items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PaymentIntentReconciliationReviewDto getReview(Long intentId) {
        PaymentIntent intent = paymentIntentRepository.findById(intentId)
                .orElseThrow(() -> notFound(intentId));
        List<PaymentIntentReconciliationEvent> events = eventRepository
                .findByPaymentIntentIdOrderByCreatedAtAscIdAsc(intentId);
        if (events.stream().noneMatch(event -> event.getEventType() == EventType.IDENTITY_MISMATCH_HOLD)) {
            throw notFound(intentId);
        }
        return toReviewDto(intent, events);
    }

    @Transactional
    public PaymentIntentReconciliationResolutionResultDto resolve(
            Long intentId,
            Long administratorId,
            PaymentIntentReconciliationResolutionRequest request) {
        jobScheduler.requireWritable();
        if (administratorId == null) {
            throw new TossPaymentException("ADMIN_ID_REQUIRED", "관리자 인증 정보를 확인할 수 없습니다.", HttpStatus.FORBIDDEN);
        }
        validateResolutionRequest(request);

        PaymentIntent intent = paymentIntentRepository.findByIdForUpdate(intentId)
                .orElseThrow(() -> notFound(intentId));
        if (intent.getStatus() != PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED
                || !(HOLD_CODE.equals(intent.getFailureCode())
                        || REVIEW_PENDING_CODE.equals(intent.getFailureCode()))) {
            throw new TossPaymentException(
                    "PAYMENT_INTENT_RECONCILIATION_ALREADY_RESOLVED",
                    "대기 중인 결제 대사 항목이 아닙니다.",
                    HttpStatus.CONFLICT);
        }
        List<PaymentIntentReconciliationEvent> events = eventRepository
                .findByPaymentIntentIdOrderByCreatedAtAscIdAsc(intentId);
        if (events.stream().noneMatch(event -> event.getEventType() == EventType.IDENTITY_MISMATCH_HOLD)) {
            throw new TossPaymentException(
                    "PAYMENT_INTENT_RECONCILIATION_EVIDENCE_MISSING",
                    "대사 근거 기록을 찾을 수 없습니다.",
                    HttpStatus.CONFLICT);
        }

        validateProviderProposal(intent, request);
        PaymentIntentReconciliationEvent proposal = events.stream()
                .filter(event -> isProposal(event.getEventType()))
                .filter(event -> matchesProposal(event, request))
                .reduce((first, last) -> last)
                .orElse(null);
        if (proposal == null) {
            eventRepository.save(createProposalEvent(
                    intentId, administratorId, request, proposalEventType(request)));
            return new PaymentIntentReconciliationResolutionResultDto(
                    true, true, request.getOutcome().name());
        }
        if (administratorId.equals(proposal.getRecordedBy())) {
            throw new TossPaymentException(
                    "PAYMENT_INTENT_RECONCILIATION_SECOND_ADMIN_REQUIRED",
                    "같은 제안의 독립 검토는 다른 관리자가 수행해야 합니다.",
                    HttpStatus.CONFLICT);
        }

        eventRepository.save(createProposalEvent(
                intentId, administratorId, request, proposalEventType(request)));
        return new PaymentIntentReconciliationResolutionResultDto(
                false, true, request.getOutcome().name());
    }

    private PaymentIntentReconciliationEvent createProposalEvent(
            Long intentId,
            Long administratorId,
            PaymentIntentReconciliationResolutionRequest request,
            EventType eventType) {
        return PaymentIntentReconciliationEvent.builder()
                .paymentIntentId(intentId)
                .eventType(eventType)
                .candidatePaymentKey(request.getVerifiedPaymentKey())
                .candidateOrderId(request.getVerifiedOrderId())
                .candidateAmount(request.getVerifiedAmount())
                .candidateStatus(request.getVerifiedStatus())
                .evidenceReference(request.getEvidenceReference())
                .recordedBy(administratorId)
                .build();
    }

    private boolean isProposal(EventType eventType) {
        return eventType == EventType.VERIFIED_PAYMENT_PROPOSED
                || eventType == EventType.VERIFIED_CANCELED_PROPOSED
                || eventType == EventType.VERIFIED_REFUNDED_PROPOSED;
    }

    private EventType proposalEventType(PaymentIntentReconciliationResolutionRequest request) {
        return switch (request.getOutcome()) {
            case VERIFIED_PAYMENT -> EventType.VERIFIED_PAYMENT_PROPOSED;
            case VERIFIED_CANCELED -> EventType.VERIFIED_CANCELED_PROPOSED;
            case VERIFIED_REFUNDED -> EventType.VERIFIED_REFUNDED_PROPOSED;
        };
    }

    private boolean matchesProposal(
            PaymentIntentReconciliationEvent proposal,
            PaymentIntentReconciliationResolutionRequest request) {
        return proposal.getEventType() == proposalEventType(request)
                && request.getVerifiedPaymentKey().equals(proposal.getCandidatePaymentKey() != null
                        ? proposal.getCandidatePaymentKey() : proposal.getVerifiedPaymentKey())
                && request.getVerifiedOrderId().equals(proposal.getCandidateOrderId() != null
                        ? proposal.getCandidateOrderId() : proposal.getVerifiedOrderId())
                && request.getVerifiedAmount().equals(proposal.getCandidateAmount() != null
                        ? proposal.getCandidateAmount() : proposal.getVerifiedAmount())
                && request.getVerifiedStatus().equals(proposal.getCandidateStatus() != null
                        ? proposal.getCandidateStatus() : proposal.getVerifiedStatus())
                && request.getEvidenceReference().equals(proposal.getEvidenceReference());
    }

    private void validateResolutionRequest(PaymentIntentReconciliationResolutionRequest request) {
        if (request == null
                || request.getOutcome() == null
                || request.getEvidenceReference() == null
                || !request.getEvidenceReference().matches(EVIDENCE_REFERENCE_PATTERN)) {
            throw new TossPaymentException(
                    "PAYMENT_INTENT_RECONCILIATION_EVIDENCE_REQUIRED",
                    "관리자 제안에 대한 감사 추적 참조가 필요합니다.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void validateProviderProposal(
            PaymentIntent intent,
            PaymentIntentReconciliationResolutionRequest request) {
        if (request.getVerifiedPaymentKey() == null || request.getVerifiedPaymentKey().isBlank()
                || !intent.getOrderId().equals(request.getVerifiedOrderId())
                || !intent.getExpectedAmount().equals(request.getVerifiedAmount())) {
            throw new TossPaymentException(
                    "PAYMENT_INTENT_RECONCILIATION_VERIFIED_IDENTITY_INVALID",
                    "제안된 결제 키, 주문, 금액이 서버 결제 의도와 일치해야 합니다.",
                    HttpStatus.BAD_REQUEST);
        }

        String requiredStatus = request.getOutcome()
                == PaymentIntentReconciliationResolutionRequest.Outcome.VERIFIED_PAYMENT
                        ? "DONE"
                        : "CANCELED";
        if (!requiredStatus.equals(request.getVerifiedStatus())) {
            throw new TossPaymentException(
                    "PAYMENT_INTENT_RECONCILIATION_VERIFIED_STATUS_INVALID",
                    "제안 결과와 제출된 provider 상태가 일치해야 합니다.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private PaymentIntentReconciliationReviewDto toReviewDto(PaymentIntent intent) {
        return toReviewDto(
                intent,
                eventRepository.findByPaymentIntentIdOrderByCreatedAtAscIdAsc(intent.getId()));
    }

    private PaymentIntentReconciliationReviewDto toReviewDto(
            PaymentIntent intent,
            List<PaymentIntentReconciliationEvent> events) {
        PaymentIntentReconciliationEvent hold = events.stream()
                .filter(event -> event.getEventType() == EventType.IDENTITY_MISMATCH_HOLD)
                .findFirst()
                .orElse(null);
        List<PaymentIntentReconciliationReviewDto.Event> auditEvents = events.stream()
                .map(event -> new PaymentIntentReconciliationReviewDto.Event(
                        event.getEventType().name(),
                        event.getCandidatePaymentKey(),
                        event.getCandidateOrderId(),
                        event.getCandidateAmount(),
                        event.getCandidateStatus(),
                        event.getVerifiedPaymentKey(),
                        event.getVerifiedOrderId(),
                        event.getVerifiedAmount(),
                        event.getVerifiedStatus(),
                        event.getEvidenceReference(),
                        event.getRecordedBy(),
                        event.getCreatedAt()))
                .toList();
        return new PaymentIntentReconciliationReviewDto(
                intent.getId(),
                intent.getOrderId(),
                intent.getExpectedAmount(),
                intent.getCurrency(),
                hold != null ? hold.getCreatedAt() : intent.getUpdatedAt(),
                hold != null ? hold.getRequestedPaymentKey() : null,
                hold != null ? hold.getCandidatePaymentKey() : null,
                hold != null ? hold.getCandidateOrderId() : null,
                hold != null ? hold.getCandidateAmount() : null,
                hold != null ? hold.getCandidateStatus() : null,
                auditEvents);
    }

    private TossPaymentException notFound(Long intentId) {
        return new TossPaymentException(
                "PAYMENT_INTENT_RECONCILIATION_NOT_FOUND",
                "결제 대사 기록을 찾을 수 없습니다: " + intentId,
                HttpStatus.NOT_FOUND);
    }
}
