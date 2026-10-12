package com.example.mate.service;

import com.example.auth.service.UserService;
import com.example.auth.repository.UserRepository;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.entity.PaymentIntentReconciliationEvent;
import com.example.mate.entity.PaymentIntentReconciliationEvent.EventType;
import com.example.mate.exception.DuplicateApplicationException;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.exception.PartyFullException;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.exception.UnauthorizedAccessException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentIntentRepository;
import com.example.mate.repository.PaymentIntentReconciliationEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import com.example.common.jobs.JobSubmissionGateway;
import org.springframework.http.HttpStatus;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIntentService {

    private static final int MAX_PENDING_APPLICATIONS = 10;
    private static final int MAX_COMPENSATION_ATTEMPTS = 5;
    private static final String CONFIRMATION_IN_PROGRESS_CODE = "CONFIRM_REQUESTED";

    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentIntentReconciliationEventRepository reconciliationEventRepository;
    private final PartyApplicationRepository applicationRepository;
    private final PartyRepository partyRepository;
    private final PaymentAmountCalculator paymentAmountCalculator;
    private final TossPaymentService tossPaymentService;
    private final JobSubmissionGateway jobScheduler;
    private final PaymentMetricsService paymentMetricsService;
    private final PaymentIntentReconciliationService paymentIntentReconciliationService;
    private final UserService userService;
    private final UserRepository userRepository;

    @Value("${mate.payment.intent-ttl-minutes:30}")
    private long intentTtlMinutes;

    @Value("${mate.auth.require-social-verification:true}")
    private boolean requireSocialVerification;

    @Transactional
    public TossPaymentDTO.PrepareResponse prepareIntent(TossPaymentDTO.PrepareClientRequest request, Principal principal) {
        jobScheduler.requireWritable();
        return prepareIntent(request, resolveUserId(principal));
    }

    @Transactional
    public TossPaymentDTO.PrepareResponse prepareIntent(TossPaymentDTO.PrepareClientRequest request, Long applicantId) {
        jobScheduler.requireWritable();
        if (request == null || request.getPartyId() == null) {
            throw new InvalidApplicationStatusException("partyId는 필수입니다.");
        }
        requireUserId(applicantId);

        PaymentFlowType flowType = request.getFlowType() != null
                ? request.getFlowType()
                : PaymentFlowType.DEPOSIT;
        validateApplicationPreconditions(request.getPartyId(), applicantId, flowType);

        PaymentAmountCalculator.AmountInfo amountInfo = paymentAmountCalculator.calculateAmount(request.getPartyId(), flowType);
        String orderId = generateOrderId(request.getPartyId(), applicantId);

        PaymentIntent intent = PaymentIntent.builder()
                .orderId(orderId)
                .partyId(request.getPartyId())
                .applicantId(applicantId)
                .expectedAmount(amountInfo.amount())
                .currency(amountInfo.currency())
                .flowType(flowType)
                .paymentType(resolvePaymentType(flowType))
                .cancelPolicyVersion(request.getCancelPolicyVersion())
                .mode(PaymentIntent.IntentMode.PREPARED)
                .status(PaymentIntent.IntentStatus.PREPARED)
                .expiresAt(Instant.now().plusSeconds(intentTtlMinutes * 60))
                .build();

        PaymentIntent saved = paymentIntentRepository.save(intent);

        return TossPaymentDTO.PrepareResponse.builder()
                .intentId(saved.getId())
                .orderId(saved.getOrderId())
                .amount(saved.getExpectedAmount())
                .currency(saved.getCurrency())
                .orderName(amountInfo.orderName())
                .flowType(saved.getFlowType())
                .cancelPolicyVersion(saved.getCancelPolicyVersion())
                .paymentType(saved.getPaymentType())
                .build();
    }

    @Transactional
    public PaymentIntent resolveIntentForConfirm(TossPaymentDTO.ClientConfirmRequest request, Principal principal) {
        jobScheduler.requireWritable();
        return resolveIntentForConfirm(request, resolveUserId(principal));
    }

    @Transactional
    public PaymentIntent resolveIntentForConfirm(TossPaymentDTO.ClientConfirmRequest request, Long applicantId) {
        jobScheduler.requireWritable();
        if (request == null || request.getOrderId() == null || request.getOrderId().isBlank()) {
            throw new InvalidApplicationStatusException("orderId는 필수입니다.");
        }
        if (request.getPaymentKey() == null || request.getPaymentKey().isBlank()) {
            throw new InvalidApplicationStatusException("paymentKey는 필수입니다.");
        }
        requireUserId(applicantId);

        PaymentIntent intent = findIntentForUpdate(request, applicantId)
                .orElseGet(() -> createLegacyIntent(request, applicantId));
        if (request.getPartyId() != null && !request.getPartyId().equals(intent.getPartyId())) {
            throw new InvalidApplicationStatusException("결제 요청의 partyId가 일치하지 않습니다.");
        }
        if (request.getFlowType() != null && intent.getFlowType() != null
                && request.getFlowType() != intent.getFlowType()) {
            throw new InvalidApplicationStatusException("결제 flowType이 준비 정보와 일치하지 않습니다.");
        }

        if (intent.getStatus() == PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED
                && !hasVerifiedIdentityPaymentBinding(intent.getId())) {
            throw reconciliationRequiredException();
        }
        if (intent.getStatus() == PaymentIntent.IntentStatus.CANCELED
                || intent.getStatus() == PaymentIntent.IntentStatus.CANCEL_REQUESTED
                || intent.getStatus() == PaymentIntent.IntentStatus.CANCEL_FAILED
                || intent.getStatus() == PaymentIntent.IntentStatus.EXPIRED) {
            throw new InvalidApplicationStatusException("이미 종료된 결제 요청입니다. 새로 결제를 시도해주세요.");
        }

        java.util.Optional<PartyApplication> existingApplication =
                applicationRepository.findByOrderIdForUpdate(intent.getOrderId());
        if (existingApplication.isPresent()) {
            requireApplicationMatchesIntent(existingApplication.get(), intent);
            intent.setStatus(PaymentIntent.IntentStatus.APPLICATION_CREATED);
            paymentIntentRepository.save(intent);
            return intent;
        }

        if (intent.getStatus() == PaymentIntent.IntentStatus.APPLICATION_CREATED) {
            throw new InvalidApplicationStatusException("신청이 이미 종료되었거나 연결 상태를 확인할 수 없습니다.");
        }

        boolean alreadyConfirmed = intent.getStatus() == PaymentIntent.IntentStatus.CONFIRMED
                || hasVerifiedIdentityPaymentBinding(intent.getId());
        if (!alreadyConfirmed && intent.getExpiresAt() != null && intent.getExpiresAt().isBefore(Instant.now())) {
            intent.setStatus(PaymentIntent.IntentStatus.EXPIRED);
            paymentIntentRepository.save(intent);
            throw new InvalidApplicationStatusException("결제 요청 유효시간이 만료되었습니다. 다시 시도해주세요.");
        }

        if (!alreadyConfirmed) {
            validateApplicationPreconditions(intent.getPartyId(), applicantId, intent.getFlowType());
            PaymentAmountCalculator.AmountInfo amountInfo = paymentAmountCalculator.calculateAmount(
                    intent.getPartyId(),
                    intent.getFlowType());
            if (!amountInfo.amount().equals(intent.getExpectedAmount())) {
                throw new InvalidApplicationStatusException("결제 금액이 변경되었습니다. 다시 결제를 시도해주세요.");
            }
        }

        return intent;
    }

    @Transactional(readOnly = true)
    public Optional<PaymentIntent> findIntentByOrderId(String orderId) {
        return paymentIntentRepository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public Optional<com.example.mate.dto.PartyApplicationDTO.Response> findExistingApplicationResponse(String orderId, Long applicantId) {
        return applicationRepository.findByOrderId(orderId)
                .filter(application -> application.getApplicantId().equals(applicantId))
                .map(com.example.mate.dto.PartyApplicationDTO.Response::from);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserveConfirmationAttempt(Long intentId, Long applicantId, String paymentKey) {
        jobScheduler.requireWritable();
        PaymentIntent intent = paymentIntentRepository.findByIdAndApplicantIdForUpdate(intentId, applicantId)
                .orElseThrow(() -> new TossPaymentException(
                        "PAYMENT_INTENT_NOT_FOUND",
                        "결제 요청을 찾을 수 없습니다: " + intentId,
                        HttpStatus.NOT_FOUND));

        if (intent.getStatus() == PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED) {
            if (hasVerifiedIdentityPaymentBinding(intentId)) {
                return false;
            }
            throw reconciliationRequiredException();
        }
        if (intent.getStatus() != PaymentIntent.IntentStatus.PREPARED) {
            return false;
        }
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new TossPaymentException(
                    "PAYMENT_KEY_MISSING",
                    "결제 키 정보가 올바르지 않습니다.",
                    HttpStatus.BAD_REQUEST);
        }
        if (intent.getPaymentKey() != null && !intent.getPaymentKey().isBlank()
                && !intent.getPaymentKey().equals(paymentKey)) {
            throw new TossPaymentException(
                    "CONFIRMED_PAYMENT_KEY_MISMATCH",
                    "이미 시도된 결제 키와 요청 정보가 일치하지 않습니다.",
                    HttpStatus.CONFLICT);
        }
        if (intent.getFailureCode() != null && !intent.getFailureCode().isBlank()) {
            if (CONFIRMATION_IN_PROGRESS_CODE.equals(intent.getFailureCode())) {
                if (!paymentKey.equals(intent.getPaymentKey())) {
                    throw new TossPaymentException(
                            "CONFIRMED_PAYMENT_KEY_MISMATCH",
                            "이미 시도된 결제 키와 요청 정보가 일치하지 않습니다.",
                            HttpStatus.CONFLICT);
                }
                return true;
            }
            throw new TossPaymentException(
                    "PAYMENT_CONFIRMATION_RECONCILIATION_REQUIRED",
                    "결제 승인 상태를 확인할 수 없어 기존 결제 키로 다시 확인해야 합니다.",
                    HttpStatus.CONFLICT);
        }

        intent.setPaymentKey(paymentKey);
        intent.setFailureCode(CONFIRMATION_IN_PROGRESS_CODE);
        intent.setFailureMessage("결제 승인 결과 확인 중");
        paymentIntentRepository.saveAndFlush(intent);
        return false;
    }

    @Transactional
    public <T> T withApplicantConfirmationSerialized(Long applicantId, Supplier<T> confirmation) {
        jobScheduler.requireWritable();
        lockConfirmationOwner(applicantId);
        return confirmation.get();
    }

    @Transactional
    public <T> T withApplicantIntentLocked(
            Long intentId,
            Long applicantId,
            java.util.function.Function<PaymentIntent, T> action) {
        jobScheduler.requireWritable();
        PaymentIntent intent = paymentIntentRepository.findByIdAndApplicantIdForUpdate(intentId, applicantId)
                .orElseThrow(() -> new TossPaymentException(
                        "PAYMENT_INTENT_NOT_FOUND",
                        "결제 요청을 찾을 수 없습니다: " + intentId,
                        org.springframework.http.HttpStatus.NOT_FOUND));
        return action.apply(intent);
    }

    @Transactional
    public <T> T withConfirmedIntentForApplicationCreation(
            Long intentId,
            Long applicantId,
            String paymentKey,
            java.util.function.Function<PaymentIntent, T> createApplication) {
        jobScheduler.requireWritable();
        lockConfirmationOwner(applicantId);
        PaymentIntent intent = paymentIntentRepository.findByIdAndApplicantIdForUpdate(intentId, applicantId)
                .orElseThrow(() -> new TossPaymentException(
                        "PAYMENT_INTENT_NOT_FOUND",
                        "결제 요청을 찾을 수 없습니다: " + intentId,
                        org.springframework.http.HttpStatus.NOT_FOUND));
        if (!java.util.Objects.equals(intent.getPaymentKey(), paymentKey)
                || paymentKey == null
                || paymentKey.isBlank()) {
            throw new InvalidApplicationStatusException("승인된 결제 정보가 요청과 일치하지 않습니다.");
        }

        boolean verifiedIdentityBindingPendingApplication = intent.getStatus()
                == PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED
                && hasVerifiedIdentityPaymentBinding(intentId);
        if (intent.getStatus() != PaymentIntent.IntentStatus.CONFIRMED
                && intent.getStatus() != PaymentIntent.IntentStatus.APPLICATION_CREATED
                && !verifiedIdentityBindingPendingApplication) {
            throw new InvalidApplicationStatusException("결제 승인 상태가 신청 생성과 일치하지 않습니다.");
        }

        Long partyId = java.util.Objects.requireNonNull(intent.getPartyId());
        partyRepository.findByIdForUpdate(partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));

        java.util.Optional<PartyApplication> existingApplication =
                applicationRepository.findByOrderIdForUpdate(intent.getOrderId());
        if (existingApplication.isPresent()) {
            requireApplicationMatchesIntent(existingApplication.get(), intent);
        }
        if (intent.getStatus() == PaymentIntent.IntentStatus.APPLICATION_CREATED
                && existingApplication.isEmpty()) {
            throw new InvalidApplicationStatusException("이미 생성된 신청을 확인할 수 없습니다.");
        }

        T result = createApplication.apply(intent);
        if (intent.getStatus() != PaymentIntent.IntentStatus.APPLICATION_CREATED) {
            intent.setStatus(PaymentIntent.IntentStatus.APPLICATION_CREATED);
            intent.setFailureCode(null);
            intent.setFailureMessage(null);
            paymentIntentRepository.save(intent);
        }
        return result;
    }

    private void lockConfirmationOwner(Long applicantId) {
        requireUserId(applicantId);
        try {
            userRepository.lockPaymentConfirmationOwnerForWrite(applicantId)
                    .orElseThrow(() -> new UnauthorizedAccessException("인증 정보를 찾을 수 없습니다."));
        } catch (PessimisticLockingFailureException
                | jakarta.persistence.PessimisticLockException
                | jakarta.persistence.LockTimeoutException contention) {
            throw new TossPaymentException(
                    "PAYMENT_CONFIRMATION_IN_PROGRESS",
                    "이미 결제 승인 처리가 진행 중입니다. 잠시 후 다시 확인해주세요.",
                    HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public void markConfirmed(PaymentIntent intent, String paymentKey) {
        jobScheduler.requireWritable();
        if (intent.getStatus() == PaymentIntent.IntentStatus.APPLICATION_CREATED) {
            return;
        }
        intent.setStatus(PaymentIntent.IntentStatus.CONFIRMED);
        intent.setPaymentKey(paymentKey);
        intent.setFailureCode(null);
        intent.setFailureMessage(null);
        intent.setConfirmedAt(Instant.now());
        paymentIntentRepository.save(intent);
    }

    @Transactional
    public void quarantineProviderIdentityMismatch(
            PaymentIntent intent,
            String requestedPaymentKey,
            TossPaymentDTO.ConfirmResponse providerResponse) {
        quarantineUnverifiedConfirmation(intent, requestedPaymentKey, providerResponse);
    }

    @Transactional
    public void quarantineUnverifiedConfirmation(
            PaymentIntent intent,
            String requestedPaymentKey,
            TossPaymentDTO.ConfirmResponse providerResponse) {
        jobScheduler.requireWritable();
        if (intent == null
                || intent.getStatus() != PaymentIntent.IntentStatus.PREPARED
                || !CONFIRMATION_IN_PROGRESS_CODE.equals(intent.getFailureCode())) {
            throw new TossPaymentException(
                    "PAYMENT_INTENT_RECONCILIATION_STATE_CHANGED",
                    "결제 상태가 변경되어 관리자 대사가 필요합니다.",
                    HttpStatus.CONFLICT);
        }

        intent.setStatus(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED);
        intent.setFailureCode("PROVIDER_IDENTITY_MISMATCH");
        intent.setFailureMessage("Provider confirmation evidence requires administrator reconciliation.");
        paymentIntentRepository.saveAndFlush(intent);
        reconciliationEventRepository.save(PaymentIntentReconciliationEvent.builder()
                .paymentIntentId(intent.getId())
                .eventType(EventType.IDENTITY_MISMATCH_HOLD)
                .requestedPaymentKey(requestedPaymentKey)
                .candidatePaymentKey(providerResponse != null
                        ? boundedEvidence(providerResponse.getPaymentKey(), 200)
                        : null)
                .candidateOrderId(providerResponse != null
                        ? boundedEvidence(providerResponse.getOrderId(), 100)
                        : null)
                .candidateAmount(providerResponse != null ? providerResponse.getTotalAmount() : null)
                .candidateStatus(providerResponse != null
                        ? boundedEvidence(providerResponse.getStatus(), 30)
                        : null)
                .build());
    }

    @Transactional(readOnly = true)
    public boolean isVerifiedIdentityResolutionRetry(Long intentId, String requestedPaymentKey) {
        if (intentId == null || requestedPaymentKey == null || requestedPaymentKey.isBlank()) {
            return false;
        }
        return reconciliationEventRepository
                .findFirstByPaymentIntentIdAndEventTypeOrderByCreatedAtAsc(intentId, EventType.IDENTITY_MISMATCH_HOLD)
                .filter(hold -> requestedPaymentKey.equals(hold.getRequestedPaymentKey()))
                .isPresent()
                && reconciliationEventRepository.existsByPaymentIntentIdAndEventType(
                        intentId, EventType.VERIFIED_PAYMENT_BOUND);
    }

    @Transactional(readOnly = true)
    public boolean hasVerifiedIdentityPaymentBinding(Long intentId) {
        if (intentId == null) {
            return false;
        }
        return reconciliationEventRepository.existsByPaymentIntentIdAndEventType(
                        intentId, EventType.IDENTITY_MISMATCH_HOLD)
                && reconciliationEventRepository.existsByPaymentIntentIdAndEventType(
                        intentId, EventType.VERIFIED_PAYMENT_BOUND);
    }

    @Transactional
    public void markApplicationCreated(PaymentIntent intent) {
        jobScheduler.requireWritable();
        intent.setStatus(PaymentIntent.IntentStatus.APPLICATION_CREATED);
        intent.setFailureCode(null);
        intent.setFailureMessage(null);
        paymentIntentRepository.save(intent);
    }

    @Transactional
    public void compensateAfterApplicationFailure(Long intentId, RuntimeException cause) {
        jobScheduler.requireWritable();
        if (intentId == null) {
            return;
        }
        PaymentIntent intent = paymentIntentRepository.findByIdForUpdate(intentId).orElse(null);
        if (intent == null) {
            return;
        }
        if (intent.getStatus() == PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED) {
            return;
        }
        if (intent.getPaymentKey() == null || intent.getPaymentKey().isBlank()) {
            return;
        }
        if (applicationRepository.findByOrderIdForUpdate(intent.getOrderId()).isPresent()) {
            if (intent.getStatus() != PaymentIntent.IntentStatus.APPLICATION_CREATED) {
                intent.setStatus(PaymentIntent.IntentStatus.APPLICATION_CREATED);
                paymentIntentRepository.save(intent);
            }
            return;
        }
        if (intent.getStatus() == PaymentIntent.IntentStatus.CANCELED
                || intent.getStatus() == PaymentIntent.IntentStatus.APPLICATION_CREATED
                || intent.getStatus() == PaymentIntent.IntentStatus.CANCEL_REQUESTED
                || intent.getStatus() == PaymentIntent.IntentStatus.EXPIRED) {
            return;
        }

        intent.setStatus(PaymentIntent.IntentStatus.CANCEL_REQUESTED);
        intent.setFailureCode(cause != null ? cause.getClass().getSimpleName() : "UNKNOWN_ERROR");
        intent.setFailureMessage(cause != null ? cause.getMessage() : null);
        paymentIntentRepository.save(intent);
        paymentMetricsService.recordCompensationRequested();

        try {
            PaymentIntentCancellationProof.requireFullCancellation(
                    intent.getPaymentKey(),
                    intent.getExpectedAmount(),
                    tossPaymentService.cancelPayment(
                            intent.getPaymentKey(),
                            "파티 신청 생성 실패로 결제를 취소합니다.",
                            intent.getExpectedAmount()));

            intent.setStatus(PaymentIntent.IntentStatus.CANCELED);
            intent.setCanceledAt(Instant.now());
            paymentIntentRepository.save(intent);
            paymentMetricsService.recordCompensation("success");
            log.info("[PaymentIntent] 결제 보상 취소 완료: intentId={}, orderId={}", intent.getId(), intent.getOrderId());
        } catch (RuntimeException e) {
            intent.setStatus(PaymentIntent.IntentStatus.CANCEL_FAILED);
            if (e instanceof TossPaymentException tpEx) {
                intent.setFailureCode(String.valueOf(tpEx.getStatusCode()));
            } else {
                intent.setFailureCode(e.getClass().getSimpleName());
            }
            intent.setFailureMessage(e.getMessage());
            paymentIntentRepository.save(intent);
            paymentMetricsService.recordCompensation("fail");
            enqueueCompensationRetry(intent.getId(), 1);
            log.error("[PaymentIntent] 결제 보상 취소 실패: intentId={}, orderId={}", intent.getId(), intent.getOrderId(), e);
        }
    }

    @Job(name = "Retry Toss Payment Compensation")
    @Transactional
    public void retryCompensation(Long intentId, int attempt) {
        jobScheduler.requireWritable();
        paymentIntentRepository.findByIdForUpdate(intentId).ifPresent(intent -> {
            if (intent.getStatus() == PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED) {
                return;
            }
            if (intent.getStatus() == PaymentIntent.IntentStatus.APPLICATION_CREATED
                    || intent.getStatus() == PaymentIntent.IntentStatus.CANCELED
                    || intent.getStatus() == PaymentIntent.IntentStatus.EXPIRED) {
                return;
            }
            if (attempt > MAX_COMPENSATION_ATTEMPTS) {
                intent.setStatus(PaymentIntent.IntentStatus.CANCEL_FAILED);
                intent.setFailureCode("MAX_RETRY_REACHED");
                intent.setFailureMessage("보상 취소 재시도 횟수 초과");
                paymentIntentRepository.save(intent);
                paymentMetricsService.recordCompensation("fail");
                return;
            }

            if (applicationRepository.findByOrderIdForUpdate(intent.getOrderId()).isPresent()) {
                intent.setStatus(PaymentIntent.IntentStatus.APPLICATION_CREATED);
                paymentIntentRepository.save(intent);
                return;
            }

            if (intent.getPaymentKey() == null || intent.getPaymentKey().isBlank()) {
                return;
            }

            try {
                PaymentIntentCancellationProof.requireFullCancellation(
                        intent.getPaymentKey(),
                        intent.getExpectedAmount(),
                        tossPaymentService.cancelPayment(
                                intent.getPaymentKey(),
                                "보상 재시도: 파티 신청 생성 실패 결제 취소",
                                intent.getExpectedAmount()));

                intent.setStatus(PaymentIntent.IntentStatus.CANCELED);
                intent.setCanceledAt(Instant.now());
                paymentIntentRepository.save(intent);
                paymentMetricsService.recordCompensation("success");
                log.info("[PaymentIntent] 재시도 취소 성공: intentId={}, attempt={}", intentId, attempt);
            } catch (RuntimeException e) {
                intent.setStatus(PaymentIntent.IntentStatus.CANCEL_FAILED);
                if (e instanceof TossPaymentException tpEx) {
                    intent.setFailureCode(String.valueOf(tpEx.getStatusCode()));
                } else {
                    intent.setFailureCode(e.getClass().getSimpleName());
                }
                intent.setFailureMessage(e.getMessage());
                paymentIntentRepository.save(intent);
                paymentMetricsService.recordCompensation("fail");
                if (attempt < MAX_COMPENSATION_ATTEMPTS) {
                    enqueueCompensationRetry(intentId, attempt + 1);
                }
                log.error("[PaymentIntent] 재시도 취소 실패: intentId={}, attempt={}", intentId, attempt, e);
            }
        });
    }

    @Job(name = "Reconcile Toss Payment Intents")
    @Transactional
    public void reconcileCompensationTargets() {
        jobScheduler.requireWritable();
        List<PaymentIntent> targets = paymentIntentRepository.findByStatusInAndUpdatedAtBefore(
                EnumSet.of(
                        PaymentIntent.IntentStatus.CONFIRMED,
                        PaymentIntent.IntentStatus.CANCEL_REQUESTED,
                        PaymentIntent.IntentStatus.CANCEL_FAILED),
                Instant.now().minusSeconds(60));

        for (PaymentIntent intent : targets) {
            try {
                paymentIntentReconciliationService.reconcileSingleIntent(intent.getId());
            } catch (RuntimeException e) {
                log.error("[PaymentIntent] 단건 정합성 보상 중 예외 발생: intentId={}", intent.getId(), e);
            }
        }
    }

    /**
     * 사용자가 직접 요청한 결제 Intent 취소.
     * - PREPARED 상태: Toss API 호출 없이 CANCELED로 변경 (아직 결제 승인 안 됨)
     * - 연결된 신청이 있는 경우: 신청 취소 경로를 사용하도록 충돌 반환 (provider 호출 없음)
     * - CONFIRMED 상태: Toss API 취소 호출 후 CANCELED로 변경
     * - CANCELED 상태: 이미 취소됨 (멱등 처리, 예외 없이 반환)
     * - CANCEL_REQUESTED / CANCEL_FAILED 상태: 409 반환 (진행 중이거나 재시도 필요)
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public PaymentIntent.IntentStatus cancelPaymentIntent(Long intentId, Long userId, String cancelReason) {
        jobScheduler.requireWritable();
        PaymentIntent intent = paymentIntentRepository.findByIdAndApplicantIdForUpdate(intentId, userId)
                .orElseThrow(() -> new TossPaymentException(
                        "PAYMENT_INTENT_NOT_FOUND",
                        "결제 요청을 찾을 수 없습니다: " + intentId,
                        HttpStatus.NOT_FOUND));

        if (intent.getStatus() == PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED) {
            throw reconciliationRequiredException();
        }

        if (intent.getStatus() == PaymentIntent.IntentStatus.APPLICATION_CREATED
                || applicationRepository.findByOrderIdForUpdate(intent.getOrderId()).isPresent()) {
            throw new TossPaymentException(
                    "APPLICATION_CANCELLATION_REQUIRED",
                    "신청 생성된 결제는 신청 취소 경로를 이용해주세요.",
                    HttpStatus.CONFLICT);
        }

        // 멱등: 이미 취소된 경우 재시도 허용
        if (intent.getStatus() == PaymentIntent.IntentStatus.CANCELED) {
            log.info("[PaymentIntent] 이미 취소된 결제 의도: intentId={}", intentId);
            return PaymentIntent.IntentStatus.CANCELED;
        }

        // 취소 처리 중이거나 실패한 경우
        if (intent.getStatus() == PaymentIntent.IntentStatus.CANCEL_REQUESTED) {
            throw new TossPaymentException("PAYMENT_CANCEL_ALREADY_REQUESTED", "결제 취소가 이미 진행 중입니다.", HttpStatus.CONFLICT);
        }
        if (intent.getStatus() == PaymentIntent.IntentStatus.CANCEL_FAILED) {
            throw new TossPaymentException(
                    "PAYMENT_CANCEL_PREVIOUS_FAILURE",
                    "이전 결제 취소가 실패했습니다. 고객센터에 문의해주세요.",
                    HttpStatus.CONFLICT);
        }
        if (intent.getStatus() == PaymentIntent.IntentStatus.EXPIRED) {
            throw new TossPaymentException("PAYMENT_INTENT_EXPIRED", "만료된 결제 요청입니다.", HttpStatus.CONFLICT);
        }

        String reason = (cancelReason != null && !cancelReason.isBlank())
                ? cancelReason
                : "사용자 요청에 의한 결제 취소";

        if (intent.getStatus() == PaymentIntent.IntentStatus.PREPARED
                && CONFIRMATION_IN_PROGRESS_CODE.equals(intent.getFailureCode())) {
            throw new TossPaymentException(
                    "PAYMENT_CONFIRMATION_IN_PROGRESS",
                    "결제 승인 결과를 확인 중입니다. 같은 결제 키로 다시 확인해주세요.",
                    HttpStatus.CONFLICT);
        }

        // PREPARED 상태: Toss 결제 승인 전이므로 DB 상태만 변경
        if (intent.getStatus() == PaymentIntent.IntentStatus.PREPARED) {
            intent.setStatus(PaymentIntent.IntentStatus.CANCELED);
            intent.setCanceledAt(Instant.now());
            intent.setFailureMessage(reason);
            paymentIntentRepository.save(intent);
            paymentMetricsService.recordCompensation("user_cancel_prepared");
            log.info("[PaymentIntent] PREPARED 상태 취소 완료 (Toss 호출 불필요): intentId={}", intentId);
            return PaymentIntent.IntentStatus.CANCELED;
        }

        // CONFIRMED 상태: Toss API 취소 필요
        if (intent.getPaymentKey() == null || intent.getPaymentKey().isBlank()) {
            throw new TossPaymentException(
                    "PAYMENT_KEY_MISSING",
                    "결제 키 정보를 찾을 수 없습니다. 고객센터에 문의해주세요.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        intent.setStatus(PaymentIntent.IntentStatus.CANCEL_REQUESTED);
        intent.setFailureMessage(reason);
        paymentIntentRepository.save(intent);

        try {
            PaymentIntentCancellationProof.requireFullCancellation(
                    intent.getPaymentKey(),
                    intent.getExpectedAmount(),
                    tossPaymentService.cancelPayment(intent.getPaymentKey(), reason, intent.getExpectedAmount()));

            intent.setStatus(PaymentIntent.IntentStatus.CANCELED);
            intent.setCanceledAt(Instant.now());
            paymentIntentRepository.save(intent);
            paymentMetricsService.recordCompensation("user_cancel_confirmed");
            log.info("[PaymentIntent] CONFIRMED 상태 취소 완료: intentId={}", intentId);
            return PaymentIntent.IntentStatus.CANCELED;

        } catch (RuntimeException e) {
            intent.setStatus(PaymentIntent.IntentStatus.CANCEL_FAILED);
            if (e instanceof TossPaymentException tpEx) {
                intent.setFailureCode(String.valueOf(tpEx.getStatusCode()));
            } else {
                intent.setFailureCode(e.getClass().getSimpleName());
            }
            intent.setFailureMessage(e.getMessage());
            paymentIntentRepository.save(intent);
            paymentMetricsService.recordCompensation("user_cancel_fail");
            log.error("[PaymentIntent] 사용자 요청 취소 실패: intentId={}", intentId, e);
            throw e;
        }
    }

    private void requireApplicationMatchesIntent(PartyApplication application, PaymentIntent intent) {
        if (!java.util.Objects.equals(application.getOrderId(), intent.getOrderId())
                || !java.util.Objects.equals(application.getApplicantId(), intent.getApplicantId())
                || !java.util.Objects.equals(application.getPartyId(), intent.getPartyId())
                || (application.getPaymentKey() != null
                        && !application.getPaymentKey().isBlank()
                        && !java.util.Objects.equals(application.getPaymentKey(), intent.getPaymentKey()))
                || (application.getDepositAmount() != null
                        && !java.util.Objects.equals(application.getDepositAmount(), intent.getExpectedAmount()))) {
            throw new InvalidApplicationStatusException("결제 요청과 신청 정보가 일치하지 않습니다.");
        }
    }

    private TossPaymentException reconciliationRequiredException() {
        return new TossPaymentException(
                "PAYMENT_INTENT_RECONCILIATION_REQUIRED",
                "결제 승인 응답 대사가 필요합니다. 고객센터에 문의해주세요.",
                HttpStatus.CONFLICT);
    }

    private String boundedEvidence(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength - 3) + "...";
    }

    public Long resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new UnauthorizedAccessException("인증 정보가 없습니다.");
        }
        String principalName = principal.getName();
        try {
            return Long.valueOf(principalName);
        } catch (NumberFormatException ignored) {
            return userService.getUserIdByEmail(principalName);
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new UnauthorizedAccessException("인증 정보가 없습니다.");
        }
    }

    private Optional<PaymentIntent> findIntentForUpdate(TossPaymentDTO.ClientConfirmRequest request, Long applicantId) {
        if (request.getIntentId() != null) {
            Optional<PaymentIntent> intent = paymentIntentRepository.findByIdAndApplicantIdForUpdate(
                    request.getIntentId(),
                    applicantId);
            if (intent.isPresent()) {
                return intent;
            }
        }
        return paymentIntentRepository.findByOrderIdAndApplicantIdForUpdate(request.getOrderId(), applicantId);
    }

    private PaymentIntent createLegacyIntent(TossPaymentDTO.ClientConfirmRequest request, Long applicantId) {
        if (request.getPartyId() == null) {
            throw new InvalidApplicationStatusException("partyId는 필수입니다.");
        }
        Optional<PaymentIntent> existing = paymentIntentRepository.findByOrderIdAndApplicantIdForUpdate(
                request.getOrderId(),
                applicantId);
        if (existing.isPresent()) {
            return existing.get();
        }
        PaymentFlowType flowType = request.getFlowType() != null ? request.getFlowType() : PaymentFlowType.DEPOSIT;
        validateApplicationPreconditions(request.getPartyId(), applicantId, flowType);
        PaymentAmountCalculator.AmountInfo amountInfo = paymentAmountCalculator.calculateAmount(request.getPartyId(), flowType);

        PaymentIntent legacyIntent = PaymentIntent.builder()
                .orderId(request.getOrderId())
                .partyId(request.getPartyId())
                .applicantId(applicantId)
                .expectedAmount(amountInfo.amount())
                .currency(amountInfo.currency())
                .flowType(flowType)
                .paymentType(resolvePaymentType(flowType))
                .cancelPolicyVersion(request.getCancelPolicyVersion())
                .mode(PaymentIntent.IntentMode.LEGACY)
                .status(PaymentIntent.IntentStatus.PREPARED)
                .expiresAt(Instant.now().plusSeconds(intentTtlMinutes * 60))
                .build();

        return paymentIntentRepository.save(legacyIntent);
    }

    private void validateApplicationPreconditions(Long partyId, Long applicantId, PaymentFlowType flowType) {
        if (requireSocialVerification && !userService.isSocialVerified(applicantId)) {
            throw new com.example.common.exception.IdentityVerificationRequiredException(
                    "메이트에 신청하려면 카카오 또는 네이버 계정 연동이 필요합니다.");
        }

        Party party = partyRepository.findByIdForUpdate(partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        PaymentFlowType resolvedFlowType = flowType != null ? flowType : PaymentFlowType.DEPOSIT;
        boolean eligible = switch (resolvedFlowType) {
            case DEPOSIT -> party.getStatus() == Party.PartyStatus.PENDING;
            case SELLING_FULL -> party.getStatus() == Party.PartyStatus.SELLING;
        };
        if (!eligible) {
            throw new InvalidApplicationStatusException(resolvedFlowType == PaymentFlowType.SELLING_FULL
                    ? "SELLING 상태 파티만 전액 결제를 진행할 수 있습니다."
                    : "모집 중인 파티만 예약금 결제를 진행할 수 있습니다.");
        }

        applicationRepository.findByPartyIdAndApplicantId(partyId, applicantId)
                .ifPresent(app -> {
                    throw new DuplicateApplicationException(partyId, applicantId);
                });

        if (applicationRepository.existsByPartyIdAndApplicantIdAndIsRejectedTrue(partyId, applicantId)) {
            throw new InvalidApplicationStatusException("거절된 파티에 다시 신청할 수 없습니다.");
        }

        long pendingCount = applicationRepository.countByPartyIdAndIsApprovedFalseAndIsRejectedFalse(partyId);
        if (pendingCount >= MAX_PENDING_APPLICATIONS) {
            throw new InvalidApplicationStatusException("이 파티의 대기 중인 신청이 최대(10건)에 도달했습니다.");
        }

        if (party.getCurrentParticipants() >= party.getMaxParticipants()) {
            throw new PartyFullException(partyId);
        }
    }

    private void enqueueCompensationRetry(Long intentId, int attempt) {
        long delaySeconds = Math.min(3600L, (long) Math.pow(2, Math.max(0, attempt - 1)) * 60L);
        jobScheduler.schedule(Instant.now().plusSeconds(delaySeconds), () -> retryCompensation(intentId, attempt));
    }

    private String generateOrderId(Long partyId, Long applicantId) {
        return "MATE-" + partyId + "-" + applicantId + "-" + System.currentTimeMillis();
    }

    private PartyApplication.PaymentType resolvePaymentType(PaymentFlowType flowType) {
        if (flowType == PaymentFlowType.SELLING_FULL) {
            return PartyApplication.PaymentType.FULL;
        }
        return PartyApplication.PaymentType.DEPOSIT;
    }
}
