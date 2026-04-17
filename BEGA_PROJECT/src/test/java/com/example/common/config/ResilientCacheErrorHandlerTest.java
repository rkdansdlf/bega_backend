package com.example.common.config;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.serializer.SerializationException;

class ResilientCacheErrorHandlerTest {

    private final ResilientCacheErrorHandler cacheErrorHandler = new ResilientCacheErrorHandler();

    @Test
    @DisplayName("캐시 조회 실패는 miss로 처리하고 손상 키를 무효화한다")
    void handleCacheGetErrorEvictsBrokenEntry() {
        Cache cache = mock(Cache.class);

        cacheErrorHandler.handleCacheGetError(new SerializationException("broken payload"), cache, "demo");

        verify(cache).evict("demo");
    }

    @Test
    @DisplayName("캐시 저장 실패 시에도 예외를 삼키고 손상 키를 무효화한다")
    void handleCachePutErrorEvictsBrokenEntry() {
        Cache cache = mock(Cache.class);

        cacheErrorHandler.handleCachePutError(new SerializationException("write failed"), cache, "demo", "value");

        verify(cache).evict("demo");
    }

    @Test
    @DisplayName("캐시 조회 실패 후 무효화까지 실패해도 추가 예외를 던지지 않는다")
    void handleCacheGetErrorIgnoresEvictionFailure() {
        Cache cache = mock(Cache.class);
        doThrow(new SerializationException("evict failed")).when(cache).evict("demo");

        cacheErrorHandler.handleCacheGetError(new SerializationException("broken payload"), cache, "demo");

        verify(cache).evict("demo");
    }
}
