package com.example.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * L1 + L2 하이브리드 캐시 설정
 *
 * L1 캐시 (Caffeine): 로컬 인메모리, 초저지연, 인스턴스별 독립
 * L2 캐시 (Redis): 분산 캐시, 멀티 인스턴스 환경에서 공유
 *
 * 캐시 조회 순서: L1 → L2 → Database
 */
@Configuration
@EnableCaching
public class CacheConfig {

    // L1 전용 캐시 (Caffeine only) - 인증/로컬 데이터
    public static final String JWT_USER_CACHE = "jwtUserCache";
    public static final String SIGNED_URLS = "signedUrls";

    // L1 + L2 하이브리드 캐시 - 공유 데이터
    public static final String TEAM_RANKINGS = "teamRankings";
    public static final String LEAGUE_DATES = "leagueDates";
    public static final String STADIUMS = "stadiums";
    public static final String TEAM_DATA = "teamData";
    public static final String GAME_SCHEDULE = "gameSchedule";
    public static final String POST_IMAGE_URLS = "postImageUrls";

    // L2 전용 캐시 (Redis only) - 라이브 데이터 (추후 확장용)
    public static final String LIVE_GAME_SCORE = "liveGameScore";
    public static final String LIVE_GAME_STATUS = "liveGameStatus";

    /**
     * Primary CacheManager: L1(Caffeine) + L2(Redis) 조합
     * 캐시 조회 시 Caffeine을 먼저 확인하고, 없으면 Redis에서 조회
     */
    @Bean
    @Primary
    public CacheManager cacheManager(
            CacheManager caffeineCacheManager,
            CacheManager redisCacheManager) {
        CompositeCacheManager compositeCacheManager = new CompositeCacheManager();
        compositeCacheManager.setCacheManagers(
                Arrays.asList(caffeineCacheManager, redisCacheManager)
        );
        compositeCacheManager.setFallbackToNoOpCache(false);
        return compositeCacheManager;
    }

    /**
     * L1 캐시 매니저 (Caffeine)
     * - 로컬 인메모리 캐시
     * - 초저지연 (< 1ms)
     * - 캐시별 TTL 개별 설정
     */
    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // 기본 설정: 2분 TTL, 최대 1000개
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .recordStats());

        // 캐시 이름 등록
        manager.setCacheNames(Arrays.asList(
                JWT_USER_CACHE,
                SIGNED_URLS,
                TEAM_RANKINGS,
                LEAGUE_DATES,
                STADIUMS,
                TEAM_DATA,
                GAME_SCHEDULE,
                POST_IMAGE_URLS
        ));

        return manager;
    }

    /**
     * L2 캐시 매니저 (Redis)
     * - 분산 캐시
     * - 멀티 인스턴스 환경에서 데이터 공유
     * - 캐시별 TTL 개별 설정
     */
    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 Redis 캐시 설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // 캐시별 개별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // L1+L2 하이브리드 캐시 (L2 TTL이 L1보다 길어야 함)
        cacheConfigs.put(TEAM_RANKINGS, defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put(GAME_SCHEDULE, defaultConfig.entryTtl(Duration.ofMinutes(1)));
        cacheConfigs.put(STADIUMS, defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put(LEAGUE_DATES, defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put(TEAM_DATA, defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put(POST_IMAGE_URLS, defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // L2 전용 캐시 - 라이브 데이터 (짧은 TTL)
        cacheConfigs.put(LIVE_GAME_SCORE, defaultConfig.entryTtl(Duration.ofSeconds(10)));
        cacheConfigs.put(LIVE_GAME_STATUS, defaultConfig.entryTtl(Duration.ofSeconds(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }
}
