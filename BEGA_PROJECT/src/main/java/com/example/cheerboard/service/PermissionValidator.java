package com.example.cheerboard.service;

import com.example.cheerboard.domain.AppUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import static com.example.cheerboard.service.CheerServiceConstants.*;

/**
 * 권한 검증 로직을 담당하는 클래스
 */
@Component
public class PermissionValidator {
    
    /**
     * 팀 접근 권한 검증
     * 사용자의 응원팀과 게시글의 팀이 일치하는지 확인
     * 
     * @param user 사용자
     * @param teamId 접근하려는 팀 ID
     * @param action 수행하려는 작업 (에러 메시지용)
     * @throws AccessDeniedException 권한이 없는 경우
     */
    public void validateTeamAccess(AppUser user, String teamId, String action) {
        if (!user.getFavoriteTeamId().equals(teamId)) {
            throw new AccessDeniedException(String.format(TEAM_ACCESS_ERROR, action));
        }
    }
    
    /**
     * 소유자 또는 관리자 권한 검증
     * 게시글/댓글 작성자이거나 관리자인지 확인
     * 
     * @param user 현재 사용자
     * @param author 작성자
     * @param action 수행하려는 작업 (에러 메시지용)
     * @throws AccessDeniedException 권한이 없는 경우
     */
    public void validateOwnerOrAdmin(AppUser user, AppUser author, String action) {
        if (!isOwnerOrAdmin(user, author)) {
            throw new AccessDeniedException(String.format(PERMISSION_ERROR, action));
        }
    }
    
    /**
     * 관리자 권한 검증
     * 사용자가 관리자인지 확인
     * 
     * @param user 사용자
     * @param action 수행하려는 작업 (에러 메시지용)
     * @throws AccessDeniedException 권한이 없는 경우
     */
    public void validateAdminRole(AppUser user, String action) {
        if (!isAdmin(user)) {
            throw new AccessDeniedException(String.format(PERMISSION_ERROR, action));
        }
    }
    
    /**
     * 공지사항 작성 권한 검증
     * 관리자만 공지사항을 작성할 수 있음
     * 
     * @param user 사용자
     * @throws AccessDeniedException 권한이 없는 경우
     */
    public void validateNoticePermission(AppUser user) {
        if (!isAdmin(user)) {
            throw new AccessDeniedException(NOTICE_ADMIN_ONLY_ERROR);
        }
    }
    
    /**
     * 소유자 또는 관리자인지 확인
     */
    public boolean isOwnerOrAdmin(AppUser user, AppUser author) {
        return author.getId().equals(user.getId()) || isAdmin(user);
    }
    
    /**
     * 관리자인지 확인
     */
    public boolean isAdmin(AppUser user) {
        return ADMIN_ROLE.equals(user.getRole());
    }
}