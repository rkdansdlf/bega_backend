package com.example.ai.service;

import static com.example.common.config.CacheConfig.GAME_SCHEDULE;
import static com.example.common.config.CacheConfig.HOME_BOOTSTRAP;
import static com.example.common.config.CacheConfig.HOME_RANKING_SNAPSHOT;
import static com.example.common.config.CacheConfig.HOME_WIDGETS;
import static com.example.common.config.CacheConfig.TEAM_RANKINGS;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BaseballReadCacheInvalidator {

    private static final List<String> BASEBALL_READ_CACHES = List.of(
            GAME_SCHEDULE,
            TEAM_RANKINGS,
            HOME_BOOTSTRAP,
            HOME_WIDGETS,
            HOME_RANKING_SNAPSHOT);

    private final CacheManager cacheManager;

    public void invalidateAll() {
        for (String cacheName : BASEBALL_READ_CACHES) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
        log.info("Invalidated baseball read caches count={}", BASEBALL_READ_CACHES.size());
    }
}
