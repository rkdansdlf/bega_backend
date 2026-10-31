package com.example.mate.service;

import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PayoutTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartyLifecycleMutationService {

    private final PartyRepository partyRepository;
    private final PartyApplicationRepository applicationRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PayoutTransactionRepository payoutTransactionRepository;

    private record ApprovedPaidPayoutContext(PaymentTransaction payment, PayoutTransaction payout) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Party expirePendingParty(Long partyId, LocalDate today) {
        Party party = partyRepository.findByIdForUpdate(partyId).orElse(null);
        if (party == null
                || party.getStatus() != Party.PartyStatus.PENDING
                || party.getGameDate() == null
                || !party.getGameDate().isBefore(today)) {
            return null;
        }

        List<ApprovedPaidPayoutContext> approvedPaidPayouts = new ArrayList<>();
        for (PartyApplication application : applicationRepository
                .findApprovedPaidApplicationsByPartyIdForUpdate(partyId)) {
            PaymentTransaction payment = application.getOrderId() == null || application.getOrderId().isBlank()
                    ? null
                    : paymentTransactionRepository.findByOrderIdForUpdate(application.getOrderId()).orElse(null);
            if (!matchesApprovedPaidApplication(payment, application, partyId)) {
                log.warn("[PartyLifecycle] approved paid application has no matching payment; keep payout held: partyId={}, applicationId={}",
                        partyId, application.getId());
                continue;
            }
            PayoutTransaction payout = payoutTransactionRepository
                    .findTopByPaymentTransactionIdForUpdateOrderByIdDesc(payment.getId())
                    .orElse(null);
            approvedPaidPayouts.add(new ApprovedPaidPayoutContext(payment, payout));
        }

        party.setStatus(Party.PartyStatus.FAILED);
        Instant expiredAt = Instant.now();
        for (PartyApplication application : applicationRepository
                .findByPartyIdAndIsApprovedFalseAndIsRejectedFalse(partyId)) {
            application.setIsRejected(true);
            application.setRejectedAt(expiredAt);
            applicationRepository.save(application);
        }
        for (ApprovedPaidPayoutContext context : approvedPaidPayouts) {
            holdPayoutForManualReconciliation(context.payment(), context.payout());
        }
        return partyRepository.save(party);
    }

    private boolean matchesApprovedPaidApplication(
            PaymentTransaction payment,
            PartyApplication application,
            Long partyId) {
        return payment != null
                && payment.getId() != null
                && payment.getSellerUserId() != null
                && payment.getNetAmount() != null
                && application.getId() != null
                && application.getApplicantId() != null
                && application.getOrderId() != null
                && partyId.equals(application.getPartyId())
                && Boolean.TRUE.equals(application.getIsApproved())
                && Boolean.TRUE.equals(application.getIsPaid())
                && !Boolean.TRUE.equals(application.getIsRejected())
                && payment.getApplicationId() != null
                && payment.getPartyId() != null
                && payment.getBuyerUserId() != null
                && payment.getOrderId() != null
                && payment.getOrderId().equals(application.getOrderId())
                && payment.getApplicationId().equals(application.getId())
                && payment.getPartyId().equals(partyId)
                && payment.getBuyerUserId().equals(application.getApplicantId());
    }

    private void holdPayoutForManualReconciliation(PaymentTransaction payment, PayoutTransaction existingPayout) {
        if (existingPayout != null
                && (existingPayout.getStatus() == SettlementStatus.COMPLETED
                        || existingPayout.getStatus() == SettlementStatus.FAILED
                        || existingPayout.getStatus() == SettlementStatus.SKIPPED)) {
            return;
        }
        PayoutTransaction payout = existingPayout != null
                ? existingPayout
                : PayoutTransaction.builder()
                        .paymentTransactionId(payment.getId())
                        .sellerId(payment.getSellerUserId())
                        .requestedAmount(payment.getNetAmount())
                        .status(SettlementStatus.PENDING)
                        .build();
        if (payout.getStatus() == SettlementStatus.PENDING) {
            payout.setStatus(SettlementStatus.REQUESTED);
        }
        payout.setFailureCode(PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE);
        payout.setFailReason("모집 미달 파티가 경기일 이후 만료되어 지급·환불을 자동 진행하지 않습니다. 운영자 수동 대사가 필요합니다.");
        payout.setNextRetryAt(null);
        payoutTransactionRepository.saveAndFlush(payout);

        if (payout.getStatus() == SettlementStatus.REQUESTED) {
            payment.setSettlementStatus(SettlementStatus.REQUESTED);
            paymentTransactionRepository.save(payment);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Party completeMatchedParty(Long partyId, LocalDate gameDate) {
        Party party = partyRepository.findByIdForUpdate(partyId).orElse(null);
        if (party == null
                || party.getStatus() != Party.PartyStatus.MATCHED
                || !gameDate.equals(party.getGameDate())) {
            return null;
        }
        party.setStatus(Party.PartyStatus.COMPLETED);
        return partyRepository.save(party);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Party completeCheckedInParty(Long partyId, Instant completedBefore) {
        Party party = partyRepository.findByIdForUpdate(partyId).orElse(null);
        if (party == null
                || party.getStatus() != Party.PartyStatus.CHECKED_IN
                || party.getGameDate() == null
                || party.getGameTime() == null) {
            return null;
        }

        Instant gameInstant = LocalDateTime.of(party.getGameDate(), party.getGameTime())
                .atZone(ZoneId.systemDefault())
                .toInstant();
        if (!gameInstant.isBefore(completedBefore)) {
            return null;
        }

        party.setStatus(Party.PartyStatus.COMPLETED);
        return partyRepository.save(party);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExpiredApplicationMutation rejectExpiredApplication(
            Long partyId,
            Long applicationId,
            Long applicantId,
            Instant now) {
        Party party = partyRepository.findByIdForUpdate(partyId).orElse(null);
        PartyApplication application = applicationRepository.findByIdAndApplicantIdForUpdate(
                        applicationId, applicantId)
                .orElse(null);
        if (application == null
                || !partyId.equals(application.getPartyId())
                || Boolean.TRUE.equals(application.getIsApproved())
                || Boolean.TRUE.equals(application.getIsRejected())
                || application.getResponseDeadline() == null
                || !application.getResponseDeadline().isBefore(now)) {
            return null;
        }

        application.setIsRejected(true);
        application.setRejectedAt(now);
        PartyApplication saved = applicationRepository.save(application);
        return new ExpiredApplicationMutation(saved, party);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markApplicationUnpaidAfterRefund(Long partyId, Long applicationId, Long applicantId) {
        partyRepository.findByIdForUpdate(partyId);
        applicationRepository.findByIdAndApplicantIdForUpdate(applicationId, applicantId)
                .filter(application -> partyId.equals(application.getPartyId()))
                .filter(application -> Boolean.TRUE.equals(application.getIsRejected()))
                .filter(application -> Boolean.TRUE.equals(application.getIsPaid()))
                .ifPresent(application -> {
                    application.setIsPaid(false);
                    applicationRepository.save(application);
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RefundAttemptClaim claimRefundAttempt(
            Long partyId,
            Long applicationId,
            Long applicantId,
            Instant retryBefore) {
        Party party = partyRepository.findByIdForUpdate(partyId).orElse(null);
        if (party == null) {
            return null;
        }

        PartyApplication application = applicationRepository.findByIdAndApplicantIdForUpdate(
                        applicationId, applicantId)
                .orElse(null);
        if (application == null
                || !partyId.equals(application.getPartyId())
                || !Boolean.TRUE.equals(application.getIsRejected())
                || !Boolean.TRUE.equals(application.getIsPaid())
                || application.getOrderId() == null
                || application.getOrderId().isBlank()) {
            return null;
        }

        PaymentTransaction transaction = paymentTransactionRepository
                .findByOrderIdForUpdate(application.getOrderId())
                .orElse(null);
        if (transaction == null) {
            if (application.getPaymentKey() == null || application.getPaymentKey().isBlank()) {
                return null;
            }
            int grossAmount = application.getDepositAmount() != null ? application.getDepositAmount() : 0;
            transaction = PaymentTransaction.builder()
                    .partyId(partyId)
                    .applicationId(applicationId)
                    .buyerUserId(applicantId)
                    .sellerUserId(party.getHostId())
                    .flowType(application.getPaymentType() == PartyApplication.PaymentType.FULL
                            ? PaymentFlowType.SELLING_FULL
                            : PaymentFlowType.DEPOSIT)
                    .orderId(application.getOrderId())
                    .paymentKey(application.getPaymentKey())
                    .grossAmount(grossAmount)
                    .feeAmount(0)
                    .refundAmount(0)
                    .netAmount(grossAmount)
                    .paymentStatus(PaymentStatus.PAID)
                    .settlementStatus(SettlementStatus.PENDING)
                    .build();
        }
        if (transaction.getPaymentStatus() == PaymentStatus.CANCELED) {
            return new RefundAttemptClaim(application, true);
        }
        if (transaction.getPaymentStatus() == PaymentStatus.REFUND_REQUESTED
                && transaction.getUpdatedAt() != null
                && retryBefore != null
                && !transaction.getUpdatedAt().isBefore(retryBefore)) {
            return null;
        }

        transaction.setPaymentStatus(PaymentStatus.REFUND_REQUESTED);
        transaction.setUpdatedAt(Instant.now());
        paymentTransactionRepository.save(transaction);
        return new RefundAttemptClaim(application, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRefundFailure(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        paymentTransactionRepository.findByOrderIdForUpdate(orderId)
                .filter(transaction -> transaction.getPaymentStatus() != PaymentStatus.CANCELED)
                .ifPresent(transaction -> {
                    transaction.setPaymentStatus(PaymentStatus.REFUND_FAILED);
                    paymentTransactionRepository.save(transaction);
                });
    }

    public record ExpiredApplicationMutation(PartyApplication application, Party party) {
    }

    public record RefundAttemptClaim(PartyApplication application, boolean alreadyCanceled) {
    }
}
