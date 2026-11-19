package com.example.kbo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.kbo.repository.GameRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** LeagueStageResolver 캐시가 만료되어 늦게 확정된 단계 시작일을 다시 읽는지 검증한다. */
class LeagueStageResolverCacheTest {

    private static final int YEAR = 2025;
    private static final LocalDate KOREAN_SERIES_GAME = LocalDate.of(2025, 10, 26);

    private GameRepository gameRepository;
    private MutableClock clock;
    private LeagueStageResolver resolver;

    @BeforeEach
    void setUp() {
        gameRepository = mock(GameRepository.class);
        clock = new MutableClock(Instant.parse("2025-10-01T00:00:00Z"));
        resolver = new LeagueStageResolver(gameRepository, clock);
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        when(gameRepository.findFirstStartDateByTypeFromSeasonYear(anyInt(), anyInt()))
                .thenReturn(Optional.empty());
    }

    @Test
    void unresolvedStartDatesAreCachedBrieflyThenReloaded() {
        resolver.resolveEffectiveLeagueTypeCode(2, KOREAN_SERIES_GAME, YEAR, "G1");
        clock.advance(Duration.ofMinutes(1));
        resolver.resolveEffectiveLeagueTypeCode(2, KOREAN_SERIES_GAME, YEAR, "G1");

        verify(gameRepository, times(1)).findConfiguredStartDateByTypeFromSeasonYear(5, YEAR);

        clock.advance(LeagueStageResolver.UNRESOLVED_TTL);
        resolver.resolveEffectiveLeagueTypeCode(2, KOREAN_SERIES_GAME, YEAR, "G1");

        verify(gameRepository, times(2)).findConfiguredStartDateByTypeFromSeasonYear(5, YEAR);
    }

    @Test
    void startDateConfirmedAfterFirstLookupIsPickedUpWithoutRestart() {
        // 최초 조회 시점에는 한국시리즈 시작일이 비어 있어 경기는 원래 단계(2)로 남는다.
        assertThat(resolver.resolveEffectiveLeagueTypeCode(2, KOREAN_SERIES_GAME, YEAR, "G1")).isEqualTo(2);

        // 이후 시작일이 확정되어도 예전 구현은 서버를 재시작할 때까지 빈 값을 재사용했다.
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(5, YEAR))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 20)));
        clock.advance(LeagueStageResolver.UNRESOLVED_TTL.plusSeconds(1));

        assertThat(resolver.resolveEffectiveLeagueTypeCode(2, KOREAN_SERIES_GAME, YEAR, "G1")).isEqualTo(5);
    }

    @Test
    void fullyResolvedStartDatesAreKeptLongerButStillExpire() {
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(2, YEAR))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 1)));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(3, YEAR))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 5)));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(4, YEAR))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 10)));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(5, YEAR))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 20)));

        resolver.resolveEffectiveLeagueTypeCode(5, KOREAN_SERIES_GAME, YEAR, "G1");
        clock.advance(LeagueStageResolver.UNRESOLVED_TTL.plusMinutes(1));
        resolver.resolveEffectiveLeagueTypeCode(5, KOREAN_SERIES_GAME, YEAR, "G1");

        verify(gameRepository, times(1)).findConfiguredStartDateByTypeFromSeasonYear(5, YEAR);

        clock.advance(LeagueStageResolver.RESOLVED_TTL);
        resolver.resolveEffectiveLeagueTypeCode(5, KOREAN_SERIES_GAME, YEAR, "G1");

        verify(gameRepository, times(2)).findConfiguredStartDateByTypeFromSeasonYear(5, YEAR);
    }

    @Test
    void missingRawLeagueTypeIsNotCachedForever() {
        when(gameRepository.findLeagueTypeCodeBySeasonId(7)).thenReturn(Optional.empty());

        assertThat(resolver.resolveRawLeagueTypeCode(7)).isNull();
        assertThat(resolver.resolveRawLeagueTypeCode(7)).isNull();
        verify(gameRepository, times(1)).findLeagueTypeCodeBySeasonId(7);

        when(gameRepository.findLeagueTypeCodeBySeasonId(7)).thenReturn(Optional.of(5));
        clock.advance(LeagueStageResolver.UNRESOLVED_TTL.plusSeconds(1));

        assertThat(resolver.resolveRawLeagueTypeCode(7)).isEqualTo(5);
    }

    /** 테스트에서 시간을 직접 흘려보내기 위한 시계. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
