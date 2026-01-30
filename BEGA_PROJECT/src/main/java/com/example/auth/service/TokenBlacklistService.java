package com.example.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * JWT 토큰 블랙리스트 서비스
 * 로그아웃된 토큰을 Redis에 저장하여 재사용 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;

    private static final String PREFIX = "token:blacklist:";

    /**
     * 토큰을 블랙리스트에 추가
     * @param token JWT 토큰
     * @param expiryMs 토큰의 남은 유효기간 (밀리초)
     */
    public void blacklist(String token, long expiryMs) {
        if (token == null || token.isBlank()) {
            return;
        }

        // 토큰이 이미 만료되었으면 블랙리스트에 추가할 필요 없음
        if (expiryMs <= 0) {
            log.debug("Token already expired, skipping blacklist");
            return;
        }

        String key = PREFIX + token;
        redisTemplate.opsForValue().set(key, "revoked", Duration.ofMillis(expiryMs));
        log.info("Token blacklisted for {} ms", expiryMs);
    }

    /**
     * 토큰이 블랙리스트에 있는지 확인
     * @param token JWT 토큰
     * @return 블랙리스트에 있으면 true
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String key = PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
