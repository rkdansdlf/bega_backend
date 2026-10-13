package com.example.auth.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.Role;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.mate.service.PartyService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AccountDeletionFinalizationService {

    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final AccountDeletionTokenRepository accountDeletionTokenRepository;
    private final PartyService partyService;
    private final AccountDeletionIdentityService accountDeletionIdentityService;
    private final AccountDeletionCommunicationService accountDeletionCommunicationService;
    private final AccountDeletionContentService accountDeletionContentService;
    private final AccountDeletionReviewService accountDeletionReviewService;
    private final AccountDeletionFinancialService accountDeletionFinancialService;

    public AccountDeletionFinalizationService(
            UserRepository userRepository,
            EntityManager entityManager,
            AccountDeletionTokenRepository accountDeletionTokenRepository,
            @Lazy PartyService partyService,
            AccountDeletionIdentityService accountDeletionIdentityService,
            AccountDeletionCommunicationService accountDeletionCommunicationService,
            AccountDeletionContentService accountDeletionContentService,
            AccountDeletionReviewService accountDeletionReviewService,
            AccountDeletionFinancialService accountDeletionFinancialService) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.accountDeletionTokenRepository = accountDeletionTokenRepository;
        this.partyService = partyService;
        this.accountDeletionIdentityService = accountDeletionIdentityService;
        this.accountDeletionCommunicationService = accountDeletionCommunicationService;
        this.accountDeletionContentService = accountDeletionContentService;
        this.accountDeletionReviewService = accountDeletionReviewService;
        this.accountDeletionFinancialService = accountDeletionFinancialService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean finalizeIfDue(Long userId, LocalDateTime cutoff) {
        UserEntity user = userRepository.findByIdForWrite(userId).orElse(null);
        if (user == null
                || !user.isPendingDeletion()
                || user.getDeletionScheduledFor() == null
                || user.getDeletionScheduledFor().isAfter(cutoff)) {
            return false;
        }

        partyService.handleUserDeletion(userId);
        accountDeletionCommunicationService.anonymize(userId);
        accountDeletionContentService.anonymize(userId);
        accountDeletionReviewService.anonymize(userId);
        accountDeletionFinancialService.anonymize(userId);
        accountDeletionIdentityService.anonymize(user);
        user.setDeletionScheduledFor(null);
        userRepository.save(user);
        accountDeletionTokenRepository.deleteByUser_Id(userId);
        log.info("Finalized pending account deletion for userId={}", userId);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean finalizeImmediatelyAfterAdminDeletion(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("A persisted account ID is required for finalization.");
        }
        UserEntity user = userRepository.findByIdForWrite(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (!Role.USER.getKey().equals(user.getRole())) {
            throw new IllegalStateException("Only ordinary user accounts may be finalized by this workflow.");
        }
        if (user.isEnabled()) {
            throw new IllegalStateException("The account must be disabled before finalization.");
        }
        if (AccountDeletionIdentityService.isAnonymizedTombstone(user)) {
            return false;
        }
        if (!user.isPendingDeletion() || user.getDeletionScheduledFor() == null) {
            throw new IllegalStateException("The immediate finalization retry marker is required.");
        }

        partyService.handleUserDeletion(userId);
        accountDeletionCommunicationService.anonymize(userId);
        accountDeletionContentService.anonymize(userId);
        accountDeletionReviewService.anonymize(userId);
        accountDeletionFinancialService.anonymize(userId);
        accountDeletionIdentityService.anonymize(user);
        user.setDeletionScheduledFor(null);
        userRepository.save(user);
        accountDeletionTokenRepository.deleteByUser_Id(userId);
        log.info("Immediately anonymized admin-deleted account userId={}", userId);
        return true;
    }
}
