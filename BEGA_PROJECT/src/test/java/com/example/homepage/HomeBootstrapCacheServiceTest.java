package com.example.homepage;

import static com.example.common.config.CacheConfig.HOME_BOOTSTRAP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.config.RedisConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

class HomeBootstrapCacheServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-07T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    @DisplayName("bootstrap cache hit은 loader를 호출하지 않고 즉시 반환한다")
    void getOrLoadReturnsCachedBootstrapWithoutCallingLoader() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(HOME_BOOTSTRAP);
        HomeBootstrapCacheService service =
                new HomeBootstrapCacheService(cacheManager, FIXED_CLOCK, new SimpleMeterRegistry());
        LocalDate selectedDate = LocalDate.of(2026, 6, 7);
        String cacheKey = service.buildCacheKey(selectedDate);
        HomeBootstrapResponseDto cachedResponse = sampleResponse(false, false);
        cacheManager.getCache(HOME_BOOTSTRAP).put(cacheKey, cachedResponse);
        AtomicInteger loaderCalls = new AtomicInteger();

        HomeBootstrapResponseDto response = service.getOrLoad(selectedDate, () -> {
            loaderCalls.incrementAndGet();
            return sampleResponse(false, false);
        });

        assertThat(response).isSameAs(cachedResponse);
        assertThat(loaderCalls).hasValue(0);
    }

    @Test
    @DisplayName("bootstrap cache miss는 loader 결과를 cache에 저장한다")
    void getOrLoadStoresLoadedBootstrapOnMiss() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(HOME_BOOTSTRAP);
        HomeBootstrapCacheService service =
                new HomeBootstrapCacheService(cacheManager, FIXED_CLOCK, new SimpleMeterRegistry());
        LocalDate selectedDate = LocalDate.of(2026, 6, 7);
        String cacheKey = service.buildCacheKey(selectedDate);
        HomeBootstrapResponseDto loadedResponse = sampleResponse(false, false);
        AtomicInteger loaderCalls = new AtomicInteger();

        HomeBootstrapResponseDto response = service.getOrLoad(selectedDate, () -> {
            loaderCalls.incrementAndGet();
            return loadedResponse;
        });

        assertThat(response).isSameAs(loadedResponse);
        assertThat(loaderCalls).hasValue(1);
        assertThat(cacheManager.getCache(HOME_BOOTSTRAP).get(cacheKey, HomeBootstrapResponseDto.class))
                .isSameAs(loadedResponse);
    }

    @Test
    @DisplayName("fallback bootstrap 응답은 짧은 TTL cache에 저장해 반복 DB 조회를 막는다")
    void getOrLoadStoresFallbackBootstrapToStopRepeatedDbLoad() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(HOME_BOOTSTRAP);
        HomeBootstrapCacheService service =
                new HomeBootstrapCacheService(cacheManager, FIXED_CLOCK, new SimpleMeterRegistry());
        LocalDate selectedDate = LocalDate.of(2026, 6, 7);
        String cacheKey = service.buildCacheKey(selectedDate);
        AtomicInteger loaderCalls = new AtomicInteger();
        HomeBootstrapResponseDto fallbackResponse = sampleResponse(true, false);

        HomeBootstrapResponseDto firstResponse = service.getOrLoad(selectedDate, () -> {
            loaderCalls.incrementAndGet();
            return fallbackResponse;
        });
        HomeBootstrapResponseDto secondResponse = service.getOrLoad(selectedDate, () -> {
            loaderCalls.incrementAndGet();
            return sampleResponse(false, false);
        });

        assertThat(firstResponse).isSameAs(fallbackResponse);
        assertThat(secondResponse).isSameAs(fallbackResponse);
        assertThat(loaderCalls).hasValue(1);
        assertThat(cacheManager.getCache(HOME_BOOTSTRAP).get(cacheKey, HomeBootstrapResponseDto.class))
                .isSameAs(fallbackResponse);
    }

    @Test
    @DisplayName("timeout bootstrap 응답은 짧은 TTL cache에 저장해 반복 timeout cascade를 막는다")
    void getOrLoadStoresTimedOutBootstrapToStopRepeatedTimeoutCascade() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(HOME_BOOTSTRAP);
        HomeBootstrapCacheService service =
                new HomeBootstrapCacheService(cacheManager, FIXED_CLOCK, new SimpleMeterRegistry());
        LocalDate selectedDate = LocalDate.of(2026, 6, 7);
        String cacheKey = service.buildCacheKey(selectedDate);
        AtomicInteger loaderCalls = new AtomicInteger();
        HomeBootstrapResponseDto timedOutResponse = sampleResponse(false, true);

        HomeBootstrapResponseDto firstResponse = service.getOrLoad(selectedDate, () -> {
            loaderCalls.incrementAndGet();
            return timedOutResponse;
        });
        HomeBootstrapResponseDto secondResponse = service.getOrLoad(selectedDate, () -> {
            loaderCalls.incrementAndGet();
            return sampleResponse(false, false);
        });

        assertThat(firstResponse).isSameAs(timedOutResponse);
        assertThat(secondResponse).isSameAs(timedOutResponse);
        assertThat(loaderCalls).hasValue(1);
        assertThat(cacheManager.getCache(HOME_BOOTSTRAP).get(cacheKey, HomeBootstrapResponseDto.class))
                .isSameAs(timedOutResponse);
    }

    @Test
    @DisplayName("section 실패 목록이 있어도 bootstrap 응답을 cache에 저장한다")
    void getOrLoadStoresBootstrapWhenFailedSectionsArePresent() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(HOME_BOOTSTRAP);
        HomeBootstrapCacheService service =
                new HomeBootstrapCacheService(cacheManager, FIXED_CLOCK, new SimpleMeterRegistry());
        LocalDate selectedDate = LocalDate.of(2026, 6, 7);
        String cacheKey = service.buildCacheKey(selectedDate);
        HomeBootstrapResponseDto failedSectionResponse = sampleResponse(false, false, List.of(), List.of("games"));

        service.getOrLoad(selectedDate, () -> failedSectionResponse);

        assertThat(cacheManager.getCache(HOME_BOOTSTRAP).get(cacheKey, HomeBootstrapResponseDto.class))
                .isSameAs(failedSectionResponse);
    }

    @Test
    @DisplayName("section timeout 목록이 있어도 bootstrap 응답을 cache에 저장한다")
    void getOrLoadStoresBootstrapWhenTimedOutSectionsArePresent() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(HOME_BOOTSTRAP);
        HomeBootstrapCacheService service =
                new HomeBootstrapCacheService(cacheManager, FIXED_CLOCK, new SimpleMeterRegistry());
        LocalDate selectedDate = LocalDate.of(2026, 6, 7);
        String cacheKey = service.buildCacheKey(selectedDate);
        HomeBootstrapResponseDto timedOutSectionResponse =
                sampleResponse(false, false, List.of("games"), List.of());

        service.getOrLoad(selectedDate, () -> timedOutSectionResponse);

        assertThat(cacheManager.getCache(HOME_BOOTSTRAP).get(cacheKey, HomeBootstrapResponseDto.class))
                .isSameAs(timedOutSectionResponse);
    }

    @Test
    @DisplayName("cache에 남아있는 partial bootstrap 응답은 TTL 동안 재사용한다")
    void getOrLoadReusesCachedPartialBootstrapWithinTtl() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(HOME_BOOTSTRAP);
        HomeBootstrapCacheService service =
                new HomeBootstrapCacheService(cacheManager, FIXED_CLOCK, new SimpleMeterRegistry());
        LocalDate selectedDate = LocalDate.of(2026, 6, 7);
        String cacheKey = service.buildCacheKey(selectedDate);
        HomeBootstrapResponseDto staleResponse = sampleResponse(false, true);
        HomeBootstrapResponseDto recoveredResponse = sampleResponse(false, false);
        cacheManager.getCache(HOME_BOOTSTRAP).put(cacheKey, staleResponse);
        AtomicInteger loaderCalls = new AtomicInteger();

        HomeBootstrapResponseDto response = service.getOrLoad(selectedDate, () -> {
            loaderCalls.incrementAndGet();
            return recoveredResponse;
        });

        assertThat(response).isSameAs(staleResponse);
        assertThat(loaderCalls).hasValue(0);
        assertThat(cacheManager.getCache(HOME_BOOTSTRAP).get(cacheKey, HomeBootstrapResponseDto.class))
                .isSameAs(staleResponse);
    }

    @Test
    @DisplayName("cache lookup 실패는 loader fallback으로 처리하고 성공 응답은 저장을 시도한다")
    void getOrLoadFallsBackToLoaderWhenCacheLookupFails() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        HomeBootstrapCacheService service =
                new HomeBootstrapCacheService(cacheManager, FIXED_CLOCK, new SimpleMeterRegistry());
        LocalDate selectedDate = LocalDate.of(2026, 6, 7);
        String cacheKey = service.buildCacheKey(selectedDate);
        HomeBootstrapResponseDto loadedResponse = sampleResponse(false, false);
        when(cacheManager.getCache(HOME_BOOTSTRAP)).thenReturn(cache);
        when(cache.get(eq(cacheKey), eq(HomeBootstrapResponseDto.class)))
                .thenThrow(new IllegalStateException("cache unavailable"));

        HomeBootstrapResponseDto response = service.getOrLoad(selectedDate, () -> loadedResponse);

        assertThat(response).isSameAs(loadedResponse);
        verify(cache).put(eq(cacheKey), same(loadedResponse));
    }

    @Test
    @DisplayName("bootstrap 응답 DTO는 Redis serializer roundtrip이 가능하다")
    void homeBootstrapResponseDtoSupportsRedisSerializerRoundTrip() {
        GenericJackson2JsonRedisSerializer serializer =
                new RedisConfig().redisValueSerializer(new ObjectMapper());
        HomeBootstrapResponseDto response = sampleResponse(false, false);

        Object deserialized = serializer.deserialize(serializer.serialize(response));

        assertThat(deserialized).isInstanceOf(HomeBootstrapResponseDto.class);
        HomeBootstrapResponseDto roundTripped = (HomeBootstrapResponseDto) deserialized;
        assertThat(roundTripped.getSelectedDate()).isEqualTo("2026-06-07");
        assertThat(roundTripped.getLeagueStartDates().getRegularSeasonStart()).isEqualTo("2026-03-28");
        assertThat(roundTripped.getNavigation().isHasNext()).isTrue();
        assertThat(roundTripped.getGames()).hasSize(1);
        assertThat(roundTripped.getScheduledGamesWindow()).hasSize(1);
        assertThat(roundTripped.getLoadState().getIsFallback()).isFalse();
    }

    private HomeBootstrapResponseDto sampleResponse(boolean fallback, boolean timedOut) {
        return sampleResponse(
                fallback,
                timedOut,
                timedOut ? List.of("games") : List.of(),
                fallback ? List.of("games") : List.of());
    }

    private HomeBootstrapResponseDto sampleResponse(
            boolean fallback,
            boolean timedOut,
            List<String> timedOutSections,
            List<String> failedSections) {
        return HomeBootstrapResponseDto.builder()
                .selectedDate("2026-06-07")
                .leagueStartDates(LeagueStartDatesDto.builder()
                        .regularSeasonStart("2026-03-28")
                        .postseasonStart("2026-10-06")
                        .koreanSeriesStart("2026-10-26")
                        .build())
                .navigation(HomeScheduleNavigationDto.builder()
                        .prevGameDate("2026-06-06")
                        .nextGameDate("2026-06-08")
                        .hasPrev(true)
                        .hasNext(true)
                        .build())
                .games(List.of(HomePageGameDto.builder()
                        .gameId("20260607LGKT0")
                        .gameDate("2026-06-07")
                        .sourceDate("2026-06-07")
                        .time("17:00")
                        .stadium("잠실야구장")
                        .leagueType("REGULAR")
                        .homeTeam("LG")
                        .awayTeam("KT")
                        .build()))
                .scheduledGamesWindow(List.of(HomePageScheduledGameDto.builder()
                        .gameId("20260608LGKT0")
                        .sourceDate("2026-06-08")
                        .leagueBadge("정규시즌")
                        .time("18:30")
                        .homeTeam("LG")
                        .awayTeam("KT")
                        .build()))
                .loadState(HomeBootstrapLoadStateDto.builder()
                        .isFallback(fallback)
                        .timedOut(timedOut)
                        .timedOutSections(timedOutSections)
                        .failedSections(failedSections)
                        .build())
                .build();
    }
}
