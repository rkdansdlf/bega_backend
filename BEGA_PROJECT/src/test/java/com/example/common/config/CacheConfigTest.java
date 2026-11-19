package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

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
                        new RedisConfig().redisValueSerializer(new ObjectMapper())));

        assertThat(caffeineCacheManager.getCacheNames())
                .containsExactlyInAnyOrder(
                        CacheConfig.JWT_USER_CACHE,
                        CacheConfig.SIGNED_URLS,
                        CacheConfig.DIARY_STATS,
                        CacheConfig.PREDICTION_MATCH_RANGE,
                        CacheConfig.MATE_POPULAR_SEARCH_TERMS)
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
        Cache matePopularSearchTermsCache = caffeineCacheManager.getCache(CacheConfig.MATE_POPULAR_SEARCH_TERMS);
        assertThat(matePopularSearchTermsCache).isInstanceOf(CaffeineCache.class);
        assertThat(cacheManager.getCache(CacheConfig.MATE_POPULAR_SEARCH_TERMS)).isSameAs(matePopularSearchTermsCache);
        assertThat(caffeineCacheManager.getCache(CacheConfig.HOME_BOOTSTRAP)).isNull();
        assertThat(cacheManager.getCache(CacheConfig.HOME_BOOTSTRAP)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.HOME_WIDGETS)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.HOME_RANKING_SNAPSHOT)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.PREDICTION_MATCH_DAY)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.USER_STATS)).isNotNull();
    }

    @Test
    @DisplayName("리그 시작일 캐시는 12시간이 아니라 짧은 TTL 을 쓴다")
    void leagueDatesCacheUsesShortTtl() {
        CacheConfig cacheConfig = new CacheConfig();
        RedisCacheManager redisCacheManager = (RedisCacheManager) cacheConfig.redisCacheManager(
                mock(RedisConnectionFactory.class),
                new RedisConfig().redisValueSerializer(new ObjectMapper()));

        redisCacheManager.afterPropertiesSet(); // 캐시 생성은 초기화 후 가능하다 (Redis 연결은 하지 않음)

        Cache cache = redisCacheManager.getCache(CacheConfig.LEAGUE_DATES);
        if (cache instanceof TransactionAwareCacheDecorator decorator) {
            cache = decorator.getTargetCache();
        }
        assertThat(cache).isInstanceOf(RedisCache.class);
        Duration ttl = ((RedisCache) cache).getCacheConfiguration()
                .getTtlFunction()
                .getTimeToLive("2025", null);

        assertThat(ttl).isEqualTo(CacheConfig.LEAGUE_DATES_TTL);
        assertThat(ttl).isLessThanOrEqualTo(Duration.ofHours(1));
    }
}
