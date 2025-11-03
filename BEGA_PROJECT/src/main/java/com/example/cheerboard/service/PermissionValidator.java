package com.example.cheerboard.service;

import com.example.demo.entity.UserEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import static com.example.cheerboard.service.CheerServiceConstants.*;

@Component
public class PermissionValidator {

    public void validateTeamAccess(UserEntity user, String teamId, String action) {
        if (teamId == null) {
            throw new AccessDeniedException(String.format(TEAM_ACCESS_ERROR, action));
        }
        String favoriteTeam = user.getFavoriteTeamId();
        if (favoriteTeam == null || !favoriteTeam.equals(teamId)) {
            throw new AccessDeniedException(String.format(TEAM_ACCESS_ERROR, action));
        }
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
        return ADMIN_ROLE.equals(user.getRole());
    }
}
