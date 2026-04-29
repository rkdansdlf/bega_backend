package com.example.kbo.validation;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameSummaryEntity;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.MatchRangeProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    @DisplayName("과거 경기 row의 상태와 최종 점수가 비어 있으면 수동 데이터 요청 계약을 유지한다")
    void ensurePredictionDateMatchesThrowsWhenPastGameStatusAndScoreMissing() {
        LocalDate gameDate = LocalDate.now().minusDays(1);
        MatchRangeProjection invalidMatch = mock(MatchRangeProjection.class);
        when(invalidMatch.getGameId()).thenReturn("20260426KTSK0");
        when(invalidMatch.getGameDate()).thenReturn(gameDate);
        when(invalidMatch.getHomeTeam()).thenReturn("KT");
        when(invalidMatch.getAwayTeam()).thenReturn("SSG");
        when(invalidMatch.getSeasonId()).thenReturn(gameDate.getYear());
        when(invalidMatch.getRawLeagueTypeCode()).thenReturn(0);
        when(invalidMatch.getGameStatus()).thenReturn("SCHEDULED");
        when(invalidMatch.getHomeScore()).thenReturn(null);
        when(invalidMatch.getAwayScore()).thenReturn(null);

        assertThatThrownBy(() -> baseballDataIntegrityGuard.ensurePredictionDateMatches(
                "prediction.matches_by_date",
                gameDate,
                List.of(invalidMatch)))
                .isInstanceOf(ManualBaseballDataRequiredException.class)
                .satisfies((throwable) -> {
                    ManualBaseballDataRequiredException exception = (ManualBaseballDataRequiredException) throwable;
                    ManualBaseballDataRequest request = (ManualBaseballDataRequest) exception.getData();
                    assertThat(request.scope()).isEqualTo("prediction.matches_by_date");
                    assertThat(request.missingItems()).extracting(ManualBaseballDataMissingItem::key)
                            .contains("game_status", "final_score");
                    assertThat(request.blocking()).isTrue();
                });
    }

    @Test
    @DisplayName("완료 경기 주요 기록이 비어 있으면 수동 데이터 요청 계약을 반환한다")
    void ensurePredictionGameSummaryRecordsThrowsWhenCompletedGameSummaryMissing() {
        LocalDate gameDate = LocalDate.now().minusDays(1);

        assertThatThrownBy(() -> baseballDataIntegrityGuard.ensurePredictionGameSummaryRecords(
                "prediction.game_detail.summary",
                "20260419HHLT0",
                gameDate,
                "COMPLETED",
                8,
                3,
                List.of()))
                .isInstanceOf(ManualBaseballDataRequiredException.class)
                .satisfies((throwable) -> {
                    ManualBaseballDataRequiredException exception = (ManualBaseballDataRequiredException) throwable;
                    assertThat(exception.getCode()).isEqualTo(ManualBaseballDataRequiredException.CODE);
                    ManualBaseballDataRequest request = (ManualBaseballDataRequest) exception.getData();
                    assertThat(request.scope()).isEqualTo("prediction.game_detail.summary");
                    assertThat(request.missingItems()).extracting(ManualBaseballDataMissingItem::key)
                            .containsExactly("game_summary");
                    assertThat(request.blocking()).isTrue();
                });
    }

    @Test
    @DisplayName("완료 경기 주요 기록이 내부 구조화 데이터뿐이면 수동 데이터 요청 계약을 반환한다")
    void ensurePredictionGameSummaryRecordsThrowsWhenCompletedGameSummaryIsNotDisplayable() {
        LocalDate gameDate = LocalDate.now().minusDays(1);
        List<GameSummaryEntity> summaries = List.of(
                GameSummaryEntity.builder()
                        .gameId("20260419HHLT0")
                        .summaryType("리뷰_WPA")
                        .playerName("기록")
                        .detailText("{\"game_id\":\"20260419HHLT0\"}")
                        .build(),
                GameSummaryEntity.builder()
                        .gameId("20260419HHLT0")
                        .summaryType("기타")
                        .detailText("[{\"inning\":\"5회말\"}]")
                        .build());

        assertThatThrownBy(() -> baseballDataIntegrityGuard.ensurePredictionGameSummaryRecords(
                "prediction.game_detail.summary",
                "20260419HHLT0",
                gameDate,
                "COMPLETED",
                8,
                3,
                summaries))
                .isInstanceOf(ManualBaseballDataRequiredException.class)
                .satisfies((throwable) -> {
                    ManualBaseballDataRequiredException exception = (ManualBaseballDataRequiredException) throwable;
                    ManualBaseballDataRequest request = (ManualBaseballDataRequest) exception.getData();
                    assertThat(request.missingItems()).extracting(ManualBaseballDataMissingItem::reason)
                            .containsExactly("주요 기록 row가 표시 가능한 형식이 아니거나 내부 구조화 데이터만 포함합니다.");
                });
    }

    @Test
    @DisplayName("예정 경기는 주요 기록이 없어도 수동 데이터 요청으로 막지 않는다")
    void ensurePredictionGameSummaryRecordsAllowsFutureScheduledGameWithoutSummary() {
        assertThatCode(() -> baseballDataIntegrityGuard.ensurePredictionGameSummaryRecords(
                "prediction.game_detail.summary",
                "20260419HHLT0",
                LocalDate.now().plusDays(1),
                "SCHEDULED",
                null,
                null,
                List.of()))
                .doesNotThrowAnyException();
    }
}
