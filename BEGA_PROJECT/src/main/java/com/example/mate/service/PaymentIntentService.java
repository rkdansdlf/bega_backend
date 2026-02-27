package com.example.mate.service;

import com.example.auth.service.UserService;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.exception.DuplicateApplicationException;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.exception.PartyFullException;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.exception.UnauthorizedAccessException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIntentService {

    private static final int MAX_PENDING_APPLICATIONS = 10;
    private static final int MAX_COMPENSATION_ATTEMPTS = 5;

    private final PaymentIntentRepository paymentIntentRepository;
    private final PartyApplicationRepository applicationRepository;
    private final PartyRepository partyRepository;
    private final PaymentAmountCalculator paymentAmountCalculator;
    private final TossPaymentService tossPaymentService;
    private final JobScheduler jobScheduler;
    private final PaymentMetricsService paymentMetricsService;
    private final PaymentIntentReconciliationService paymentIntentReconciliationService;
    private final UserService userService;

    @Value("${mate.payment.intent-ttl-minutes:30}")
    private long intentTtlMinutes;

    @Value("${mate.auth.require-social-verification:true}")
    private boolean requireSocialVerification;

    @Transactional
    public TossPaymentDTO.PrepareResponse prepareIntent(TossPaymentDTO.PrepareClientRequest request, Principal principal) {
        if (request == null || request.getPartyId() == null) {
            throw new InvalidApplicationStatusException("partyId는 필수입니다.");
        }

        PaymentFlowType flowType = request.getFlowType() != null
                ? request.getFlowType()
                : PaymentFlowType.DEPOSIT;
        Long applicantId = resolveUserId(principal);
        validateApplicationPreconditions(request.getPartyId(), applicantId);

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
        if (request == null || request.getOrderId() == null || request.getOrderId().isBlank()) {
            throw new InvalidApplicationStatusException("orderId는 필수입니다.");
        }
        if (request.getPaymentKey() == null || request.getPaymentKey().isBlank()) {
            throw new InvalidApplicationStatusException("paymentKey는 필수입니다.");
        }

        Long applicantId = resolveUserId(principal);

        PaymentIntent intent = findIntentForUpdate(request)
                .orElseGet(() -> createLegacyIntent(request, applicantId));

        if (!intent.getApplicantId().equals(applicantId)) {
            throw new UnauthorizedAccessException("본인의 결제 요청만 처리할 수 있습니다.");
        }
        if (request.getPartyId() != null && !request.getPartyId().equals(intent.getPartyId())) {
            throw new InvalidApplicationStatusException("결제 요청의 partyId가 일치하지 않습니다.");
        }
        if (request.getFlowType() != null && intent.getFlowType() != null
                && request.getFlowType() != intent.getFlowType()) {
            throw new InvalidApplicationStatusException("결제 flowType이 준비 정보와 일치하지 않습니다.");
        }

        if (intent.getStatus() == PaymentIntent.IntentStatus.APPLICATION_CREATED
                || applicationRepository.findByOrderIdForUpdate(intent.getOrderId()).isPresent()) {
            intent.setStatus(PaymentIntent.IntentStatus.APPLICATION_CREATED);
            paymentIntentRepository.save(intent);
            return intent;
        }

        if (intent.getStatus() == PaymentIntent.IntentStatus.CANCELED
                || intent.getStatus() == PaymentIntent.IntentStatus.CANCEL_REQUESTED
                || intent.getStatus() == PaymentIntent.IntentStatus.CANCEL_FAILED
                || intent.getStatus() == PaymentIntent.IntentStatus.EXPIRED) {
            throw new InvalidApplicationStatusException("이미 종료된 결제 요청입니다. 새로 결제를 시도해주세요.");
        }

        if (intent.getExpiresAt() != null && intent.getExpiresAt().isBefore(Instant.now())) {
            intent.setStatus(PaymentIntent.IntentStatus.EXPIRED);
            paymentIntentRepository.save(intent);
            throw new InvalidApplicationStatusException("결제 요청 유효시간이 만료되었습니다. 다시 시도해주세요.");
        }

        validateApplicationPreconditions(intent.getPartyId(), applicantId);
        PaymentAmountCalculator.AmountInfo amountInfo = paymentAmountCalculator.calculateAmount(
                intent.getPartyId(),
                intent.getFlowType());
        if (!amountInfo.amount().equals(intent.getExpectedAmount())) {
            throw new InvalidApplicationStatusException("결제 금액이 변경되었습니다. 다시 결제를 시도해주세요.");
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

    @Transactional
    public void markConfirmed(PaymentIntent intent, String paymentKey) {
        if (intent.getStatus() == PaymentIntent.IntentStatus.APPLICATION_CREATED) {
            return;
        }
        intent.setStatus(PaymentIntent.IntentStatus.CONFIRMED);
        intent.setPaymentKey(paymentKey);
        intent.setConfirmedAt(Instant.now());
        paymentIntentRepository.save(intent);
    }

    @Transactional
    public void markApplicationCreated(PaymentIntent intent) {
        intent.setStatus(PaymentIntent.IntentStatus.APPLICATION_CREATED);
        paymentIntentRepository.save(intent);
    }

    @Transactional
    public void compensateAfterApplicationFailure(Long intentId, RuntimeException cause) {
        if (intentId == null) {
            return;
        }
        PaymentIntent intent = paymentIntentRepository.findByIdForUpdate(intentId).orElse(null);
        if (intent == null) {
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
            tossPaymentService.cancelPayment(
                    intent.getPaymentKey(),
                    "파티 신청 생성 실패로 결제를 취소합니다.",
                    intent.getExpectedAmount());

            intent.setStatus(PaymentIntent.IntentStatus.CANCELED);
            intent.setCanceledAt(Instant.now());
            paymentIntentRepository.save(intent);
            paymentMetricsService.recordCompensation("success");
            log.info("[PaymentIntent] 결제 보상 취소 완료: intentId={}, orderId={}", intent.getId(), intent.getOrderId());
        } catch (RuntimeException e) {
            if (isAlreadyCancelledByProvider(e)) {
                intent.setStatus(PaymentIntent.IntentStatus.CANCELED);
                intent.setCanceledAt(Instant.now());
                paymentIntentRepository.save(intent);
                paymentMetricsService.recordCompensation("success");
                log.info("[PaymentIntent] 보상 취소가 이미 반영되어 완료 처리: intentId={}, orderId={}",
                        intent.getId(), intent.getOrderId());
                return;
            }

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
        paymentIntentRepository.findByIdForUpdate(intentId).ifPresent(intent -> {
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
                tossPaymentService.cancelPayment(
                        intent.getPaymentKey(),
                        "보상 재시도: 파티 신청 생성 실패 결제 취소",
                        intent.getExpectedAmount());

                intent.setStatus(PaymentIntent.IntentStatus.CANCELED);
                intent.setCanceledAt(Instant.now());
                paymentIntentRepository.save(intent);
                paymentMetricsService.recordCompensation("success");
                log.info("[PaymentIntent] 재시도 취소 성공: intentId={}, attempt={}", intentId, attempt);
            } catch (RuntimeException e) {
                if (isAlreadyCancelledByProvider(e)) {
                    intent.setStatus(PaymentIntent.IntentStatus.CANCELED);
                    intent.setCanceledAt(Instant.now());
                    paymentIntentRepository.save(intent);
                    paymentMetricsService.recordCompensation("success");
                    log.info("[PaymentIntent] 보상 재시도에서 이미 취소됨으로 처리: intentId={}, attempt={}", intentId, attempt);
                    return;
                }

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
     * - CONFIRMED / APPLICATION_CREATED 상태: Toss API 취소 호출 후 CANCELED로 변경
     * - CANCELED 상태: 이미 취소됨 (멱등 처리, 예외 없이 반환)
     * - CANCEL_REQUESTED / CANCEL_FAILED 상태: 409 반환 (진행 중이거나 재시도 필요)
     */
    @Transactional
    public PaymentIntent.IntentStatus cancelPaymentIntent(Long intentId, Long userId, String cancelReason) {
        PaymentIntent intent = paymentIntentRepository.findByIdForUpdate(intentId)
                .orElseThrow(() -> new TossPaymentException("결제 요청을 찾을 수 없습니다: " + intentId, HttpStatus.NOT_FOUND));

        if (!intent.getApplicantId().equals(userId)) {
            throw new UnauthorizedAccessException("본인의 결제 요청만 취소할 수 있습니다.");
        }

        // 멱등: 이미 취소된 경우 재시도 허용
        if (intent.getStatus() == PaymentIntent.IntentStatus.CANCELED) {
            log.info("[PaymentIntent] 이미 취소된 결제 의도: intentId={}", intentId);
            return PaymentIntent.IntentStatus.CANCELED;
        }

        // 취소 처리 중이거나 실패한 경우
        if (intent.getStatus() == PaymentIntent.IntentStatus.CANCEL_REQUESTED) {
            throw new TossPaymentException("결제 취소가 이미 진행 중입니다.", HttpStatus.CONFLICT);
        }
        if (intent.getStatus() == PaymentIntent.IntentStatus.CANCEL_FAILED) {
            throw new TossPaymentException("이전 결제 취소가 실패했습니다. 고객센터에 문의해주세요.", HttpStatus.CONFLICT);
        }
        if (intent.getStatus() == PaymentIntent.IntentStatus.EXPIRED) {
            throw new TossPaymentException("만료된 결제 요청입니다.", HttpStatus.CONFLICT);
        }

        String reason = (cancelReason != null && !cancelReason.isBlank())
                ? cancelReason
                : "사용자 요청에 의한 결제 취소";

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

        // CONFIRMED / APPLICATION_CREATED 상태: Toss API 취소 필요
        if (intent.getPaymentKey() == null || intent.getPaymentKey().isBlank()) {
            throw new TossPaymentException(
                    "결제 키 정보를 찾을 수 없습니다. 고객센터에 문의해주세요.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        intent.setStatus(PaymentIntent.IntentStatus.CANCEL_REQUESTED);
        intent.setFailureMessage(reason);
        paymentIntentRepository.save(intent);

        try {
            tossPaymentService.cancelPayment(intent.getPaymentKey(), reason, intent.getExpectedAmount());

            intent.setStatus(PaymentIntent.IntentStatus.CANCELED);
            intent.setCanceledAt(Instant.now());
            paymentIntentRepository.save(intent);
            paymentMetricsService.recordCompensation("user_cancel_confirmed");
            log.info("[PaymentIntent] CONFIRMED 상태 취소 완료: intentId={}, paymentKey={}", intentId, intent.getPaymentKey());
            return PaymentIntent.IntentStatus.CANCELED;

        } catch (RuntimeException e) {
            if (isAlreadyCancelledByProvider(e)) {
                intent.setStatus(PaymentIntent.IntentStatus.CANCELED);
                intent.setCanceledAt(Instant.now());
                paymentIntentRepository.save(intent);
                paymentMetricsService.recordCompensation("user_cancel_already_done");
                log.info("[PaymentIntent] Toss에서 이미 취소된 결제를 완료 처리: intentId={}", intentId);
                return PaymentIntent.IntentStatus.CANCELED;
            }

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

    public Long resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new UnauthorizedAccessException("인증 정보가 없습니다.");
        }
        return userService.getUserIdByEmail(principal.getName());
    }

    private Optional<PaymentIntent> findIntentForUpdate(TossPaymentDTO.ClientConfirmRequest request) {
        if (request.getIntentId() != null) {
            Optional<PaymentIntent> intent = paymentIntentRepository.findByIdForUpdate(request.getIntentId());
            if (intent.isPresent()) {
                return intent;
            }
        }
        return paymentIntentRepository.findByOrderIdForUpdate(request.getOrderId());
    }

    private PaymentIntent createLegacyIntent(TossPaymentDTO.ClientConfirmRequest request, Long applicantId) {
        if (request.getPartyId() == null) {
            throw new InvalidApplicationStatusException("partyId는 필수입니다.");
        }
        Optional<PaymentIntent> existing = paymentIntentRepository.findByOrderIdForUpdate(request.getOrderId());
        if (existing.isPresent()) {
            return existing.get();
        }
        validateApplicationPreconditions(request.getPartyId(), applicantId);

        PaymentFlowType flowType = request.getFlowType() != null ? request.getFlowType() : PaymentFlowType.DEPOSIT;
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

    private void validateApplicationPreconditions(Long partyId, Long applicantId) {
        if (requireSocialVerification && !userService.isSocialVerified(applicantId)) {
            throw new com.example.common.exception.IdentityVerificationRequiredException(
                    "메이트에 신청하려면 카카오 또는 네이버 계정 연동이 필요합니다.");
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

        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
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

    private boolean isAlreadyCancelledByProvider(RuntimeException ex) {
        if (ex instanceof TossPaymentException tossEx) {
            // 1순위: Toss API가 응답한 정확한 에러 코드로 판별
            String tossErrorCode = tossEx.getTossErrorCode();
            if (tossErrorCode != null) {
                return "ALREADY_CANCELED_PAYMENT".equals(tossErrorCode)
                        || "ALREADY_FULLY_CANCELED".equals(tossErrorCode)
                        || "PAYMENT_ALREADY_CANCELED".equals(tossErrorCode);
            }

            // 2순위: 에러 코드가 없는 경우(구버전 호환), HTTP 상태 + 명확한 키워드 조합으로만 판별
            // 주의: "취소" 단어는 TossPaymentService 내부 메시지에도 포함되므로 사용하지 않음
            HttpStatus status = tossEx.getStatusCode() instanceof HttpStatus ? (HttpStatus) tossEx.getStatusCode() : null;
            boolean conflictStatus = status == HttpStatus.CONFLICT || status == HttpStatus.NOT_FOUND;
            String message = tossEx.getMessage() == null ? "" : tossEx.getMessage().toLowerCase();
            boolean alreadyCancelledByMessage = message.contains("already")
                    || message.contains("이미 취소된")
                    || message.contains("already canceled")
                    || message.contains("already cancelled");
            if (conflictStatus && alreadyCancelledByMessage) {
                return true;
            }
        }

        return false;
    }

    private PartyApplication.PaymentType resolvePaymentType(PaymentFlowType flowType) {
        if (flowType == PaymentFlowType.SELLING_FULL) {
            return PartyApplication.PaymentType.FULL;
        }
        return PartyApplication.PaymentType.DEPOSIT;
    }
}
