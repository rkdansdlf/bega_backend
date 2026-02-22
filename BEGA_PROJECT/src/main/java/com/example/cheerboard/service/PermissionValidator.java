package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import static com.example.cheerboard.service.CheerServiceConstants.*;

@Component
public class PermissionValidator {

    public void validateTeamAccess(UserEntity user, String teamId, String action) {
        // Admins can bypass checks
        if (isAdmin(user)) {
            return;
        }

        // Basic validation: teamId must be present
        if (teamId == null) {
            throw new AccessDeniedException(String.format("잘못된 팀 정보입니다. (%s)", action));
        }

        // Removed strict favoriteTeam check to allow cross-team interaction
        // Users can now participate in any team's board
    }

    public void validateOwnerOrAdmin(UserEntity user, UserEntity author, String action) {
        if (!isOwnerOrAdmin(user, author)) {
            throw new AccessDeniedException(String.format(PERMISSION_ERROR, action));
        }
    }

    public void validateAdminRole(UserEntity user, String action) {
        if (!isAdmin(user)) {
            throw new AccessDeniedException(String.format(PERMISSION_ERROR, action));
        }
    }

    public void validateNoticePermission(UserEntity user) {
        if (!isAdmin(user)) {
            throw new AccessDeniedException(NOTICE_ADMIN_ONLY_ERROR);
        }
    }

    public boolean isOwnerOrAdmin(UserEntity user, UserEntity author) {
        return author.getId().equals(user.getId()) || isAdmin(user);
    }

    public boolean isAdmin(UserEntity user) {
        return user.isAdmin();
    }
}
