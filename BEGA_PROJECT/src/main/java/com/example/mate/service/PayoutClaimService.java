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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PayoutClaimService {

    private static final int MAX_PAYOUT_RETRY_ATTEMPTS = 5;
    private static final long PROVIDER_CALL_LEASE_SECONDS = 180;
    private static final String SNAPSHOT_CLAIM_PROTOCOL = "SNAPSHOT_V1";

    private final PayoutTransactionRepository payoutTransactionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PartyApplicationRepository partyApplicationRepository;
    private final PartyRepository partyRepository;
    private final SellerRecoveryService sellerRecoveryService;
    private final PaymentMetricsService metricsService;
    private final AccountDeletionFinancialService accountDeletionFinancialService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimedPayout claimInitial(Long paymentTransactionId, boolean payoutEnabled) {
        PaymentTransaction candidate = paymentTransactionRepository.findById(paymentTransactionId)
                .orElseThrow(() -> new IllegalStateException(
                        "결제 트랜잭션을 찾을 수 없습니다: " + paymentTransactionId));
        accountDeletionFinancialService.lockSellerForPayoutClaim(candidate.getSellerUserId());
        Party party = lockParty(candidate.getPartyId());
        PaymentTransaction payment = lockPayment(paymentTransactionId);
        if (!Objects.equals(candidate.getSellerUserId(), payment.getSellerUserId())) {
            throw new PayoutEligibilityException("결제 판매자 정보가 변경되어 지급 자격을 확인할 수 없습니다.");
        }
        boolean partyLinkUnchanged = Objects.equals(candidate.getPartyId(), payment.getPartyId());
        PayoutTransaction payout = payoutTransactionRepository
                .findTopByPaymentTransactionIdForUpdateOrderByIdDesc(payment.getId())
                .orElseGet(() -> PayoutTransaction.builder()
                        .paymentTransactionId(payment.getId())
                        .sellerId(payment.getSellerUserId())
                        .requestedAmount(payment.getNetAmount())
                        .status(SettlementStatus.PENDING)
                        .build());

        if (payout.getStatus() == SettlementStatus.COMPLETED
                || payout.getStatus() == SettlementStatus.REQUESTED
                || payout.getStatus() == SettlementStatus.FAILED
                || payout.getStatus() == SettlementStatus.SKIPPED) {
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        if (payment.getPaymentStatus() != PaymentStatus.PAID) {
            markSkipped(payment, payout, "PAYMENT_NOT_PAYABLE", false);
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        PartyApplication application = payment.getApplicationId() == null
                ? null
                : partyApplicationRepository.findById(payment.getApplicationId()).orElse(null);
        validateLinkedApplicationEligibility(payment, application);
        if (!partyLinkUnchanged
                || party == null
                || !Objects.equals(party.getId(), payment.getPartyId())
                || party.getStatus() == Party.PartyStatus.FAILED) {
            return persistManualReconciliationHold(
                    payment,
                    payout,
                    "파티가 만료되었거나 연결을 확인할 수 없어 지급을 보류합니다. 운영자 수동 대사가 필요합니다.");
        }

        if (!payoutEnabled) {
            markSkipped(payment, payout, "PAYMENT_PAYOUT_DISABLED", true);
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }

        if (payout.getId() == null) {
            payout = payoutTransactionRepository.saveAndFlush(payout);
        }
        int payoutAmount = reserveRecoveryOffset(payment, payout);
        if (payoutAmount == 0) {
            completeWithRecoveryOffset(payment, payout);
            accountDeletionFinancialService.clearFallbackIfNoUnboundPayouts(payment.getSellerUserId());
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        persistDurableRequestClaim(payment, payout, payoutAmount);
        return new ClaimedPayout(payment, payout, ClaimAction.CALL_PROVIDER);
    }

    public static void validateLinkedApplicationEligibility(
            PaymentTransaction payment,
            PartyApplication application) {
        if (payment == null
                || application == null
                || payment.getApplicationId() == null
                || payment.getPartyId() == null
                || payment.getBuyerUserId() == null
                || payment.getOrderId() == null
                || payment.getOrderId().isBlank()
                || !Objects.equals(payment.getApplicationId(), application.getId())
                || !Objects.equals(payment.getPartyId(), application.getPartyId())
                || !Objects.equals(payment.getBuyerUserId(), application.getApplicantId())
                || !Objects.equals(payment.getOrderId(), application.getOrderId())
                || !Boolean.TRUE.equals(application.getIsApproved())
                || Boolean.TRUE.equals(application.getIsRejected())) {
            throw new PayoutEligibilityException("승인된 결제 신청의 지급 자격을 확인할 수 없습니다.");
        }
    }

    public static final class PayoutEligibilityException extends IllegalStateException {
        public PayoutEligibilityException(String message) {
            super(message);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimedPayout claimRetry(Long payoutId, boolean payoutEnabled) {
        PayoutTransaction candidate = payoutTransactionRepository.findById(payoutId).orElse(null);
        if (candidate == null) {
            return ClaimedPayout.none();
        }
        PaymentTransaction paymentCandidate = paymentTransactionRepository
                .findById(candidate.getPaymentTransactionId())
                .orElseThrow(() -> new IllegalStateException(
                        "결제 트랜잭션을 찾을 수 없습니다: " + candidate.getPaymentTransactionId()));
        accountDeletionFinancialService.lockSellerForPayoutClaim(paymentCandidate.getSellerUserId());
        Party party = lockParty(paymentCandidate.getPartyId());
        PaymentTransaction payment = lockPayment(candidate.getPaymentTransactionId());
        if (!Objects.equals(paymentCandidate.getSellerUserId(), payment.getSellerUserId())) {
            throw new PayoutEligibilityException("결제 판매자 정보가 변경되어 지급 자격을 확인할 수 없습니다.");
        }
        boolean partyLinkUnchanged = Objects.equals(paymentCandidate.getPartyId(), payment.getPartyId());
        PayoutTransaction payout = payoutTransactionRepository.findByIdForUpdate(payoutId).orElse(null);
        if (payout == null || payout.getStatus() != SettlementStatus.REQUESTED) {
            return ClaimedPayout.none();
        }
        if (!Objects.equals(payout.getPaymentTransactionId(), payment.getId())) {
            throw new PayoutEligibilityException("지급 트랜잭션과 결제 트랜잭션 연결이 일치하지 않습니다.");
        }
        if (PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE.equals(payout.getFailureCode())) {
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        if (!partyLinkUnchanged
                || party == null
                || !Objects.equals(party.getId(), payment.getPartyId())
                || party.getStatus() == Party.PartyStatus.FAILED) {
            return persistManualReconciliationHold(
                    payment,
                    payout,
                    "파티가 만료되었거나 연결을 확인할 수 없어 지급 재시도를 보류합니다. 운영자 수동 대사가 필요합니다.");
        }
        if (!SNAPSHOT_CLAIM_PROTOCOL.equals(payout.getClaimProtocol())) {
            payout.setFailureCode("PAYOUT_LEGACY_SNAPSHOT_RECONCILIATION_REQUIRED");
            payout.setFailReason("provider snapshot이 없는 레거시 지급 건은 운영자 수동 조정이 필요합니다.");
            payout.setNextRetryAt(null);
            payoutTransactionRepository.saveAndFlush(payout);
            payment.setSettlementStatus(SettlementStatus.REQUESTED);
            paymentTransactionRepository.save(payment);
            metricsService.recordPayout("manual_reconciliation");
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        if (Objects.requireNonNullElse(payout.getRetryCount(), 0) >= MAX_PAYOUT_RETRY_ATTEMPTS) {
            payout.setStatus(SettlementStatus.REQUESTED);
            payout.setFailureCode("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
            payout.setFailReason("지급 결과를 자동 확인하지 못했습니다. 운영자 수동 조정이 필요합니다.");
            payout.setNextRetryAt(null);
            payoutTransactionRepository.save(payout);
            payment.setSettlementStatus(SettlementStatus.REQUESTED);
            paymentTransactionRepository.save(payment);
            metricsService.recordPayout("manual_reconciliation");
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        if (!payoutEnabled) {
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        if (payout.getNextRetryAt() != null && payout.getNextRetryAt().isAfter(Instant.now())) {
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        if (payout.getStatus() == SettlementStatus.REQUESTED
                && payout.getProviderRef() != null
                && !payout.getProviderRef().isBlank()) {
            Instant now = Instant.now();
            payout.setLastRetryAt(now);
            payout.setNextRetryAt(now.plusSeconds(PROVIDER_CALL_LEASE_SECONDS));
            payoutTransactionRepository.saveAndFlush(payout);
            return new ClaimedPayout(payment, payout, ClaimAction.POLL_PROVIDER);
        }
        if (payment.getPaymentStatus() != PaymentStatus.PAID) {
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }

        int payoutAmount = reserveRecoveryOffset(payment, payout);
        if (payoutAmount == 0) {
            completeWithRecoveryOffset(payment, payout);
            accountDeletionFinancialService.clearFallbackIfNoUnboundPayouts(payment.getSellerUserId());
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        persistDurableRequestClaim(payment, payout, payoutAmount);
        return new ClaimedPayout(payment, payout, ClaimAction.CALL_PROVIDER);
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = PayoutEligibilityException.class)
    public PayoutTransaction bindProviderPayload(
            Long paymentTransactionId,
            Long payoutId,
            String providerCode,
            String providerSellerId) {
        PaymentTransaction candidate = paymentTransactionRepository.findById(paymentTransactionId)
                .orElseThrow(() -> new IllegalStateException(
                        "결제 트랜잭션을 찾을 수 없습니다: " + paymentTransactionId));
        accountDeletionFinancialService.lockSellerForPayoutClaim(candidate.getSellerUserId());
        Party party = lockParty(candidate.getPartyId());
        PaymentTransaction payment = lockPayment(paymentTransactionId);
        if (!Objects.equals(candidate.getSellerUserId(), payment.getSellerUserId())) {
            throw new PayoutEligibilityException("결제 판매자 정보가 변경되어 지급 자격을 확인할 수 없습니다.");
        }
        boolean partyLinkUnchanged = Objects.equals(candidate.getPartyId(), payment.getPartyId());
        PayoutTransaction payout = payoutTransactionRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new IllegalStateException(
                        "지급 트랜잭션을 찾을 수 없습니다: " + payoutId));
        if (!Objects.equals(payout.getPaymentTransactionId(), payment.getId())) {
            throw new PayoutEligibilityException("지급 트랜잭션과 결제 트랜잭션 연결이 일치하지 않습니다.");
        }
        if (!partyLinkUnchanged
                || party == null
                || !Objects.equals(party.getId(), payment.getPartyId())
                || party.getStatus() == Party.PartyStatus.FAILED) {
            if (payout.getStatus() != SettlementStatus.COMPLETED
                    && payout.getStatus() != SettlementStatus.FAILED
                    && payout.getStatus() != SettlementStatus.SKIPPED) {
                persistManualReconciliationHold(
                        payment,
                        payout,
                        "파티가 만료되었거나 연결을 확인할 수 없어 지급 dispatch를 보류합니다. 운영자 수동 대사가 필요합니다.");
            }
            throw new PayoutEligibilityException("만료되었거나 연결을 확인할 수 없는 파티의 지급은 dispatch할 수 없습니다.");
        }
        if (PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE.equals(payout.getFailureCode())) {
            throw new PayoutEligibilityException("수동 대사 보류 중인 지급은 provider 요청을 시작할 수 없습니다.");
        }
        if (payout.getStatus() != SettlementStatus.REQUESTED) {
            throw new PayoutEligibilityException("확정된 지급 claim이 없어 provider 요청을 시작할 수 없습니다.");
        }
        String normalizedProviderCode = Objects.toString(providerCode, "")
                .trim()
                .toUpperCase(Locale.ROOT);
        if (normalizedProviderCode.isBlank()) {
            throw new IllegalArgumentException("지급대행 provider가 올바르지 않습니다.");
        }
        if ("TOSS".equals(normalizedProviderCode)
                && (providerSellerId == null || providerSellerId.isBlank())) {
            throw new IllegalArgumentException("TOSS 판매자 지급 식별자가 올바르지 않습니다.");
        }
        if (payout.getProviderCode() != null
                && !payout.getProviderCode().isBlank()
                && !payout.getProviderCode().equals(normalizedProviderCode)) {
            throw new IllegalStateException("이미 고정된 지급대행 provider를 변경할 수 없습니다.");
        }
        if (payout.getProviderSellerId() != null
                && !payout.getProviderSellerId().isBlank()
                && !Objects.equals(payout.getProviderSellerId(), providerSellerId)) {
            throw new IllegalStateException("이미 고정된 판매자 지급 식별자를 변경할 수 없습니다.");
        }
        payout.setProviderCode(normalizedProviderCode);
        if (payout.getProviderSellerId() == null || payout.getProviderSellerId().isBlank()) {
            payout.setProviderSellerId(providerSellerId);
        }
        // This locked, durable payload snapshot is the dispatch-intent boundary shared with party expiry.
        Instant dispatchIntentAt = Instant.now();
        payout.setLastRetryAt(dispatchIntentAt);
        payout.setNextRetryAt(dispatchIntentAt.plusSeconds(PROVIDER_CALL_LEASE_SECONDS));
        PayoutTransaction saved = payoutTransactionRepository.saveAndFlush(payout);
        accountDeletionFinancialService.clearFallbackIfNoUnboundPayouts(payment.getSellerUserId());
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DispatchIntentClaim claimProviderDispatchIntent(Long paymentTransactionId, Long payoutId) {
        if (paymentTransactionId == null || payoutId == null) {
            throw new IllegalArgumentException("지급 dispatch intent 식별자가 올바르지 않습니다.");
        }

        PaymentTransaction candidate = paymentTransactionRepository.findById(paymentTransactionId)
                .orElseThrow(() -> new IllegalStateException(
                        "결제 트랜잭션을 찾을 수 없습니다: " + paymentTransactionId));
        accountDeletionFinancialService.lockSellerForPayoutClaim(candidate.getSellerUserId());
        Party party = lockParty(candidate.getPartyId());
        PartyApplication application = candidate.getApplicationId() == null || candidate.getBuyerUserId() == null
                ? null
                : partyApplicationRepository.findByIdAndApplicantIdForUpdate(
                        candidate.getApplicationId(), candidate.getBuyerUserId())
                        .orElse(null);

        PaymentTransaction payment = lockPayment(paymentTransactionId);
        PayoutTransaction payout = payoutTransactionRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new IllegalStateException(
                        "지급 트랜잭션을 찾을 수 없습니다: " + payoutId));
        if (!Objects.equals(payout.getPaymentTransactionId(), paymentTransactionId)) {
            throw new PayoutEligibilityException("지급 트랜잭션과 결제 트랜잭션 연결이 일치하지 않습니다.");
        }
        if (payout.getStatus() == SettlementStatus.COMPLETED
                || payout.getStatus() == SettlementStatus.FAILED
                || payout.getStatus() == SettlementStatus.SKIPPED
                || PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE.equals(payout.getFailureCode())) {
            return new DispatchIntentClaim(payout, false);
        }

        boolean eligible = Objects.equals(candidate.getSellerUserId(), payment.getSellerUserId())
                && Objects.equals(candidate.getPartyId(), payment.getPartyId())
                && Objects.equals(payout.getSellerId(), payment.getSellerUserId())
                && party != null
                && Objects.equals(party.getId(), payment.getPartyId())
                && party.getStatus() != Party.PartyStatus.FAILED
                && payment.getPaymentStatus() == PaymentStatus.PAID
                && application != null
                && Boolean.TRUE.equals(application.getIsPaid());
        if (eligible) {
            try {
                validateLinkedApplicationEligibility(payment, application);
            } catch (PayoutEligibilityException invalidLink) {
                eligible = false;
            }
        }
        if (!eligible) {
            return new DispatchIntentClaim(persistManualReconciliationHold(
                    payment,
                    payout,
                    "파티 만료 또는 결제 신청 연결 검증 실패로 provider 지급 요청을 보류합니다. 운영자 수동 대사가 필요합니다.").payout(),
                    false);
        }
        if (payout.getStatus() != SettlementStatus.REQUESTED) {
            return new DispatchIntentClaim(persistManualReconciliationHold(
                    payment,
                    payout,
                    "지급 요청 상태를 확인할 수 없어 provider 지급 요청을 보류합니다. 운영자 수동 대사가 필요합니다.").payout(),
                    false);
        }

        if (payout.getProviderDispatchIntentAt() == null) {
            payout.setProviderDispatchIntentAt(Instant.now());
        }
        PayoutTransaction savedPayout = payoutTransactionRepository.saveAndFlush(payout);
        accountDeletionFinancialService.clearFallbackIfNoUnboundPayouts(payment.getSellerUserId());
        return new DispatchIntentClaim(savedPayout, true);
    }

    private PaymentTransaction lockPayment(Long paymentTransactionId) {
        return paymentTransactionRepository.findByIdForUpdate(paymentTransactionId)
                .orElseThrow(() -> new IllegalStateException(
                        "결제 트랜잭션을 찾을 수 없습니다: " + paymentTransactionId));
    }

    private Party lockParty(Long partyId) {
        return partyId == null
                ? null
                : partyRepository.findByIdForUpdate(partyId).orElse(null);
    }

    private ClaimedPayout persistManualReconciliationHold(
            PaymentTransaction payment,
            PayoutTransaction payout,
            String reason) {
        payout.setStatus(SettlementStatus.REQUESTED);
        payout.setFailureCode(PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE);
        payout.setFailReason(reason);
        payout.setNextRetryAt(null);
        PayoutTransaction savedPayout = payoutTransactionRepository.saveAndFlush(payout);
        payment.setSettlementStatus(SettlementStatus.REQUESTED);
        paymentTransactionRepository.saveAndFlush(payment);
        metricsService.recordPayout("manual_reconciliation");
        return new ClaimedPayout(payment, savedPayout, ClaimAction.NONE);
    }

    private int reserveRecoveryOffset(PaymentTransaction payment, PayoutTransaction payout) {
        int netAmount = Math.max(0, Objects.requireNonNullElse(payment.getNetAmount(), 0));
        if (payout.getRecoveryOffsetReservedAt() == null) {
            SellerRecoveryService.RecoveryOffsetResult result = sellerRecoveryService.reserveOffset(
                    payout,
                    payment.getSellerUserId(),
                    netAmount);
            int offsetAmount = result != null ? result.offsetAmount() : 0;
            payout.setRecoveryOffsetAmount(Math.min(netAmount, Math.max(0, offsetAmount)));
            payout.setRecoveryOffsetReservedAt(Instant.now());
            payoutTransactionRepository.save(payout);
        }
        int reservedOffset = Math.min(
                netAmount,
                Math.max(0, Objects.requireNonNullElse(payout.getRecoveryOffsetAmount(), 0)));
        return netAmount - reservedOffset;
    }

    private void persistDurableRequestClaim(
            PaymentTransaction payment,
            PayoutTransaction payout,
            int payoutAmount) {
        Instant now = Instant.now();
        payout.setStatus(SettlementStatus.REQUESTED);
        payout.setRequestedAmount(payoutAmount);
        if (payout.getRequestedAt() == null) {
            payout.setRequestedAt(now);
        }
        payout.setCompletedAt(null);
        payout.setFailReason(null);
        payout.setFailureCode(null);
        payout.setClaimProtocol(SNAPSHOT_CLAIM_PROTOCOL);
        payout.setLastRetryAt(now);
        payout.setNextRetryAt(now.plusSeconds(PROVIDER_CALL_LEASE_SECONDS));
        payoutTransactionRepository.saveAndFlush(payout);

        payment.setSettlementStatus(SettlementStatus.REQUESTED);
        paymentTransactionRepository.saveAndFlush(payment);
    }

    private void completeWithRecoveryOffset(PaymentTransaction payment, PayoutTransaction payout) {
        Instant now = Instant.now();
        sellerRecoveryService.applyReservedOffset(payout);
        payout.setStatus(SettlementStatus.COMPLETED);
        payout.setRequestedAmount(0);
        payout.setProviderRef("RECOVERY_OFFSET");
        payout.setClaimProtocol(SNAPSHOT_CLAIM_PROTOCOL);
        payout.setRequestedAt(now);
        payout.setCompletedAt(now);
        payout.setFailReason(null);
        payout.setFailureCode("PAYOUT_COMPLETION_VERIFIED");
        payout.setNextRetryAt(null);
        payoutTransactionRepository.save(payout);

        payment.setSettlementStatus(SettlementStatus.COMPLETED);
        paymentTransactionRepository.save(payment);
        metricsService.recordPayout("recovery_offset");
    }

    private void markSkipped(
            PaymentTransaction payment,
            PayoutTransaction payout,
            String failureCode,
            boolean updatePaymentSettlement) {
        sellerRecoveryService.releaseReservedOffset(payout);
        payout.setRecoveryOffsetAmount(0);
        payout.setRecoveryOffsetReservedAt(null);
        payout.setStatus(SettlementStatus.SKIPPED);
        payout.setRequestedAmount(payment.getNetAmount());
        payout.setRequestedAt(Instant.now());
        payout.setLastRetryAt(null);
        payout.setNextRetryAt(null);
        payout.setFailureCode(failureCode);
        payout.setFailReason("paymentStatus=" + payment.getPaymentStatus());
        payoutTransactionRepository.save(payout);
        if (updatePaymentSettlement) {
            payment.setSettlementStatus(SettlementStatus.SKIPPED);
            paymentTransactionRepository.save(payment);
        }
        accountDeletionFinancialService.clearFallbackIfNoUnboundPayouts(payment.getSellerUserId());
        metricsService.recordPayout("skip");
    }

    public enum ClaimAction {
        NONE,
        CALL_PROVIDER,
        POLL_PROVIDER
    }

    public record ClaimedPayout(
            PaymentTransaction payment,
            PayoutTransaction payout,
            ClaimAction action) {

        static ClaimedPayout none() {
            return new ClaimedPayout(null, null, ClaimAction.NONE);
        }
    }

    public record DispatchIntentClaim(PayoutTransaction payout, boolean dispatchAllowed) {
    }
}
