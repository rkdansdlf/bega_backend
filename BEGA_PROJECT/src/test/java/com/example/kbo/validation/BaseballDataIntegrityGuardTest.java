package com.example.kbo.validation;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class BaseballDataIntegrityGuardTest {

    @Mock
    private GameRepository gameRepository;

    private BaseballDataIntegrityGuard baseballDataIntegrityGuard;

    @BeforeEach
    void setUp() {
        baseballDataIntegrityGuard = new BaseballDataIntegrityGuard(gameRepository);
    }

    @Test
    @DisplayName("홈 일정 row가 있지만 시즌 문맥이 비어 있으면 수동 데이터 요청 계약을 유지한다")
    void ensureHomeGamesByDateThrowsWhenReturnedRowMissesSeasonContext() {
        LocalDate gameDate = LocalDate.of(2026, 4, 14);
        GameEntity invalidGame = GameEntity.builder()
                .gameId("20260414LGKT0")
                .gameDate(gameDate)
                .homeTeam("LG")
                .awayTeam("KT")
                .seasonId(null)
                .gameStatus("SCHEDULED")
                .build();

        assertThatThrownBy(() -> baseballDataIntegrityGuard.ensureHomeGamesByDate(
                "home.schedule",
                gameDate,
                java.util.List.of(invalidGame)))
                .isInstanceOf(ManualBaseballDataRequiredException.class)
                .satisfies((throwable) -> {
                    ManualBaseballDataRequiredException exception = (ManualBaseballDataRequiredException) throwable;
                    assertThat(exception.getCode()).isEqualTo(ManualBaseballDataRequiredException.CODE);
                    assertThat(exception.getData()).isInstanceOf(ManualBaseballDataRequest.class);
                    ManualBaseballDataRequest request = (ManualBaseballDataRequest) exception.getData();
                    assertThat(request.scope()).isEqualTo("home.schedule");
                    assertThat(request.missingItems()).extracting(ManualBaseballDataMissingItem::key)
                            .contains("season_league_context");
                    assertThat(request.blocking()).isTrue();
                });
    }
}
