package com.example.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    // Sliding Window Lua Script
    private static final String LUA_SCRIPT = "local key = KEYS[1] " +
            "local now = tonumber(ARGV[1]) " +
            "local window = tonumber(ARGV[2]) " +
            "local limit = tonumber(ARGV[3]) " +
            "local clearBefore = now - window " +
            "redis.call('zremrangebyscore', key, 0, clearBefore) " +
            "local currentCount = redis.call('zcard', key) " +
            "if currentCount < limit then " +
            "  redis.call('zadd', key, now, ARGV[4]) " +
            "  redis.call('expire', key, math.ceil(window / 1000)) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end";

    private final RedisScript<Long> limitScript = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    /**
     * 특정 키에 대해 요청이 허용되는지 확인
     * 
     * @param key    Redis 키
     * @param limit  허용 요청 수
     * @param window 시간 창 (초)
     * @return 허용 여부
     */
    public boolean isAllowed(String key, int limit, int window) {
        return isAllowed(key, limit, window, false);
    }

    public boolean isAllowed(String key, int limit, int window, boolean failClosed) {
        long now = Instant.now().toEpochMilli();
        long windowMillis = window * 1000L;
        List<String> keys = Collections.singletonList(key);
        String member = now + ":" + UUID.randomUUID();

        try {
            Long result = redisTemplate.execute(
                    Objects.requireNonNull(limitScript),
                    Objects.requireNonNull(keys),
                    String.valueOf(now),
                    String.valueOf(windowMillis),
                    String.valueOf(limit),
                    member);
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("Error executing rate limit script for key {}: {}", key, e.getMessage());
            return !failClosed;
        }
    }
}
