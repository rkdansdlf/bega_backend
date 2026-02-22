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
    public static final String ADMIN_ROLE = "ROLE_ADMIN";
    public static final String NOTICE_POST_TYPE = "NOTICE";
    
    // 에러 메시지
    public static final String TEAM_ACCESS_ERROR = "%s는 마이팀에서만 가능합니다.";
    public static final String PERMISSION_ERROR = "%s 권한이 없습니다.";
    public static final String NOTICE_ADMIN_ONLY_ERROR = "공지사항은 관리자만 작성할 수 있습니다.";

    // 리포스트 정책 메시지
    public static final String REPOST_NOT_ALLOWED_CODE = "REPOST_NOT_ALLOWED";
    public static final String REPOST_NOT_ALLOWED_BLOCKED_CODE = "REPOST_NOT_ALLOWED_BLOCKED";
    public static final String REPOST_NOT_ALLOWED_PRIVATE_CODE = "REPOST_NOT_ALLOWED_PRIVATE";
    public static final String REPOST_CANCEL_NOT_ALLOWED_CODE = "REPOST_CANCEL_NOT_ALLOWED";
    public static final String REPOST_CANCEL_NOT_ALLOWED_ERROR = "리포스트를 작성한 사용자만 삭제할 수 있습니다.";
    public static final String REPOST_QUOTE_NOT_ALLOWED_CODE = "REPOST_QUOTE_NOT_ALLOWED";
    public static final String REPOST_NOT_A_REPOST_CODE = "REPOST_NOT_A_REPOST";
    public static final String REPOST_SELF_NOT_ALLOWED_CODE = "REPOST_SELF_NOT_ALLOWED";
    public static final String REPOST_TARGET_NOT_FOUND_CODE = "REPOST_TARGET_NOT_FOUND";
    public static final String REPOST_CONFLICT_CODE = "REPOST_CONFLICT";
    public static final String REPOST_CYCLE_DETECTED_CODE = "REPOST_CYCLE_DETECTED";

    public static final String REPOST_NOT_ALLOWED_ERROR = "리포스트 정책에 위배됩니다.";
    public static final String REPOST_NOT_ALLOWED_BLOCKED_ERROR = "차단된 사용자의 게시글은 리포스트할 수 없습니다.";
    public static final String REPOST_NOT_ALLOWED_PRIVATE_ERROR = "비공개 계정의 게시글은 리포스트할 수 없습니다.";
    public static final String REPOST_CYCLE_DETECTED_ERROR = "리포스트 대상이 비정상적으로 설정되어 있습니다.";
    public static final String REPOST_NOT_ALLOWED_SELF_ERROR = "자신의 게시글은 리포스트할 수 없습니다.";
    public static final String REPOST_QUOTE_NOT_ALLOWED_ERROR = "리포스트된 글은 인용할 수 없습니다.";
    public static final String REPOST_NOT_A_REPOST_ERROR = "리포스트가 아닌 게시글입니다.";
    public static final String REPOST_TARGET_NOT_FOUND_ERROR = "요청한 리포스트 대상 게시글을 찾을 수 없습니다.";
    public static final String REPOST_CONFLICT_ERROR = "동일한 리포스트 요청이 중복 처리되었습니다.";

    public static final String GLOBAL_TEAM_ID = "ALLSTAR1";
    
    private CheerServiceConstants() {
        // 유틸리티 클래스이므로 인스턴스화 방지
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
