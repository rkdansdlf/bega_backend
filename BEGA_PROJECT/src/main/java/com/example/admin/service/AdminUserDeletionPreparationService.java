package com.example.admin.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AccountDeletionIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminUserDeletionPreparationService {

    private final UserRepository userRepository;
    private final AccountDeletionTokenRepository accountDeletionTokenRepository;

    /**
     * Disables the account and revokes its sessions even while a post purge is running.
     * A started purge does not block this step: the finalizer returns DEFERRED for it, so the
     * account stays disabled and the purge finishes first.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserEntity disableForDeletion(Long userId, Long actingUserId) {
        Long requiredUserId = Objects.requireNonNull(userId, "userId must not be null");
        UserEntity user = userRepository.findByIdForWrite(requiredUserId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
        AdminUserDeletionPolicy.assertCanDelete(actingUserId, user);
        if (AccountDeletionIdentityService.isAnonymizedTombstone(user)) {
            return user;
        }

        LocalDateTime now = LocalDateTime.now();
        boolean changed = false;
        if (user.isEnabled()) {
            user.setEnabled(false);
            user.setTokenVersion(Objects.requireNonNullElse(user.getTokenVersion(), 0) + 1);
            changed = true;
        }
        if (!user.isPendingDeletion()) {
            user.setPendingDeletion(true);
            changed = true;
        }
        if (user.getDeletionRequestedAt() != null) {
            user.setDeletionRequestedAt(null);
            changed = true;
        }
        if (user.getDeletionScheduledFor() == null || user.getDeletionScheduledFor().isAfter(now)) {
            user.setDeletionScheduledFor(now);
            changed = true;
        }
        if (user.getLockExpiresAt() != null) {
            user.setLockExpiresAt(null);
            changed = true;
        }
        accountDeletionTokenRepository.deleteByUser_Id(requiredUserId);
        return changed ? userRepository.save(user) : user;
    }
}
