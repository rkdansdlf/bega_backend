package com.example.auth.service;

import com.example.mate.entity.SellerPayoutProfile;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.Party;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.SellerPayoutProfileRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Scrubs seller verification material while preserving only a live payout fallback when required. */
@Service
@RequiredArgsConstructor
public class AccountDeletionFinancialService {

    private static final List<SettlementStatus> INCOMPLETE_PAYOUT_STATUSES =
            List.of(SettlementStatus.PENDING, SettlementStatus.REQUESTED);

    private final SellerPayoutProfileRepository sellerPayoutProfileRepository;
    private final PayoutTransactionRepository payoutTransactionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;
    private final PartyApplicationRepository partyApplicationRepository;

    @Transactional
    public void anonymize(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("A persisted account ID is required for finalization.");
        }
        List<SellerPayoutProfile> profiles = sellerPayoutProfileRepository.findAllByUserIdForUpdate(userId);
        if (profiles.isEmpty()) {
            anonymizeApplications(userId);
            return;
        }
        boolean providerFallbackRequired = hasPendingPayoutFallback(userId);
        for (SellerPayoutProfile profile : profiles) {
            profile.setKycStatus(null);
            profile.setMetadataJson(null);
            boolean isTossProfile = profile.getProvider() != null
                    && "TOSS".equalsIgnoreCase(profile.getProvider().trim());
            if (!providerFallbackRequired || !isTossProfile) {
                profile.setProviderSellerId(null);
            }
        }
        sellerPayoutProfileRepository.saveAll(profiles);
        anonymizeApplications(userId);
    }

    private void anonymizeApplications(Long userId) {
        List<PartyApplication> applications = partyApplicationRepository.findByApplicantId(userId);
        for (PartyApplication application : applications) {
            application.setApplicantName("탈퇴한 사용자");
            application.setApplicantBadge(Party.BadgeType.NEW);
            application.setApplicantRating(null);
            application.setMessage("신청 내용이 비식별 처리되었습니다.");
            application.setTicketImageUrl(null);
            application.setLastReadChatAt(null);
        }
        partyApplicationRepository.saveAll(applications);
    }

    /** Clears the retained fallback after the last payout snapshots it onto its financial record. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearFallbackIfNoUnboundPayouts(Long sellerId) {
        if (sellerId == null) {
            return;
        }
        UserEntity user = userRepository.findByIdForWrite(sellerId).orElse(null);
        if (!isFinalizedTombstone(user) || hasPendingPayoutFallback(sellerId)) {
            return;
        }
        List<SellerPayoutProfile> profiles = sellerPayoutProfileRepository.findAllByUserIdForUpdate(sellerId);
        profiles.forEach(profile -> profile.setProviderSellerId(null));
        sellerPayoutProfileRepository.saveAll(profiles);
    }

    private boolean hasUnboundPayoutFallback(Long sellerId) {
        return payoutTransactionRepository.existsUnboundTossPayoutForSeller(sellerId, INCOMPLETE_PAYOUT_STATUSES);
    }

    private boolean hasPendingPayoutFallback(Long sellerId) {
        return hasUnboundPayoutFallback(sellerId)
                || !paymentTransactionRepository.findApprovedWithoutPayoutForSeller(
                        sellerId,
                        PaymentStatus.PAID,
                        SettlementStatus.PENDING,
                        PageRequest.of(0, 1)).isEmpty();
    }

    /** Serializes a new durable payout claim with final account deletion. */
    @Transactional
    public void lockSellerForPayoutClaim(Long sellerId) {
        if (sellerId != null) {
            userRepository.findByIdForWrite(sellerId);
        }
    }

    private boolean isFinalizedTombstone(UserEntity user) {
        return user != null && !user.isEnabled() && user.isPendingDeletion()
                && user.getDeletionScheduledFor() == null;
    }
}
