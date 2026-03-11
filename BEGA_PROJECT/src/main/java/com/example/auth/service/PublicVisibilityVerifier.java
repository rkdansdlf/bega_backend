package com.example.auth.service;

import com.example.auth.entity.UserBlock;
import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserFollow;
import com.example.auth.repository.UserBlockRepository;
import com.example.auth.repository.UserFollowRepository;
import com.example.common.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PublicVisibilityVerifier {

    private final UserBlockRepository userBlockRepository;
    private final UserFollowRepository userFollowRepository;

    public boolean canAccess(UserEntity target, Long viewerId) {
        Long targetUserId = extractTargetUserId(target);

        if (viewerId != null && viewerId.equals(targetUserId)) {
            return true;
        }

        if (viewerId != null && userBlockRepository.existsBidirectionalBlock(viewerId, targetUserId)) {
            return false;
        }

        if (!target.isPrivateAccount()) {
            return true;
        }

        return viewerId != null && userFollowRepository.existsById(new UserFollow.Id(viewerId, targetUserId));
    }

    public void validate(UserEntity target, Long viewerId, String resourceLabel) {
        Long targetUserId = extractTargetUserId(target);

        if (viewerId != null && viewerId.equals(targetUserId)) {
            return;
        }

        if (viewerId != null && userBlockRepository.existsBidirectionalBlock(viewerId, targetUserId)) {
            throw new AccessDeniedException("차단 관계인 사용자의 " + resourceLabel + "는 조회할 수 없습니다.");
        }

        if (!target.isPrivateAccount()) {
            return;
        }

        if (viewerId != null && userFollowRepository.existsById(new UserFollow.Id(viewerId, targetUserId))) {
            return;
        }

        throw new AccessDeniedException("비공개 계정의 " + resourceLabel + "는 팔로워만 조회할 수 있습니다.");
    }

    private Long extractTargetUserId(UserEntity target) {
        if (target == null || target.getId() == null) {
            throw new UserNotFoundException("userId", String.valueOf(target != null ? target.getId() : null));
        }
        return Objects.requireNonNull(target.getId());
    }
}
