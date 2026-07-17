package com.example.mate.service;

import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class PartyLifecycleMutationService {

    private final PartyRepository partyRepository;
    private final PartyApplicationRepository applicationRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Party expirePendingParty(Long partyId, LocalDate today) {
        Party party = partyRepository.findByIdForUpdate(partyId).orElse(null);
        if (party == null
                || party.getStatus() != Party.PartyStatus.PENDING
                || party.getGameDate() == null
                || !party.getGameDate().isBefore(today)) {
            return null;
        }
        party.setStatus(Party.PartyStatus.FAILED);
        return partyRepository.save(party);
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
