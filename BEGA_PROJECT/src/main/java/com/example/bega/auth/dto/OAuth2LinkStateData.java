package com.example.bega.auth.dto;

/**
 * OAuth2 계정 연동 상태 데이터
 * state 파라미터를 통해 전달되는 연동 정보
 */
public record OAuth2LinkStateData(
        String mode, // "link" 또는 null
        Long userId, // 연동 대상 사용자 ID
        String redirectUri, // 인증 후 리다이렉트 URI
        long createdAt, // 생성 시각
        String failureReason // [Security Fix] 실패 사유 (null이면 정상)
) {
    /**
     * 연동 모드인지 확인
     */
    public boolean isLinkMode() {
        return "link".equals(mode) && userId != null;
    }

    /**
     * 만료 여부 확인 (5분)
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > 5 * 60 * 1000;
    }
}
