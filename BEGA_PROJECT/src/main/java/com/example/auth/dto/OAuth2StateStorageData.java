package com.example.auth.dto;

/**
 * OAuth2 인증 상태 Redis 저장용 DTO
 * [Security Fix] 민감 정보 최소화 - userId만 저장
 */
public record OAuth2StateStorageData(
    Long userId,
    long createdAt
) {
    public static OAuth2StateStorageData of(Long userId) {
        return new OAuth2StateStorageData(userId, System.currentTimeMillis());
    }

    /**
     * 5분 만료 확인
     */
    public boolean isExpired() {
        long fiveMinutesInMillis = 5 * 60 * 1000L;
        return System.currentTimeMillis() - createdAt > fiveMinutesInMillis;
    }
}
