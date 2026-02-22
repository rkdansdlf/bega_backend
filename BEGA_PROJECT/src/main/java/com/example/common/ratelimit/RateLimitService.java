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
            "  redis.call('zadd', key, now, now) " +
            "  redis.call('expire', key, window) " +
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
        long now = Instant.now().getEpochSecond();
        List<String> keys = Collections.singletonList(key);

        try {
            Long result = redisTemplate.execute(
                    Objects.requireNonNull(limitScript),
                    Objects.requireNonNull(keys),
                    String.valueOf(now),
                    String.valueOf(window),
                    String.valueOf(limit));
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("Error executing rate limit script for key {}: {}", key, e.getMessage());
            // Redis 에러 시 서비스 가용성을 위해 일단 허용 (Fail-open 전략)
            return true;
        }
    }
}
