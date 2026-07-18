package com.example.ai.service;

import static com.example.common.config.CacheConfig.GAME_SCHEDULE;
import static com.example.common.config.CacheConfig.HOME_BOOTSTRAP;
import static com.example.common.config.CacheConfig.HOME_RANKING_SNAPSHOT;
import static com.example.common.config.CacheConfig.HOME_WIDGETS;
import static com.example.common.config.CacheConfig.TEAM_RANKINGS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

class BaseballReadCacheInvalidatorTest {

    @Test
    void invalidateAllClearsEveryBaseballReadCache() {
        CacheManager cacheManager = org.mockito.Mockito.mock(CacheManager.class);
        Map<String, Cache> caches = Map.of(
                GAME_SCHEDULE, org.mockito.Mockito.mock(Cache.class),
                TEAM_RANKINGS, org.mockito.Mockito.mock(Cache.class),
                HOME_BOOTSTRAP, org.mockito.Mockito.mock(Cache.class),
                HOME_WIDGETS, org.mockito.Mockito.mock(Cache.class),
                HOME_RANKING_SNAPSHOT, org.mockito.Mockito.mock(Cache.class));
        caches.forEach((name, cache) -> when(cacheManager.getCache(name)).thenReturn(cache));
        BaseballReadCacheInvalidator invalidator = new BaseballReadCacheInvalidator(cacheManager);

        invalidator.invalidateAll();

        caches.forEach((name, cache) -> verify(cache).clear());
    }
}
