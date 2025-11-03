package com.example.cheerboard.service;

/**
 * CheerService에서 사용하는 상수들을 정의하는 클래스
 */
public final class CheerServiceConstants {
    
    // HOT 게시글 판정 기준
    public static final int HOT_LIKE_THRESHOLD = 10;
    public static final int HOT_VIEW_THRESHOLD = 20;
    public static final int HOT_LIKE_WITH_COMMENT_THRESHOLD = 5;
    public static final int HOT_COMMENT_THRESHOLD = 3;
    
    // 권한 관련
    public static final String ADMIN_ROLE = "ADMIN";
    public static final String NOTICE_POST_TYPE = "NOTICE";
    
    // 에러 메시지
    public static final String TEAM_ACCESS_ERROR = "%s는 마이팀에서만 가능합니다.";
    public static final String PERMISSION_ERROR = "%s 권한이 없습니다.";
    public static final String NOTICE_ADMIN_ONLY_ERROR = "공지사항은 관리자만 작성할 수 있습니다.";
    
    private CheerServiceConstants() {
        // 유틸리티 클래스이므로 인스턴스화 방지
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}