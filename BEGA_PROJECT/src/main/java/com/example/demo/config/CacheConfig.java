package com.example.demo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 캐시 설정 클래스
 * 
 * Caffeine 캐시를 사용하여 자주 조회되는 데이터를 메모리에 캐싱합니다.
 * 이를 통해 데이터베이스 부하를 줄이고 응답 시간을 개선합니다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 캐시 이름 상수
     */
    public static final String TEAM_RANKINGS = "teamRankings";
    public static final String LEAGUE_DATES = "leagueDates";
    public static final String STADIUMS = "stadiums";
    public static final String TEAM_DATA = "teamData";
    public static final String GAME_SCHEDULE = "gameSchedule";
    public static final String JWT_USER_CACHE = "jwtUserCache";

    @Bean
    @SuppressWarnings("null")
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                JWT_USER_CACHE, // JWT 토큰 캐시
                TEAM_RANKINGS, // 팀 순위 캐시
                LEAGUE_DATES, // 리그 시작일 캐시
                STADIUMS, // 구장 정보 캐시
                TEAM_DATA, // 팀 정보 캐시
                GAME_SCHEDULE // 경기 일정 캐시
        );

        // 기본 캐시 설정: 10분 TTL, 최대 1000개 항목
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats() // 캐시 통계 기록 (모니터링용)
        );

        return cacheManager;
    }
}