package com.example.homepage;

import static com.example.common.config.CacheConfig.HOME_WIDGETS;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class HomeWidgetsCacheServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-07T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final String RANKING_FALLBACK_SOURCE_MESSAGE = "순위 데이터를 불러오지 못했습니다.";
    private static final Predicate<HomeWidgetsResponseDto> UNCACHEABLE = response ->
            response == null
                    || response.getRankingSnapshot() == null
                    || RANKING_FALLBACK_SOURCE_MESSAGE.equals(response.getRankingSnapshot().getRankingSourceMessage());

    @Test
    @DisplayName("widgets cache miss는 loader 결과를 저장한다")
    void getOrLoadStoresLoadedWidgetsOnMiss() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(HOME_WIDGETS);
        HomeWidgetsCacheService service =
                new HomeWidgetsCacheService(cacheManager, FIXED_CLOCK, new SimpleMeterRegistry());
        LocalDate selectedDate = LocalDate.of(2026, 6, 7);
        HomeWidgetsResponseDto loadedResponse = successfulWidgets("LG");
        AtomicInteger loaderCalls = new AtomicInteger();

        HomeWidgetsResponseDto first = service.getOrLoad(selectedDate, null, () -> {
            loaderCalls.incrementAndGet();
            return loadedResponse;
        }, UNCACHEABLE);
        HomeWidgetsResponseDto second = service.getOrLoad(selectedDate, null, () -> {
            loaderCalls.incrementAndGet();
            return successfulWidgets("KT");
        }, UNCACHEABLE);

        assertThat(first).isSameAs(loadedResponse);
        assertThat(second).isSameAs(loadedResponse);
        assertThat(loaderCalls).hasValue(1);
        assertThat(cacheManager.getCache(HOME_WIDGETS).get("2026-06-07:auto", HomeWidgetsResponseDto.class))
                .isSameAs(loadedResponse);
    }

    @Test
    @DisplayName("fallback ranking widgets 응답은 main cache 대신 짧은 로컬 cache에 저장한다")
    void getOrLoadStoresFallbackRankingWidgetsOnlyInShortLocalCache() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(HOME_WIDGETS);
        HomeWidgetsCacheService service =
                new HomeWidgetsCacheService(cacheManager, FIXED_CLOCK, new SimpleMeterRegistry());
        LocalDate selectedDate = LocalDate.of(2026, 6, 7);
        AtomicInteger loaderCalls = new AtomicInteger();

        HomeWidgetsResponseDto first = service.getOrLoad(selectedDate, null, () -> {
            loaderCalls.incrementAndGet();
            return fallbackWidgets();
        }, UNCACHEABLE);
        HomeWidgetsResponseDto second = service.getOrLoad(selectedDate, null, () -> {
            loaderCalls.incrementAndGet();
            return successfulWidgets("LG");
        }, UNCACHEABLE);

        assertThat(first.getRankingSnapshot().getRankings()).isEmpty();
        assertThat(second).isSameAs(first);
        assertThat(second.getRankingSnapshot().getRankings()).isEmpty();
        assertThat(loaderCalls).hasValue(1);
        assertThat(cacheManager.getCache(HOME_WIDGETS).get("2026-06-07:auto", HomeWidgetsResponseDto.class))
                .isNull();
    }

    @Test
    @DisplayName("동시 widgets cache miss는 loader를 한 번만 실행한다")
    void getOrLoadCoalescesConcurrentMisses() throws Exception {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(HOME_WIDGETS);
        HomeWidgetsCacheService service =
                new HomeWidgetsCacheService(cacheManager, FIXED_CLOCK, new SimpleMeterRegistry());
        LocalDate selectedDate = LocalDate.of(2026, 6, 7);
        HomeWidgetsResponseDto loadedResponse = successfulWidgets("LG");
        AtomicInteger loaderCalls = new AtomicInteger();
        int workers = 4;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);

        try {
            Callable<HomeWidgetsResponseDto> task = () -> {
                ready.countDown();
                start.await(1, TimeUnit.SECONDS);
                return service.getOrLoad(selectedDate, 2026, () -> {
                    loaderCalls.incrementAndGet();
                    sleepBriefly();
                    return loadedResponse;
                }, UNCACHEABLE);
            };

            List<Future<HomeWidgetsResponseDto>> futures = List.of(
                    executor.submit(task),
                    executor.submit(task),
                    executor.submit(task),
                    executor.submit(task));
            assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<HomeWidgetsResponseDto> future : futures) {
                assertThat(future.get(1, TimeUnit.SECONDS)).isSameAs(loadedResponse);
            }
            assertThat(loaderCalls).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("widgets cache key는 자동 날짜와 명시 시즌을 분리한다")
    void buildCacheKeySeparatesAutoAndSeason() {
        HomeWidgetsCacheService service = new HomeWidgetsCacheService(
                new ConcurrentMapCacheManager(HOME_WIDGETS),
                FIXED_CLOCK,
                new SimpleMeterRegistry());

        assertThat(service.buildCacheKey(LocalDate.of(2026, 6, 7), null))
                .isEqualTo("2026-06-07:auto");
        assertThat(service.buildCacheKey(LocalDate.of(2026, 6, 7), 2025))
                .isEqualTo("2026-06-07:2025");
        assertThat(service.buildCacheKey(null, null))
                .isEqualTo("2026-06-07:auto");
    }

    private static void sleepBriefly() {
        try {
            TimeUnit.MILLISECONDS.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private HomeWidgetsResponseDto successfulWidgets(String teamId) {
        return HomeWidgetsResponseDto.builder()
                .hotCheerPosts(List.of())
                .featuredMates(List.of())
                .rankingSnapshot(HomeRankingSnapshotDto.builder()
                        .rankingSeasonYear(2026)
                        .rankingSourceMessage("2026 시즌 순위 데이터")
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
                        .build())
                .build();
    }

    private HomeWidgetsResponseDto fallbackWidgets() {
        return HomeWidgetsResponseDto.builder()
                .hotCheerPosts(List.of())
                .featuredMates(List.of())
                .rankingSnapshot(HomeRankingSnapshotDto.builder()
                        .rankingSeasonYear(2026)
                        .rankingSourceMessage(RANKING_FALLBACK_SOURCE_MESSAGE)
                        .isOffSeason(false)
                        .rankings(List.of())
                        .build())
                .build();
    }
}
