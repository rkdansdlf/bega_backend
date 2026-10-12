package com.example.auth.service;

import com.example.admin.entity.AuditLog;
import com.example.admin.repository.AuditLogRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.mate.service.PartyService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AccountDeletionFinalizationService {

    private final UserRepository userRepository;
    private final AccountDeletionTokenRepository accountDeletionTokenRepository;
    private final PartyService partyService;
    private final CheerPostLikeRepo likeRepository;
    private final CheerPostRepo cheerPostRepository;
    private final AccountDeletionContentService contentService;
    private final AccountDeletionReviewService reviewService;
    private final AccountDeletionFinancialService financialService;
    private final AccountDeletionIdentityService identityService;
    private final AuditLogRepository auditLogRepository;

    public AccountDeletionFinalizationService(
            UserRepository userRepository,
            AccountDeletionTokenRepository accountDeletionTokenRepository,
            @Lazy PartyService partyService,
            CheerPostLikeRepo likeRepository,
            CheerPostRepo cheerPostRepository,
            AccountDeletionContentService contentService,
            AccountDeletionReviewService reviewService,
            AccountDeletionFinancialService financialService,
            AccountDeletionIdentityService identityService,
            AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.accountDeletionTokenRepository = accountDeletionTokenRepository;
        this.partyService = partyService;
        this.likeRepository = likeRepository;
        this.cheerPostRepository = cheerPostRepository;
        this.contentService = contentService;
        this.reviewService = reviewService;
        this.financialService = financialService;
        this.identityService = identityService;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean finalizeIfDue(Long userId, LocalDateTime cutoff) {
        return finalizeLockedDueDeletion(userId, cutoff, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean finalizeImmediatelyAfterAdminDeletion(Long userId, Long administratorId) {
        return finalizeLockedDueDeletion(userId, LocalDateTime.now(), administratorId);
    }

    private boolean finalizeLockedDueDeletion(Long userId, LocalDateTime cutoff, Long administratorId) {
        UserEntity user = userRepository.findByIdForWrite(userId).orElse(null);
        if (user == null
                || !user.isPendingDeletion()
                || user.getDeletionScheduledFor() == null
                || user.getDeletionScheduledFor().isAfter(cutoff)) {
            return false;
        }
        if (AccountDeletionIdentityService.isAnonymizedTombstone(user)) {
            return false;
        }

        // Self-service always persists deletionRequestedAt; null marks the admin immediate/retry path.
        boolean adminInitiated = user.getDeletionRequestedAt() == null;
        partyService.handleUserDeletion(userId);
        if (adminInitiated) {
            removeAdminLikes(user);
        }
        contentService.anonymize(userId);
        reviewService.anonymize(userId);
        financialService.anonymize(userId);
        // Bulk updates may clear the persistence context; reload a managed user before lazy identity access.
        user = userRepository.findByIdForWrite(userId)
                .orElseThrow(() -> new IllegalStateException("The account disappeared during deletion finalization."));
        user.getProviders().size();
        identityService.anonymize(user);
        user.setDeletionScheduledFor(null);
        userRepository.save(user);
        accountDeletionTokenRepository.deleteByUser_Id(userId);
        if (adminInitiated && administratorId != null) {
            auditLogRepository.save(AuditLog.builder()
                    .adminId(administratorId)
                    .targetUserId(userId)
                    .action(AuditLog.AuditAction.DELETE_USER)
                    .oldValue("[REDACTED]")
                    .newValue(null)
                    .description("사용자 삭제 및 비식별 처리")
                    .build());
        }
        log.info("Finalized pending account deletion for userId={}", userId);
        return true;
    }

    private void removeAdminLikes(UserEntity user) {
        List<CheerPostLike> userLikes = likeRepository.findByUser(user);
        if (userLikes == null || userLikes.isEmpty()) {
            return;
        }
        Set<Long> affectedPostIds = userLikes.stream()
                .map(CheerPostLike::getPost)
                .filter(post -> post != null && post.getId() != null)
                .map(CheerPost::getId)
                .collect(Collectors.toSet());
        likeRepository.deleteAll(userLikes);
        likeRepository.flush();
        for (Long postId : affectedPostIds) {
            int exactLikeCount = Math.toIntExact(likeRepository.countByPostId(postId));
            cheerPostRepository.setExactLikeCount(postId, exactLikeCount);
        }
    }
}
