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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PayoutClaimService {

    private static final int MAX_PAYOUT_RETRY_ATTEMPTS = 5;
    private static final long PROVIDER_CALL_LEASE_SECONDS = 180;
    private static final String SNAPSHOT_CLAIM_PROTOCOL = "SNAPSHOT_V1";
    static final String PAYOUT_DISABLED_FAILURE_CODE = "PAYMENT_PAYOUT_DISABLED";

    private final PayoutTransactionRepository payoutTransactionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;
    private final PartyRepository partyRepository;
    private final PartyApplicationRepository partyApplicationRepository;
    private final MateLinkReconciliationCaseRecorder reconciliationCaseRecorder;
    private final SellerRecoveryService sellerRecoveryService;
    private final PaymentMetricsService metricsService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimedPayout claimInitial(Long paymentTransactionId, boolean payoutEnabled) {
        PaymentTransaction candidate = paymentTransactionRepository.findById(paymentTransactionId)
                .orElseThrow(() -> new IllegalStateException(
                        "결제 트랜잭션을 찾을 수 없습니다: " + paymentTransactionId));

        // Match the payout/lifecycle lock order so approval and expiry cannot race this eligibility check.
        if (candidate.getSellerUserId() != null) {
            userRepository.findByIdForWrite(candidate.getSellerUserId());
        }
        Party party = candidate.getPartyId() == null
                ? null
                : partyRepository.findByIdForUpdate(candidate.getPartyId()).orElse(null);
        PartyApplication application = candidate.getOrderId() == null || candidate.getOrderId().isBlank()
                ? null
                : partyApplicationRepository.findByOrderIdForUpdate(candidate.getOrderId()).orElse(null);
        PaymentTransaction payment = paymentTransactionRepository.findByIdForUpdate(paymentTransactionId)
                .orElseThrow(() -> new IllegalStateException(
                        "결제 트랜잭션을 찾을 수 없습니다: " + paymentTransactionId));

        if (payment.getPaymentStatus() != PaymentStatus.PAID) {
            PayoutTransaction unpaidPayout = payoutTransactionRepository
                    .findTopByPaymentTransactionIdForUpdateOrderByIdDesc(payment.getId())
                    .orElseGet(() -> PayoutTransaction.builder()
                            .paymentTransactionId(payment.getId())
                            .sellerId(payment.getSellerUserId())
                            .requestedAmount(payment.getNetAmount())
                            .status(SettlementStatus.PENDING)
                            .build());
            markSkipped(payment, unpaidPayout, "PAYMENT_NOT_PAYABLE", false);
            return new ClaimedPayout(payment, unpaidPayout, ClaimAction.NONE);
        }

        MateLinkReconciliationReasonCode linkReason = initialPayoutLinkMismatchReason(
                candidate, payment, application, party);
        if (linkReason != null) {
            reconciliationCaseRecorder.record(
                    party != null ? party.getId() : payment.getPartyId(),
                    application != null ? application.getId() : payment.getApplicationId(),
                    payment.getId(),
                    linkReason);
            throw new InvalidApplicationStatusException(
                    "결제와 신청 연결을 확인할 수 없어 정산 지급을 요청할 수 없습니다.");
        }
        if (!Boolean.TRUE.equals(application.getIsApproved())
                || Boolean.TRUE.equals(application.getIsRejected())
                || !Boolean.TRUE.equals(application.getIsPaid())) {
            throw new InvalidApplicationStatusException(
                    "승인·결제된 신청에 대해서만 정산 지급을 요청할 수 있습니다.");
        }

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
        if (!payoutEnabled) {
            markSkipped(payment, payout, PAYOUT_DISABLED_FAILURE_CODE, true);
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }

        if (payout.getId() == null) {
            payout = payoutTransactionRepository.saveAndFlush(payout);
        }
        int payoutAmount = reserveRecoveryOffset(payment, payout);
        if (payoutAmount == 0) {
            completeWithRecoveryOffset(payment, payout);
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        persistDurableRequestClaim(payment, payout, payoutAmount);
        return new ClaimedPayout(payment, payout, ClaimAction.CALL_PROVIDER);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimedPayout resumeDisabledPayout(Long payoutId, boolean payoutEnabled) {
        if (!payoutEnabled || payoutId == null) {
            return ClaimedPayout.none();
        }

        PayoutTransaction candidate = payoutTransactionRepository.findById(payoutId).orElse(null);
        if (!isDisabledPayoutSkip(candidate)) {
            return ClaimedPayout.none();
        }
        PaymentTransaction paymentCandidate = paymentTransactionRepository
                .findById(candidate.getPaymentTransactionId())
                .orElse(null);
        if (paymentCandidate == null) {
            reconciliationCaseRecorder.record(
                    null,
                    null,
                    candidate.getPaymentTransactionId(),
                    MateLinkReconciliationReasonCode.PAYMENT_TRANSACTION_MISSING);
            holdPayoutWithoutPayment(candidate, "결제 연결을 확인할 수 없어 지급 수동 대사가 필요합니다.");
            return new ClaimedPayout(null, candidate, ClaimAction.NONE);
        }

        // Match the lifecycle lock order: seller -> party -> application -> payment -> payout.
        UserEntity seller = paymentCandidate.getSellerUserId() == null
                ? null
                : userRepository.findByIdForWrite(paymentCandidate.getSellerUserId()).orElse(null);
        Party party = paymentCandidate.getPartyId() == null
                ? null
                : partyRepository.findByIdForUpdate(paymentCandidate.getPartyId()).orElse(null);
        PartyApplication application = paymentCandidate.getOrderId() == null
                || paymentCandidate.getOrderId().isBlank()
                        ? null
                        : partyApplicationRepository.findByOrderIdForUpdate(paymentCandidate.getOrderId())
                                .orElse(null);
        PaymentTransaction payment = paymentTransactionRepository
                .findByIdForUpdate(candidate.getPaymentTransactionId())
                .orElse(null);
        PayoutTransaction payout = payoutTransactionRepository.findByIdForUpdate(payoutId).orElse(null);
        if (!isDisabledPayoutSkip(payout)) {
            return ClaimedPayout.none();
        }
        if (payment == null) {
            reconciliationCaseRecorder.record(
                    paymentCandidate.getPartyId(),
                    paymentCandidate.getApplicationId(),
                    paymentCandidate.getId(),
                    MateLinkReconciliationReasonCode.PAYMENT_TRANSACTION_MISSING);
            holdPayoutWithoutPayment(payout, "결제 연결을 확인할 수 없어 지급 수동 대사가 필요합니다.");
            return new ClaimedPayout(null, payout, ClaimAction.NONE);
        }

        MateLinkReconciliationReasonCode paymentLinkReason = dispatchLinkMismatchReason(
                paymentCandidate, payment, payout);
        if (paymentLinkReason != null) {
            reconciliationCaseRecorder.record(
                    party != null ? party.getId() : payment.getPartyId(),
                    application != null ? application.getId() : payment.getApplicationId(),
                    payment.getId(),
                    paymentLinkReason);
            return manualHoldForResumption(payment, payout,
                    "지급과 결제·판매자 연결 정보가 일치하지 않아 재개를 보류합니다.");
        }
        MateLinkReconciliationReasonCode applicationLinkReason = party == null
                ? MateLinkReconciliationReasonCode.PARTY_LINK_MISSING
                : applicationLinkMismatchReason(application, payment, party);
        if (applicationLinkReason != null) {
            reconciliationCaseRecorder.record(
                    party != null ? party.getId() : payment.getPartyId(),
                    application != null ? application.getId() : payment.getApplicationId(),
                    payment.getId(),
                    applicationLinkReason);
        }

        if (seller == null || !seller.isEnabled() || seller.isPendingDeletion()) {
            return manualHoldForResumption(payment, payout,
                    "판매자 계정 상태를 검증할 수 없어 재개를 보류합니다.");
        }
        if (party == null
                || party.getStatus() == null
                || party.getStatus() == Party.PartyStatus.FAILED
                || party.getGameDate() == null
                || party.getGameDate().isBefore(LocalDate.now())) {
            return manualHoldForResumption(payment, payout,
                    "파티가 만료되었거나 기한을 확인할 수 없어 재개를 보류합니다.");
        }
        if (application == null
                || applicationLinkReason != null
                || !Boolean.TRUE.equals(application.getIsApproved())
                || !Boolean.TRUE.equals(application.getIsPaid())
                || Boolean.TRUE.equals(application.getIsRejected())) {
            return manualHoldForResumption(payment, payout,
                    "승인·결제된 신청을 검증할 수 없어 재개를 보류합니다.");
        }
        if (payment.getPaymentStatus() != PaymentStatus.PAID
                || payment.getSettlementStatus() != SettlementStatus.SKIPPED
                || !Objects.equals(payout.getRequestedAmount(), payment.getNetAmount())) {
            return manualHoldForResumption(payment, payout,
                    "결제 금액 또는 지급 상태가 예상과 달라 재개를 보류합니다.");
        }
        if (hasProviderEvidence(payout)) {
            return manualHoldForResumption(payment, payout,
                    "provider 흔적이 있는 지급 건은 중복 요청 위험으로 수동 대사를 유지합니다.");
        }

        int payoutAmount = reserveRecoveryOffset(payment, payout);
        if (payoutAmount == 0) {
            completeWithRecoveryOffset(payment, payout);
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        persistDurableRequestClaim(payment, payout, payoutAmount);
        return new ClaimedPayout(payment, payout, ClaimAction.CALL_PROVIDER);
    }

    private boolean isDisabledPayoutSkip(PayoutTransaction payout) {
        return payout != null
                && payout.getStatus() == SettlementStatus.SKIPPED
                && PAYOUT_DISABLED_FAILURE_CODE.equals(payout.getFailureCode());
    }

    private boolean hasProviderEvidence(PayoutTransaction payout) {
        return payout.getProviderRef() != null && !payout.getProviderRef().isBlank()
                || payout.getProviderDispatchIntentAt() != null
                || payout.getProviderCode() != null && !payout.getProviderCode().isBlank()
                || payout.getProviderSellerId() != null && !payout.getProviderSellerId().isBlank()
                || payout.getClaimProtocol() != null && !payout.getClaimProtocol().isBlank()
                || Objects.requireNonNullElse(payout.getRetryCount(), 0) != 0
                || payout.getRecoveryOffsetReservedAt() != null
                || Objects.requireNonNullElse(payout.getRecoveryOffsetAmount(), 0) != 0;
    }

    private ClaimedPayout manualHoldForResumption(
            PaymentTransaction payment,
            PayoutTransaction payout,
            String reason) {
        holdForManualReconciliation(payment, payout, reason);
        return new ClaimedPayout(payment, payout, ClaimAction.NONE);
    }

    private void holdPayoutWithoutPayment(PayoutTransaction payout, String reason) {
        payout.setStatus(SettlementStatus.REQUESTED);
        payout.setFailureCode(PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE);
        payout.setFailReason(reason);
        payout.setNextRetryAt(null);
        payoutTransactionRepository.saveAndFlush(payout);
        metricsService.recordPayout("manual_reconciliation");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimedPayout claimRetry(Long payoutId, boolean payoutEnabled) {
        PayoutTransaction candidate = payoutTransactionRepository.findById(payoutId).orElse(null);
        if (candidate == null) {
            return ClaimedPayout.none();
        }
        PaymentTransaction payment = lockPayment(candidate.getPaymentTransactionId());
        PayoutTransaction payout = payoutTransactionRepository.findByIdForUpdate(payoutId).orElse(null);
        if (payout == null || payout.getStatus() != SettlementStatus.REQUESTED) {
            return ClaimedPayout.none();
        }
        if (PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE.equals(payout.getFailureCode())) {
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
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

        Party party = payment.getPartyId() == null
                ? null
                : partyRepository.findById(payment.getPartyId()).orElse(null);
        if (party == null) {
            reconciliationCaseRecorder.record(
                    payment.getPartyId(),
                    payment.getApplicationId(),
                    payment.getId(),
                    MateLinkReconciliationReasonCode.PARTY_LINK_MISSING);
        }
        if (party == null || party.getStatus() == Party.PartyStatus.FAILED) {
            holdForManualReconciliation(payment, payout,
                    "파티가 없거나 만료 또는 실패 상태여서 지급 결과 수동 대사가 필요합니다.");
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }

        int payoutAmount = reserveRecoveryOffset(payment, payout);
        if (payoutAmount == 0) {
            completeWithRecoveryOffset(payment, payout);
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        persistDurableRequestClaim(payment, payout, payoutAmount);
        return new ClaimedPayout(payment, payout, ClaimAction.CALL_PROVIDER);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimProviderDispatchIntent(Long paymentTransactionId, Long payoutId) {
        return claimProviderDispatchIntentLocked(paymentTransactionId, payoutId, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimResumedPayoutDispatchIntent(Long paymentTransactionId, Long payoutId) {
        return claimProviderDispatchIntentLocked(paymentTransactionId, payoutId, true);
    }

    private boolean claimProviderDispatchIntentLocked(
            Long paymentTransactionId,
            Long payoutId,
            boolean requireUnexpiredGameDate) {
        PaymentTransaction candidate = paymentTransactionRepository.findById(paymentTransactionId).orElse(null);
        if (candidate == null) {
            reconciliationCaseRecorder.record(
                    null,
                    null,
                    paymentTransactionId,
                    MateLinkReconciliationReasonCode.PAYMENT_TRANSACTION_MISSING);
            return false;
        }

        // Keep the lock order aligned with account finalization and party expiry:
        // seller -> party -> application -> payment -> payout.
        UserEntity seller = candidate.getSellerUserId() == null
                ? null
                : userRepository.findByIdForWrite(candidate.getSellerUserId()).orElse(null);
        Party party = candidate.getPartyId() == null
                ? null
                : partyRepository.findByIdForUpdate(candidate.getPartyId()).orElse(null);
        PartyApplication application = candidate.getOrderId() == null || candidate.getOrderId().isBlank()
                ? null
                : partyApplicationRepository.findByOrderIdForUpdate(candidate.getOrderId()).orElse(null);
        PaymentTransaction payment = paymentTransactionRepository.findByIdForUpdate(paymentTransactionId)
                .orElse(null);
        PayoutTransaction payout = payoutTransactionRepository.findByIdForUpdate(payoutId).orElse(null);

        if (payment == null) {
            reconciliationCaseRecorder.record(
                    candidate.getPartyId(),
                    candidate.getApplicationId(),
                    paymentTransactionId,
                    MateLinkReconciliationReasonCode.PAYMENT_TRANSACTION_MISSING);
            return false;
        }
        if (payout == null) {
            reconciliationCaseRecorder.record(
                    payment.getPartyId(),
                    payment.getApplicationId(),
                    payment.getId(),
                    MateLinkReconciliationReasonCode.PAYOUT_TRANSACTION_MISSING);
            return false;
        }
        if (payout.getStatus() != SettlementStatus.REQUESTED
                || PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE.equals(payout.getFailureCode())) {
            return false;
        }
        MateLinkReconciliationReasonCode paymentLinkReason = dispatchLinkMismatchReason(
                candidate, payment, payout);
        if (paymentLinkReason != null) {
            reconciliationCaseRecorder.record(
                    party != null ? party.getId() : payment.getPartyId(),
                    application != null ? application.getId() : payment.getApplicationId(),
                    payment.getId(),
                    paymentLinkReason);
            return holdForManualReconciliation(payment, payout,
                    "지급과 결제·판매자 연결 정보가 일치하지 않아 자동 디스패치를 보류합니다.");
        }
        if (seller == null || !seller.isEnabled() || seller.isPendingDeletion()) {
            return holdForManualReconciliation(payment, payout,
                    "판매자 계정이 비활성 또는 탈퇴 처리 중이어서 자동 디스패치를 보류합니다.");
        }
        if (party == null) {
            reconciliationCaseRecorder.record(
                    payment.getPartyId(),
                    payment.getApplicationId(),
                    payment.getId(),
                    MateLinkReconciliationReasonCode.PARTY_LINK_MISSING);
            return holdForManualReconciliation(payment, payout,
                    "파티 연결을 확인할 수 없어 지급 결과 수동 대사가 필요합니다.");
        }
        if (party.getStatus() == Party.PartyStatus.FAILED
                || requireUnexpiredGameDate
                        && (party.getGameDate() == null || party.getGameDate().isBefore(LocalDate.now()))) {
            return holdForManualReconciliation(payment, payout,
                    "파티가 만료 또는 실패 상태여서 지급 디스패치를 보류합니다.");
        }
        MateLinkReconciliationReasonCode applicationLinkReason = applicationLinkMismatchReason(
                application, payment, party);
        if (applicationLinkReason != null) {
            reconciliationCaseRecorder.record(
                    party.getId(),
                    application != null ? application.getId() : payment.getApplicationId(),
                    payment.getId(),
                    applicationLinkReason);
        }
        if (application == null
                || applicationLinkReason != null
                || !Boolean.TRUE.equals(application.getIsApproved())
                || !Boolean.TRUE.equals(application.getIsPaid())
                || Boolean.TRUE.equals(application.getIsRejected())) {
            return holdForManualReconciliation(payment, payout,
                    "승인·결제된 신청과 지급 연결을 검증할 수 없어 자동 디스패치를 보류합니다.");
        }
        if (payment.getPaymentStatus() != PaymentStatus.PAID) {
            return false;
        }

        if (payout.getProviderDispatchIntentAt() != null) {
            if (payout.getProviderRef() == null || payout.getProviderRef().isBlank()) {
                return holdForManualReconciliation(payment, payout,
                        "지급 요청 intent는 기록됐지만 provider 결과 식별자가 없어 재요청을 보류합니다.");
            }
            return false;
        }

        payout.setProviderDispatchIntentAt(Instant.now());
        payoutTransactionRepository.saveAndFlush(payout);
        return true;
    }

    private MateLinkReconciliationReasonCode dispatchLinkMismatchReason(
            PaymentTransaction candidate,
            PaymentTransaction payment,
            PayoutTransaction payout) {
        if (!Objects.equals(candidate.getApplicationId(), payment.getApplicationId())) {
            return MateLinkReconciliationReasonCode.PAYMENT_APPLICATION_LINK_MISMATCH;
        }
        if (!Objects.equals(candidate.getPartyId(), payment.getPartyId())) {
            return MateLinkReconciliationReasonCode.PAYMENT_PARTY_LINK_MISMATCH;
        }
        if (!Objects.equals(candidate.getOrderId(), payment.getOrderId())) {
            return MateLinkReconciliationReasonCode.PAYMENT_ORDER_LINK_MISMATCH;
        }
        if (!Objects.equals(candidate.getSellerUserId(), payment.getSellerUserId())) {
            return MateLinkReconciliationReasonCode.PAYMENT_SELLER_LINK_MISMATCH;
        }
        if (!Objects.equals(payout.getPaymentTransactionId(), payment.getId())) {
            return MateLinkReconciliationReasonCode.PAYOUT_PAYMENT_LINK_MISMATCH;
        }
        if (!Objects.equals(payout.getSellerId(), payment.getSellerUserId())) {
            return MateLinkReconciliationReasonCode.PAYOUT_SELLER_LINK_MISMATCH;
        }
        if (payment.getApplicationId() == null
                || payment.getPartyId() == null
                || payment.getBuyerUserId() == null
                || payment.getSellerUserId() == null
                || payment.getOrderId() == null
                || payment.getOrderId().isBlank()) {
            return MateLinkReconciliationReasonCode.PAYMENT_LINK_INCOMPLETE;
        }
        return null;
    }

    private MateLinkReconciliationReasonCode initialPayoutLinkMismatchReason(
            PaymentTransaction candidate,
            PaymentTransaction payment,
            PartyApplication application,
            Party party) {
        if (!Objects.equals(candidate.getApplicationId(), payment.getApplicationId())) {
            return MateLinkReconciliationReasonCode.PAYMENT_APPLICATION_LINK_MISMATCH;
        }
        if (!Objects.equals(candidate.getPartyId(), payment.getPartyId())) {
            return MateLinkReconciliationReasonCode.PAYMENT_PARTY_LINK_MISMATCH;
        }
        if (!Objects.equals(candidate.getOrderId(), payment.getOrderId())) {
            return MateLinkReconciliationReasonCode.PAYMENT_ORDER_LINK_MISMATCH;
        }
        if (!Objects.equals(candidate.getSellerUserId(), payment.getSellerUserId())) {
            return MateLinkReconciliationReasonCode.PAYMENT_SELLER_LINK_MISMATCH;
        }
        if (!Objects.equals(candidate.getBuyerUserId(), payment.getBuyerUserId())) {
            return MateLinkReconciliationReasonCode.PAYMENT_BUYER_LINK_MISMATCH;
        }
        if (party == null) {
            return MateLinkReconciliationReasonCode.PARTY_LINK_MISSING;
        }
        MateLinkReconciliationReasonCode applicationReason = applicationLinkMismatchReason(
                application, payment, party);
        if (applicationReason != null) {
            return applicationReason;
        }
        if (application.getPaymentKey() == null
                || application.getPaymentKey().isBlank()
                || application.getDepositAmount() == null) {
            return MateLinkReconciliationReasonCode.APPLICATION_LINK_INCOMPLETE;
        }
        if (payment.getPaymentKey() == null
                || payment.getPaymentKey().isBlank()
                || payment.getGrossAmount() == null) {
            return MateLinkReconciliationReasonCode.PAYMENT_LINK_INCOMPLETE;
        }
        if (!Objects.equals(application.getPaymentKey(), payment.getPaymentKey())
                || !Objects.equals(application.getDepositAmount(), payment.getGrossAmount())) {
            return MateLinkReconciliationReasonCode.PAYMENT_APPLICATION_LINK_MISMATCH;
        }
        return null;
    }

    private MateLinkReconciliationReasonCode applicationLinkMismatchReason(
            PartyApplication application,
            PaymentTransaction payment,
            Party party) {
        if (application == null) {
            return payment.getOrderId() == null || payment.getOrderId().isBlank()
                    ? MateLinkReconciliationReasonCode.APPLICATION_LINK_INCOMPLETE
                    : MateLinkReconciliationReasonCode.APPLICATION_LINK_MISSING;
        }
        if (application.getId() == null
                || application.getApplicantId() == null
                || application.getOrderId() == null
                || application.getOrderId().isBlank()) {
            return MateLinkReconciliationReasonCode.APPLICATION_LINK_INCOMPLETE;
        }
        if (!Objects.equals(application.getId(), payment.getApplicationId())) {
            return MateLinkReconciliationReasonCode.PAYMENT_APPLICATION_LINK_MISMATCH;
        }
        if (application.getPartyId() == null || payment.getPartyId() == null) {
            return MateLinkReconciliationReasonCode.PARTY_LINK_MISSING;
        }
        if (!Objects.equals(application.getPartyId(), payment.getPartyId())) {
            return MateLinkReconciliationReasonCode.PARTY_LINK_MISMATCH;
        }
        if (!Objects.equals(application.getApplicantId(), payment.getBuyerUserId())) {
            return MateLinkReconciliationReasonCode.PAYMENT_BUYER_LINK_MISMATCH;
        }
        if (!Objects.equals(application.getOrderId(), payment.getOrderId())) {
            return MateLinkReconciliationReasonCode.PAYMENT_ORDER_LINK_MISMATCH;
        }
        if (!Objects.equals(party.getHostId(), payment.getSellerUserId())) {
            return MateLinkReconciliationReasonCode.PAYMENT_SELLER_LINK_MISMATCH;
        }
        return null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PayoutTransaction bindProviderPayload(
            Long paymentTransactionId,
            Long payoutId,
            String providerCode,
            String providerSellerId) {
        lockPayment(paymentTransactionId);
        PayoutTransaction payout = payoutTransactionRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new IllegalStateException(
                        "지급 트랜잭션을 찾을 수 없습니다: " + payoutId));
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
        return payoutTransactionRepository.saveAndFlush(payout);
    }

    private PaymentTransaction lockPayment(Long paymentTransactionId) {
        return paymentTransactionRepository.findByIdForUpdate(paymentTransactionId)
                .orElseThrow(() -> new IllegalStateException(
                        "결제 트랜잭션을 찾을 수 없습니다: " + paymentTransactionId));
    }

    private boolean holdForManualReconciliation(
            PaymentTransaction payment,
            PayoutTransaction payout,
            String reason) {
        payout.setStatus(SettlementStatus.REQUESTED);
        payout.setFailureCode(PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE);
        payout.setFailReason(reason);
        payout.setNextRetryAt(null);
        payoutTransactionRepository.saveAndFlush(payout);
        payment.setSettlementStatus(SettlementStatus.REQUESTED);
        paymentTransactionRepository.saveAndFlush(payment);
        metricsService.recordPayout("manual_reconciliation");
        return false;
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
}
