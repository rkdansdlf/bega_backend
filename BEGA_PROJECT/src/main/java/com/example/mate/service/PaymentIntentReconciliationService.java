package com.example.mate.service;

import com.example.mate.entity.PaymentIntent;
import com.example.mate.entity.PaymentIntent.IntentStatus;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import com.example.common.jobs.JobSubmissionGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIntentReconciliationService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final PartyApplicationRepository applicationRepository;
    private final TossPaymentService tossPaymentService;
    private final PaymentMetricsService paymentMetricsService;
    private final JobSubmissionGateway jobScheduler;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileSingleIntent(Long intentId) {
        jobScheduler.requireWritable();
        PaymentIntent intent = paymentIntentRepository.findByIdForUpdate(intentId).orElse(null);
        if (intent == null) {
            return;
        }
        if (intent.getStatus() == IntentStatus.RECONCILIATION_REQUIRED) {
            return;
        }
        try {
            if (applicationRepository.findByOrderId(intent.getOrderId()).isPresent()) {
                intent.setStatus(IntentStatus.APPLICATION_CREATED);
                paymentIntentRepository.save(intent);
                return;
            }
            if (intent.getPaymentKey() == null || intent.getPaymentKey().isBlank()) {
                return;
            }
            PaymentIntentCancellationProof.requireFullCancellation(
                    intent.getPaymentKey(),
                    intent.getExpectedAmount(),
                    tossPaymentService.cancelPayment(
                            intent.getPaymentKey(),
                            "정합성 점검: 미완료 신청 결제 취소",
                            intent.getExpectedAmount()));

            intent.setStatus(IntentStatus.CANCELED);
            intent.setCanceledAt(Instant.now());
            paymentIntentRepository.save(intent);
            paymentMetricsService.recordCompensation("success");
        } catch (RuntimeException e) {
            intent.setStatus(IntentStatus.CANCEL_FAILED);
            intent.setFailureCode(e.getClass().getSimpleName());
            intent.setFailureMessage(e.getMessage());
            paymentIntentRepository.save(intent);
            paymentMetricsService.recordCompensation("fail");
            enqueueCompensationRetry(intent.getId(), 1);
        }
    }

    @Job(name = "Reconcile Toss Payment Intents - Retry Compensation")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryCompensation(Long intentId, int attempt) {
        jobScheduler.requireWritable();
        paymentIntentRepository.findByIdForUpdate(intentId).ifPresent(intent -> {
            if (intent.getStatus() == IntentStatus.APPLICATION_CREATED
                    || intent.getStatus() == IntentStatus.CANCELED
                    || intent.getStatus() == IntentStatus.RECONCILIATION_REQUIRED
                    || intent.getStatus() == IntentStatus.EXPIRED) {
                return;
            }
            if (attempt > 5) {
                intent.setStatus(IntentStatus.CANCEL_FAILED);
                intent.setFailureCode("MAX_RETRY_REACHED");
                intent.setFailureMessage("보상 취소 재시도 횟수 초과");
                paymentIntentRepository.save(intent);
                paymentMetricsService.recordCompensation("fail");
                return;
            }

            if (applicationRepository.findByOrderId(intent.getOrderId()).isPresent()) {
                intent.setStatus(IntentStatus.APPLICATION_CREATED);
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

                intent.setStatus(IntentStatus.CANCELED);
                intent.setCanceledAt(Instant.now());
                paymentIntentRepository.save(intent);
                paymentMetricsService.recordCompensation("success");
                log.info("[PaymentIntent] 재시도 취소 성공: intentId={}, attempt={}", intentId, attempt);
            } catch (RuntimeException e) {
                intent.setStatus(IntentStatus.CANCEL_FAILED);
                if (e instanceof TossPaymentException tpEx) {
                    intent.setFailureCode(String.valueOf(tpEx.getStatusCode()));
                } else {
                    intent.setFailureCode(e.getClass().getSimpleName());
                }
                intent.setFailureMessage(e.getMessage());
                paymentIntentRepository.save(intent);
                paymentMetricsService.recordCompensation("fail");
                if (attempt < 5) {
                    enqueueCompensationRetry(intentId, attempt + 1);
                }
                log.error("[PaymentIntent] 재시도 취소 실패: intentId={}, attempt={}", intentId, attempt, e);
            }
        });
    }

    private void enqueueCompensationRetry(Long intentId, int attempt) {
        long delaySeconds = Math.min(3600L, (long) Math.pow(2, Math.max(0, attempt - 1)) * 60L);
        jobScheduler.schedule(
                Instant.now().plusSeconds(delaySeconds),
                () -> retryCompensation(intentId, attempt));
    }

}
