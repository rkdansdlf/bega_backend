package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

class CacheConfigTest {

    @Test
    @DisplayName("Caffeine은 로컬 캐시만 보유하고 공유 캐시는 Redis로 위임한다")
    void cacheManagersSeparation() {
        CacheConfig cacheConfig = new CacheConfig();

        CaffeineCacheManager caffeineCacheManager = (CaffeineCacheManager) cacheConfig.caffeineCacheManager();
        CompositeCacheManager cacheManager = (CompositeCacheManager) cacheConfig.cacheManager(
                caffeineCacheManager,
                cacheConfig.redisCacheManager(
                        mock(RedisConnectionFactory.class),
                        new GenericJackson2JsonRedisSerializer()));

        assertThat(caffeineCacheManager.getCacheNames())
                .containsExactlyInAnyOrder(
                        CacheConfig.JWT_USER_CACHE,
                        CacheConfig.SIGNED_URLS,
                        CacheConfig.DIARY_STATS,
                        CacheConfig.PREDICTION_MATCH_RANGE)
                .doesNotContain(
                        CacheConfig.HOME_BOOTSTRAP,
                        CacheConfig.HOME_WIDGETS,
                        CacheConfig.HOME_RANKING_SNAPSHOT,
                        CacheConfig.PREDICTION_MATCH_DAY,
                        CacheConfig.TEAM_RANKINGS,
                        CacheConfig.USER_STATS);
        Cache diaryStatsCache = caffeineCacheManager.getCache(CacheConfig.DIARY_STATS);
        assertThat(diaryStatsCache).isInstanceOf(CaffeineCache.class);
        assertThat(cacheManager.getCache(CacheConfig.DIARY_STATS)).isSameAs(diaryStatsCache);
        Cache predictionMatchRangeCache = caffeineCacheManager.getCache(CacheConfig.PREDICTION_MATCH_RANGE);
        assertThat(predictionMatchRangeCache).isInstanceOf(CaffeineCache.class);
        assertThat(cacheManager.getCache(CacheConfig.PREDICTION_MATCH_RANGE)).isSameAs(predictionMatchRangeCache);
        assertThat(caffeineCacheManager.getCache(CacheConfig.HOME_BOOTSTRAP)).isNull();
        assertThat(cacheManager.getCache(CacheConfig.HOME_BOOTSTRAP)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.HOME_RANKING_SNAPSHOT)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.PREDICTION_MATCH_DAY)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.USER_STATS)).isNotNull();
    }
}
