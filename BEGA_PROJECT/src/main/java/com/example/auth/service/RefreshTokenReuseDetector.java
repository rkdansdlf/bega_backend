package com.example.auth.service;

import com.example.auth.util.JWTUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * [Security Fix - Medium #4] Refresh Token 재사용 탐지.
 * A07 — Identification and Authentication Failures / CWE-287.
 *
 * <p>회전된(rotated-out) refresh token의 SHA-256 해시를 Redis에 기록하고,
 * 동일 값이 다시 제시되면 탈취 후 재생 공격으로 간주하여 세션 revoke를 트리거한다.</p>
 *
 * <p>저장 형식: {@code refresh:rotated:{hash}} → userId (TTL = refresh token 수명)</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenReuseDetector {

    private final StringRedisTemplate redisTemplate;

    private static final String PREFIX = "refresh:rotated:";
    private static final String SESSION_MARKER_VERSION = "v1:";

    public record ReuseMarker(Long userId, String sessionId) {
    }

    /**
     * 회전된 토큰을 기록한다. 이후 동일 토큰이 다시 제시되면 재사용으로 탐지된다.
     *
     * @param rotatedToken 회전되어 무효화된 원본 refresh token
     * @param userId 해당 토큰 소유자 userId
     * @param ttlMillis refresh token 수명(ms). 이 기간 후에는 흔적 삭제.
     */
    public void markRotated(String rotatedToken, Long userId, String sessionId, long ttlMillis) {
        if (rotatedToken == null || rotatedToken.isBlank()) {
            return;
        }
        if (userId == null || sessionId == null || sessionId.isBlank() || ttlMillis <= 0) {
            throw new IllegalStateException("Refresh token session mapping is unavailable");
        }
        try {
            String key = PREFIX + JWTUtil.hashKey(rotatedToken);
            String encodedSessionId = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
            redisTemplate.opsForValue().set(
                    key,
                    SESSION_MARKER_VERSION + userId + ":" + encodedSessionId,
                    Duration.ofMillis(ttlMillis));
        } catch (Exception e) {
            log.warn("Failed to persist refresh token session mapping: {}", e.getClass().getSimpleName());
            throw new IllegalStateException("Refresh token session mapping is unavailable", e);
        }
    }

    /**
     * 제시된 refresh token이 과거에 회전된 적 있는지 확인한다.
     *
     * @return 해당 토큰이 회전된 기록이 있으면 userId, 없으면 Optional.empty()
     */
    public Optional<ReuseMarker> findReuseMarker(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return Optional.empty();
        }
        try {
            String key = PREFIX + JWTUtil.hashKey(presentedToken);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            if (!value.startsWith(SESSION_MARKER_VERSION)) {
                return Optional.of(new ReuseMarker(Long.parseLong(value), null));
            }
            String[] parts = value.split(":", 3);
            if (parts.length != 3 || parts[2].isBlank()) {
                return Optional.empty();
            }
            Long userId = Long.parseLong(parts[1]);
            String sessionId = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
            if (sessionId.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new ReuseMarker(userId, sessionId));
        } catch (NumberFormatException e) {
            log.warn("Malformed reuse marker value: {}", e.getMessage());
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            log.warn("Malformed refresh token session mapping: {}", e.getClass().getSimpleName());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to read refresh token reuse marker: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public Optional<Long> findReuseUserId(String presentedToken) {
        return findReuseMarker(presentedToken).map(ReuseMarker::userId);
    }
}
