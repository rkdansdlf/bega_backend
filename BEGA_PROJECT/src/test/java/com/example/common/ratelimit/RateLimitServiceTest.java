package com.example.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void redisFailureFallsOpenByDefault() {
        RateLimitService service = new RateLimitService(redisTemplate);
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        assertThat(service.isAllowed("rate:test", 10, 60)).isTrue();
    }

    @Test
    void redisFailureFailsClosedForProtectedAuthEndpoints() {
        RateLimitService service = new RateLimitService(redisTemplate);
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        assertThat(service.isAllowed("rate:test", 10, 60, true)).isFalse();
    }
}
