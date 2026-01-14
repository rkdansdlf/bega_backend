package com.example.demo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @SuppressWarnings("null")
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "jwtUserCache" // 캐시 이름
        );

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10000) // 최대 10,000개 토큰 캐싱
                .expireAfterWrite(60, TimeUnit.MINUTES) // 60분 후 자동 삭제
                .recordStats() // 캐시 통계 기록 (선택)
        );

        return cacheManager;
    }
}