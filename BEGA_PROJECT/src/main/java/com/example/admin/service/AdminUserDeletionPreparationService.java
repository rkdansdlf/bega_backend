package com.example.admin.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminUserDeletionPreparationService {

    private final UserRepository userRepository;
    private final AccountDeletionTokenRepository accountDeletionTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserEntity disableForDeletion(Long userId) {
        Long requiredUserId = Objects.requireNonNull(userId, "userId must not be null");
        UserEntity user = userRepository.findByIdForWrite(requiredUserId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
        boolean changed = false;
        if (user.isEnabled()) {
            user.setEnabled(false);
            user.setTokenVersion(Objects.requireNonNullElse(user.getTokenVersion(), 0) + 1);
            changed = true;
        }
        if (user.isPendingDeletion()
                || user.getDeletionRequestedAt() != null
                || user.getDeletionScheduledFor() != null
                || user.getLockExpiresAt() != null) {
            user.setPendingDeletion(false);
            user.setDeletionRequestedAt(null);
            user.setDeletionScheduledFor(null);
            user.setLockExpiresAt(null);
            changed = true;
        }
        accountDeletionTokenRepository.deleteByUser_Id(requiredUserId);
        return changed ? userRepository.save(user) : user;
    }
}
