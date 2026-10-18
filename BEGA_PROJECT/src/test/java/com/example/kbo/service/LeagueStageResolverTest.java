package com.example.kbo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.kbo.repository.GameRepository;
import java.time.LocalDate;
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

    @Mock
    private GameRepository gameRepository;

    @Test
    @DisplayName("비어 있던 단계 시작일은 캐시하지 않고 다음 조회에서 새로 생긴 시작일을 반영한 뒤, 확인된 값만 캐시한다")
    void emptyStageStartDateIsNotCachedAndLaterLookupPicksUpNewDate() {
        LeagueStageResolver resolver = new LeagueStageResolver(gameRepository);
        LocalDate gameDate = LocalDate.of(2026, 10, 27);
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(anyInt(), eq(2026)))
                .thenReturn(Optional.empty());
        when(gameRepository.findFirstStartDateByTypeFromSeasonYear(anyInt(), eq(2026)))
                .thenReturn(Optional.empty());

        assertThat(resolver.resolveEffectiveLeagueTypeCode(PLAYOFF, gameDate, 20264, "20261027LGHH0"))
                .isEqualTo(PLAYOFF);

        when(gameRepository.findFirstStartDateByTypeFromSeasonYear(KOREAN_SERIES, 2026))
                .thenReturn(Optional.of(LocalDate.of(2026, 10, 26)));

        assertThat(resolver.resolveEffectiveLeagueTypeCode(PLAYOFF, gameDate, 20264, "20261027LGHH0"))
                .isEqualTo(KOREAN_SERIES);
        assertThat(resolver.resolveEffectiveLeagueTypeCode(PLAYOFF, gameDate, 20264, "20261027LGHH0"))
                .isEqualTo(KOREAN_SERIES);

        verify(gameRepository, times(2)).findFirstStartDateByTypeFromSeasonYear(KOREAN_SERIES, 2026);
        verify(gameRepository, times(3)).findFirstStartDateByTypeFromSeasonYear(PLAYOFF, 2026);
    }
}
