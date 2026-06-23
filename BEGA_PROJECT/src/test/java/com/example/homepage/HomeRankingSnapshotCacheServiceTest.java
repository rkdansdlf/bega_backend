package com.example.homepage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class HomeRankingSnapshotCacheServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-07T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    @DisplayName("동일 자동 날짜 ranking snapshot은 loader를 한 번만 실행한다")
    void getOrLoadCachesAutoDateSnapshot() {
        HomeRankingSnapshotCacheService cacheService = newCacheService();
        AtomicInteger loaderCalls = new AtomicInteger();
        LocalDate selectedDate = LocalDate.of(2026, 6, 7);

        HomeRankingSnapshotDto first = cacheService.getOrLoad(selectedDate, null, () -> {
            loaderCalls.incrementAndGet();
            return successfulSnapshot(2026, "LG");
        });
        HomeRankingSnapshotDto second = cacheService.getOrLoad(selectedDate, null, () -> {
            loaderCalls.incrementAndGet();
            return successfulSnapshot(2026, "KT");
        });

        assertThat(loaderCalls).hasValue(1);
        assertThat(first.getRankings()).hasSize(1);
        assertThat(second.getRankings().get(0).getTeamId()).isEqualTo("LG");
    }

    @Test
    @DisplayName("fallback ranking snapshot은 캐시하지 않는다")
    void getOrLoadDoesNotCacheFallbackSnapshot() {
        HomeRankingSnapshotCacheService cacheService = newCacheService();
        AtomicInteger loaderCalls = new AtomicInteger();
        LocalDate selectedDate = LocalDate.of(2026, 6, 7);

        cacheService.getOrLoad(selectedDate, null, () -> {
            loaderCalls.incrementAndGet();
            return fallbackSnapshot(2026);
        });
        cacheService.getOrLoad(selectedDate, null, () -> {
            loaderCalls.incrementAndGet();
            return successfulSnapshot(2026, "LG");
        });

        assertThat(loaderCalls).hasValue(2);
    }

    @Test
    @DisplayName("자동 날짜와 명시 시즌 ranking snapshot cache key를 분리한다")
    void buildCacheKeySeparatesAutoDateAndSeason() {
        HomeRankingSnapshotCacheService cacheService = newCacheService();

        assertThat(cacheService.buildCacheKey(LocalDate.of(2026, 6, 7), null))
                .isEqualTo("auto:2026-06-07");
        assertThat(cacheService.buildCacheKey(LocalDate.of(2026, 6, 7), 2025))
                .isEqualTo("season:2025");
        assertThat(cacheService.buildCacheKey(null, null))
                .isEqualTo("auto:2026-06-07");
    }

    @Test
    @DisplayName("cache lookup 실패 시에도 loader 결과를 반환한다")
    void getOrLoadReturnsLoaderResponseWhenLookupFails() {
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getCache("homeRankingSnapshot")).thenThrow(new IllegalStateException("cache unavailable"));
        HomeRankingSnapshotCacheService cacheService = new HomeRankingSnapshotCacheService(
                cacheManager,
                FIXED_CLOCK,
                new SimpleMeterRegistry());

        HomeRankingSnapshotDto response = cacheService.getOrLoad(
                LocalDate.of(2026, 6, 7),
                null,
                () -> successfulSnapshot(2026, "LG"));

        assertThat(response.getRankingSeasonYear()).isEqualTo(2026);
        assertThat(response.getRankings()).hasSize(1);
    }

    @Test
    @DisplayName("cache store 실패 시에도 loader 결과를 반환한다")
    void getOrLoadReturnsLoaderResponseWhenStoreFails() {
        Cache cache = mock(Cache.class);
        when(cache.get("auto:2026-06-07", HomeRankingSnapshotDto.class)).thenReturn(null);
        org.mockito.Mockito.doThrow(new IllegalStateException("store failed"))
                .when(cache)
                .put(org.mockito.Mockito.eq("auto:2026-06-07"), org.mockito.Mockito.any(HomeRankingSnapshotDto.class));
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getCache("homeRankingSnapshot")).thenReturn(cache);
        HomeRankingSnapshotCacheService cacheService = new HomeRankingSnapshotCacheService(
                cacheManager,
                FIXED_CLOCK,
                new SimpleMeterRegistry());

        HomeRankingSnapshotDto response = cacheService.getOrLoad(
                LocalDate.of(2026, 6, 7),
                null,
                () -> successfulSnapshot(2026, "LG"));

        assertThat(response.getRankingSeasonYear()).isEqualTo(2026);
        assertThat(response.getRankings()).hasSize(1);
    }

    private HomeRankingSnapshotCacheService newCacheService() {
        return new HomeRankingSnapshotCacheService(
                new ConcurrentMapCacheManager("homeRankingSnapshot"),
                FIXED_CLOCK,
                new SimpleMeterRegistry());
    }

    private HomeRankingSnapshotDto successfulSnapshot(int seasonYear, String teamId) {
        return HomeRankingSnapshotDto.builder()
                .rankingSeasonYear(seasonYear)
                .rankingSourceMessage(seasonYear + " 시즌 순위 데이터")
                .isOffSeason(false)
                .rankings(List.of(HomePageTeamRankingDto.builder()
                        .rank(1)
                        .teamId(teamId)
                        .teamName(teamId)
                        .wins(80)
                        .losses(50)
                        .draws(2)
                        .winRate("0.615")
                        .games(132)
                        .gamesBehind(0.0)
                        .build()))
                .build();
    }

    private HomeRankingSnapshotDto fallbackSnapshot(int seasonYear) {
        return HomeRankingSnapshotDto.builder()
                .rankingSeasonYear(seasonYear)
                .rankingSourceMessage("순위 데이터를 불러오지 못했습니다.")
                .isOffSeason(false)
                .rankings(List.of())
                .build();
    }
}
