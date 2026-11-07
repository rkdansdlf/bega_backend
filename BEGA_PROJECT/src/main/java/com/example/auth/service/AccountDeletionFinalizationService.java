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
    private final ModerationHoldAlertService moderationHoldAlerts;

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
            AuditLogRepository auditLogRepository,
            ModerationHoldAlertService moderationHoldAlerts) {
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
        this.moderationHoldAlerts = moderationHoldAlerts;
    }

    public enum Outcome { FINALIZED, DEFERRED, SKIPPED }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean finalizeIfDue(Long userId, LocalDateTime cutoff) {
        return finalizeLockedDueDeletion(userId, cutoff, null) == Outcome.FINALIZED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome finalizeImmediatelyAfterAdminDeletion(Long userId, Long administratorId) {
        return finalizeLockedDueDeletion(userId, LocalDateTime.now(), administratorId);
    }

    private Outcome finalizeLockedDueDeletion(Long userId, LocalDateTime cutoff, Long administratorId) {
        UserEntity user = userRepository.findByIdForWrite(userId).orElse(null);
        if (user == null
                || !user.isPendingDeletion()
                || user.getDeletionScheduledFor() == null
                || user.getDeletionScheduledFor().isAfter(cutoff)) {
            return Outcome.SKIPPED;
        }
        if (AccountDeletionIdentityService.isAnonymizedTombstone(user)) {
            return Outcome.SKIPPED;
        }

        // Self-service always persists deletionRequestedAt; null marks the admin immediate/retry path.
        boolean adminInitiated = user.getDeletionRequestedAt() == null;
        // Content goes first: PURGE_PENDING returns before any side effect, so nothing partial commits
        // and the daily finalizer retries the whole pass (deletionScheduledFor stays set).
        AccountDeletionContentService.Result contentResult = contentService.anonymize(userId);
        if (contentResult == AccountDeletionContentService.Result.PURGE_PENDING) {
            if (adminInitiated && administratorId != null) {
                saveDeleteUserAudit(administratorId, userId, "사용자 삭제 보류: 게시물 purge 완료 대기");
            }
            log.warn("Deferred account finalization until post purge completes: userId={}", userId);
            return Outcome.DEFERRED;
        }
        partyService.handleUserDeletion(userId);
        if (adminInitiated) {
            removeAdminLikes(user);
        }
        reviewService.anonymize(userId);
        financialService.anonymize(userId);
        // Bulk updates may clear the persistence context; reload a managed user before lazy identity access.
        user = userRepository.findByIdForWrite(userId)
                .orElseThrow(() -> new IllegalStateException("The account disappeared during deletion finalization."));
        user.getProviders().size();
        identityService.anonymize(user);
        user.setContentAnonymizationRetryAt(contentResult != AccountDeletionContentService.Result.COMPLETED
                ? LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(10) : null);
        if (contentResult == AccountDeletionContentService.Result.COMPLETED) {
            // The retry loop will not visit this account again, so end its alert state here.
            moderationHoldAlerts.forget(userId);
            moderationHoldAlerts.clearPurgeHold(user);
        }
        user.setDeletionScheduledFor(null);
        userRepository.save(user);
        accountDeletionTokenRepository.deleteByUser_Id(userId);
        if (adminInitiated && administratorId != null) {
            saveDeleteUserAudit(administratorId, userId, "사용자 삭제 및 비식별 처리");
        }
        log.info("Finalized pending account deletion for userId={}", userId);
        return Outcome.FINALIZED;
    }

    private void saveDeleteUserAudit(Long administratorId, Long userId, String description) {
        auditLogRepository.save(AuditLog.builder()
                .adminId(administratorId)
                .targetUserId(userId)
                .action(AuditLog.AuditAction.DELETE_USER)
                .oldValue("[REDACTED]")
                .newValue(null)
                .description(description)
                .build());
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
