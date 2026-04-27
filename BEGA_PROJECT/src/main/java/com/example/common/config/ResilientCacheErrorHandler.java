package com.example.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.util.StringUtils;

@Slf4j
public class ResilientCacheErrorHandler implements CacheErrorHandler {

    private static final int MAX_REASON_LENGTH = 220;

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("캐시 조회 실패. cache miss로 처리합니다: cache={}, key={}, reason={}",
                getCacheName(cache), key, summarize(exception));
        safeEvict(cache, key);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("캐시 저장 실패. 응답은 계속 진행합니다: cache={}, key={}, reason={}",
                getCacheName(cache), key, summarize(exception));
        safeEvict(cache, key);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("캐시 무효화 실패: cache={}, key={}, reason={}",
                getCacheName(cache), key, summarize(exception));
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("캐시 전체 삭제 실패: cache={}, reason={}", getCacheName(cache), summarize(exception));
    }

    private String getCacheName(Cache cache) {
        return cache == null ? "unknown" : cache.getName();
    }

    private String summarize(RuntimeException exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        String message = rootCause.getMessage();
        if (!StringUtils.hasText(message)) {
            message = rootCause.getClass().getSimpleName();
        }

        String normalized = message.replaceAll("\\s+", " ").trim();
        if (normalized.length() > MAX_REASON_LENGTH) {
            return normalized.substring(0, MAX_REASON_LENGTH) + "...";
        }
        return normalized;
    }

    private void safeEvict(Cache cache, Object key) {
        if (cache == null || key == null) {
            return;
        }
        try {
            cache.evict(key);
        } catch (RuntimeException e) {
            log.warn("캐시 엔트리 무효화 실패: cache={}, key={}, reason={}",
                    getCacheName(cache), key, summarize(e));
        }
    }
}
