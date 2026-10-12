package com.example.leaderboard.service;

import static com.example.leaderboard.support.LeaderboardTestFixtureFactory.prediction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import com.example.leaderboard.entity.GameResultSettlementBaseline;
import com.example.leaderboard.repository.GameResultSettlementBaselineRepository;
import com.example.leaderboard.repository.LegacyPerfectDayDateRepository;
import com.example.prediction.Prediction;
import com.example.prediction.PredictionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameResultSettlementBaselineServiceTest {

    @InjectMocks
    private GameResultSettlementBaselineService service;

    @Mock
    private GameResultSettlementBaselineRepository baselineRepository;

    @Mock
    private LegacyPerfectDayDateRepository legacyPerfectDayDateRepository;

    @Mock
    private PredictionRepository predictionRepository;

    @Mock
    private GameRepository gameRepository;

    @Test
    void processNextBatch_preservesFinishedHistoryAndEnablesOnlyCanonicalUnfinishedGames() {
        LocalDate finishedDate = LocalDate.of(2026, 7, 1);
        LocalDate pendingDate = LocalDate.of(2026, 7, 2);
        LocalDate invalidFinishedDate = LocalDate.of(2026, 7, 3);
        Prediction finishedPrediction = prediction(1L, "finished", "home");
        Prediction pendingPrediction = prediction(2L, "pending", "away");
        Prediction invalidPrediction = prediction(3L, "invalid", "home");
        Prediction invalidFinishedPrediction = prediction(4L, "invalid-finished", "away");
        Long cutoffId = invalidFinishedPrediction.getId();
        GameResultSettlementBaseline baseline = new GameResultSettlementBaseline(1L, cutoffId, 0L, false);

        when(baselineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(baseline));
        when(predictionRepository.findTop500ByIdGreaterThanAndIdLessThanEqualOrderByIdAsc(0L, cutoffId))
                .thenReturn(List.of(finishedPrediction, pendingPrediction, invalidPrediction, invalidFinishedPrediction));
        when(gameRepository.findByGameIdIn(Set.of("finished", "pending", "invalid", "invalid-finished"))).thenReturn(List.of(
                game("finished", "LG", "KIA", finishedDate, 3, 1),
                game("pending", "HH", "SSG", pendingDate, null, null),
                game("invalid", "XX", "YY", pendingDate, null, null),
                game("invalid-finished", "XX", "YY", invalidFinishedDate, 5, 2)));

        boolean complete = service.processNextBatch();

        assertThat(complete).isTrue();
        assertThat(baseline.getLastPredictionId()).isEqualTo(cutoffId);
        assertThat(baseline.isComplete()).isTrue();
        verify(predictionRepository).markPendingForSettlement(
                Set.of(pendingPrediction.getId()),
                Prediction.ScoreSettlementStatus.PENDING);
        verify(predictionRepository).markLegacyForSettlement(
                Set.of(finishedPrediction.getId(), invalidPrediction.getId(), invalidFinishedPrediction.getId()),
                Prediction.ScoreSettlementStatus.LEGACY);
        verify(legacyPerfectDayDateRepository).saveAll(
                org.mockito.ArgumentMatchers.argThat(dates -> {
                    var iterator = dates.iterator();
                    Set<LocalDate> actualDates = new java.util.HashSet<>();
                    while (iterator.hasNext()) {
                        actualDates.add(iterator.next().getGameDate());
                    }
                    return actualDates.equals(Set.of(finishedDate, invalidFinishedDate));
                }));
    }

    @Test
    void processNextBatch_keepsMissingGamePredictionPendingForLaterInternalData() {
        Prediction missingGamePrediction = prediction(7L, "supplied-later", "home");
        Long cutoffId = missingGamePrediction.getId();
        GameResultSettlementBaseline baseline = new GameResultSettlementBaseline(1L, cutoffId, 0L, false);
        when(baselineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(baseline));
        when(predictionRepository.findTop500ByIdGreaterThanAndIdLessThanEqualOrderByIdAsc(0L, cutoffId))
                .thenReturn(List.of(missingGamePrediction));
        when(gameRepository.findByGameIdIn(Set.of("supplied-later"))).thenReturn(List.of());
        when(predictionRepository.findDistinctPendingGameIdsAfter(eq(null),
                eq(Prediction.ScoreSettlementStatus.PENDING), any()))
                .thenReturn(List.of("supplied-later"));

        service.processNextBatch();
        List<String> pendingGameIds = service.reserveNextPendingGameBatch();

        assertThat(pendingGameIds).containsExactly("supplied-later");
        verify(predictionRepository).markPendingForSettlement(
                Set.of(missingGamePrediction.getId()),
                Prediction.ScoreSettlementStatus.PENDING);
        verify(predictionRepository, org.mockito.Mockito.never()).markLegacyForSettlement(
                any(), eq(Prediction.ScoreSettlementStatus.LEGACY));
    }

    @Test
    void processNextBatch_neverQueuesDummyGamesForScoring() {
        Prediction dummyPrediction = prediction(4L, "dummy", "home");
        Long cutoffId = dummyPrediction.getId();
        GameResultSettlementBaseline baseline = new GameResultSettlementBaseline(1L, cutoffId, 0L, false);
        when(baselineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(baseline));
        when(predictionRepository.findTop500ByIdGreaterThanAndIdLessThanEqualOrderByIdAsc(0L, cutoffId))
                .thenReturn(List.of(dummyPrediction));
        when(gameRepository.findByGameIdIn(Set.of("dummy"))).thenReturn(List.of(
                GameEntity.builder()
                        .gameId("dummy")
                        .homeTeam("LG")
                        .awayTeam("KIA")
                        .gameDate(LocalDate.of(2026, 7, 3))
                        .homeScore(null)
                        .awayScore(null)
                        .isDummy(true)
                        .build()));

        service.processNextBatch();

        verify(predictionRepository, org.mockito.Mockito.never()).markPendingForSettlement(
                any(), eq(Prediction.ScoreSettlementStatus.PENDING));
        verify(predictionRepository).markLegacyForSettlement(
                Set.of(dummyPrediction.getId()),
                Prediction.ScoreSettlementStatus.LEGACY);
    }

    @Test
    void reserveNextPendingGameBatchAdvancesDurableCursor() {
        GameResultSettlementBaseline baseline = new GameResultSettlementBaseline(1L, 0L, 0L, true);
        when(baselineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(baseline));
        when(predictionRepository.findDistinctPendingGameIdsAfter(
                eq(null),
                eq(Prediction.ScoreSettlementStatus.PENDING),
                any())).thenReturn(List.of("20261012-001", "20261012-002"));

        List<String> gameIds = service.reserveNextPendingGameBatch();

        assertThat(gameIds).containsExactly("20261012-001", "20261012-002");
        assertThat(baseline.getPendingGameCursor()).isEqualTo("20261012-002");
        verify(baselineRepository).save(baseline);
    }

    private static GameEntity game(
            String id, String homeTeam, String awayTeam, LocalDate date, Integer homeScore, Integer awayScore) {
        return GameEntity.builder()
                .gameId(id)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .gameDate(date)
                .homeScore(homeScore)
                .awayScore(awayScore)
                .isDummy(false)
                .build();
    }
}
