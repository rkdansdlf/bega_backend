package com.example.admin.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.Role;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;

final class AdminUserDeletionPolicy {

    private AdminUserDeletionPolicy() {}

    static void assertCanDelete(Long actingUserId, UserEntity targetUser) {
        if (targetUser == null) {
            throw new IllegalArgumentException("삭제할 유저를 찾을 수 없습니다.");
        }
        if (Objects.equals(actingUserId, targetUser.getId())
                || !Role.USER.getKey().equals(targetUser.getRole())) {
            throw new AccessDeniedException("대상 계정은 삭제할 수 없습니다.");
        }
    }
}
