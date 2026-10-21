package com.example.kbo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.kbo.repository.GameRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeagueStageResolverTest {

    private static final int PLAYOFF = 4;
    private static final int KOREAN_SERIES = 5;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    private GameRepository gameRepository;

    @Test
    @DisplayName("현재 시즌의 빈 단계 시작일은 짧은 TTL 동안만 캐시되고, 만료 후 새 시작일을 반영한 뒤 영구 캐시한다")
    void currentSeasonEmptyStageStartDateIsCachedOnlyForShortTtl() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-27T03:00:00Z"), SEOUL);
        LeagueStageResolver resolver = new LeagueStageResolver(gameRepository, clock);
        LocalDate gameDate = LocalDate.of(2026, 10, 27);
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(anyInt(), eq(2026)))
                .thenReturn(Optional.empty());
        when(gameRepository.findFirstStartDateByTypeFromSeasonYear(anyInt(), eq(2026)))
                .thenReturn(Optional.empty());

        assertThat(resolve(resolver, gameDate)).isEqualTo(PLAYOFF);

        when(gameRepository.findFirstStartDateByTypeFromSeasonYear(KOREAN_SERIES, 2026))
                .thenReturn(Optional.of(LocalDate.of(2026, 10, 26)));

        clock.advance(Duration.ofSeconds(60));
        assertThat(resolve(resolver, gameDate)).isEqualTo(PLAYOFF);
        verify(gameRepository, times(1)).findFirstStartDateByTypeFromSeasonYear(KOREAN_SERIES, 2026);

        clock.advance(LeagueStageResolver.EMPTY_STAGE_START_DATE_TTL);
        assertThat(resolve(resolver, gameDate)).isEqualTo(KOREAN_SERIES);
        verify(gameRepository, times(2)).findFirstStartDateByTypeFromSeasonYear(KOREAN_SERIES, 2026);

        clock.advance(Duration.ofDays(1));
        assertThat(resolve(resolver, gameDate)).isEqualTo(KOREAN_SERIES);
        verify(gameRepository, times(2)).findFirstStartDateByTypeFromSeasonYear(KOREAN_SERIES, 2026);
        verify(gameRepository, times(3)).findFirstStartDateByTypeFromSeasonYear(PLAYOFF, 2026);
    }

    @Test
    @DisplayName("지난 시즌의 빈 단계 시작일은 영구 캐시되어 TTL이 지나도 다시 조회하지 않는다")
    void pastSeasonEmptyStageStartDateIsCachedPermanently() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-27T03:00:00Z"), SEOUL);
        LeagueStageResolver resolver = new LeagueStageResolver(gameRepository, clock);
        LocalDate gameDate = LocalDate.of(2025, 10, 27);
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(anyInt(), eq(2025)))
                .thenReturn(Optional.empty());
        when(gameRepository.findFirstStartDateByTypeFromSeasonYear(anyInt(), eq(2025)))
                .thenReturn(Optional.empty());

        assertThat(resolver.resolveEffectiveLeagueTypeCode(PLAYOFF, gameDate, 20254, "20251027LGHH0"))
                .isEqualTo(PLAYOFF);
        clock.advance(Duration.ofHours(6));
        assertThat(resolver.resolveEffectiveLeagueTypeCode(PLAYOFF, gameDate, 20254, "20251027LGHH0"))
                .isEqualTo(PLAYOFF);

        verify(gameRepository, times(1)).findFirstStartDateByTypeFromSeasonYear(KOREAN_SERIES, 2025);
        verify(gameRepository, times(1)).findConfiguredStartDateByTypeFromSeasonYear(KOREAN_SERIES, 2025);
    }

    private Integer resolve(LeagueStageResolver resolver, LocalDate gameDate) {
        return resolver.resolveEffectiveLeagueTypeCode(PLAYOFF, gameDate, 20264, "20261027LGHH0");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
