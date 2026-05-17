package com.example.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

        // L1 전용 캐시 (Caffeine only) - 인스턴스 로컬 데이터
        public static final String JWT_USER_CACHE = "jwtUserCache";
        public static final String SIGNED_URLS = "signedUrls";

        // Redis 전용 또는 공유 캐시
        public static final String TEAM_RANKINGS = "teamRankings";
        public static final String LEAGUE_DATES = "leagueDates";
        public static final String STADIUMS = "stadiums";
        public static final String TEAM_DATA = "teamData";
        public static final String GAME_SCHEDULE = "gameSchedule";
        public static final String HOME_BOOTSTRAP = "homeBootstrap";
        public static final String HOME_WIDGETS = "homeWidgets";
        public static final String POST_IMAGE_URLS = "postImageUrls";
        public static final String USER_RANK = "userRank";           // 리더보드 랭킹 (per-user, season rank only)
        public static final String USER_STATS = "userStats";          // 리더보드 전체 통계 (per-user, 4x rank counts)
        public static final String PREDICTION_USER_STATS = "predictionUserStats"; // 예측 적중률/스트릭 통계 (per-user)
        public static final String PREDICTION_VOTE_STATUS = "predictionVoteStatus";
        public static final String RANKING_PREDICTION_CONTEXT = "rankingPredictionContext";
        public static final String RANKING_SHARE_IDS = "rankingShareIds";

        public static final String STADIUM_PLACES = "stadiumPlaces";
        public static final String GAME_DETAIL = "gameDetail";
        public static final String RECENT_COMPLETED_GAMES = "recentCompletedGames";

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
                                Objects.requireNonNull(List.of(caffeineCacheManager, redisCacheManager)));
                compositeCacheManager.setFallbackToNoOpCache(true);
                return compositeCacheManager;
        }

        @Bean
        public CacheErrorHandler cacheErrorHandler() {
                return new ResilientCacheErrorHandler();
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

                // 기본 설정: 50분 TTL, 최대 1000개
                manager.setCaffeine(Objects.requireNonNull(Caffeine.newBuilder()
                                .maximumSize(1000)
                                .expireAfterWrite(50, TimeUnit.MINUTES)
                                .recordStats()));

                // 기본 TTL을 쓰는 인스턴스 로컬 캐시만 등록
                manager.setCacheNames(List.of(
                                SIGNED_URLS));

                // jwtUserCache는 토큰 claims용 민감 캐시이므로 별도 짧은 TTL을 적용한다.
                manager.registerCustomCache(JWT_USER_CACHE,
                                Caffeine.newBuilder()
                                                .maximumSize(1000)
                                                .expireAfterWrite(60, TimeUnit.SECONDS)
                                                .recordStats()
                                                .build());

                return manager;
        }

        /**
         * L2 캐시 매니저 (Redis)
         * - 분산 캐시
         * - 멀티 인스턴스 환경에서 데이터 공유
         * - 캐시별 TTL 개별 설정
         */
        @Bean
        public CacheManager redisCacheManager(
                        RedisConnectionFactory connectionFactory,
                        GenericJackson2JsonRedisSerializer redisValueSerializer) {
                // 기본 Redis 캐시 설정
                RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Objects.requireNonNull(Duration.ofMinutes(5)))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(redisValueSerializer))
                                .disableCachingNullValues();

                // 캐시별 개별 TTL 설정
                Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

                // L1+L2 하이브리드 캐시 (L2 TTL이 L1보다 길어야 함)
                cacheConfigs.put(TEAM_RANKINGS, defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofMinutes(5))));
                cacheConfigs.put(GAME_SCHEDULE, defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofMinutes(1))));
                cacheConfigs.put(STADIUMS, defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofHours(1))));
                cacheConfigs.put(STADIUM_PLACES, defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofHours(1))));
                cacheConfigs.put(GAME_DETAIL, defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofMinutes(30))));
                cacheConfigs.put(RECENT_COMPLETED_GAMES, defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofMinutes(15))));
                cacheConfigs.put(LEAGUE_DATES, defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofHours(1))));
                cacheConfigs.put(TEAM_DATA, defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofMinutes(30))));
                cacheConfigs.put(HOME_BOOTSTRAP,
                                defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofSeconds(60))));
                cacheConfigs.put(HOME_WIDGETS,
                                defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofSeconds(45))));
                cacheConfigs.put(POST_IMAGE_URLS,
                                defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofMinutes(50))));
                cacheConfigs.put(USER_STATS,
                                defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofMinutes(5))));
                cacheConfigs.put(PREDICTION_USER_STATS,
                                defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofMinutes(5))));
                cacheConfigs.put(RANKING_PREDICTION_CONTEXT,
                                defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofMinutes(60))));
                cacheConfigs.put(RANKING_SHARE_IDS,
                                defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofMinutes(30))));

                // L2 전용 캐시 - 라이브 데이터 (짧은 TTL)
                cacheConfigs.put(LIVE_GAME_SCORE,
                                defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofSeconds(10))));
                cacheConfigs.put(LIVE_GAME_STATUS,
                                defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofSeconds(5))));
                cacheConfigs.put(PREDICTION_VOTE_STATUS,
                                defaultConfig.entryTtl(Objects.requireNonNull(Duration.ofSeconds(30))));

                return RedisCacheManager.builder(Objects.requireNonNull(connectionFactory))
                                .cacheDefaults(defaultConfig)
                                .withInitialCacheConfigurations(cacheConfigs)
                                .transactionAware()
                                .build();
        }
}
