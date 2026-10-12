package com.example.leaderboard.service;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import com.example.leaderboard.dto.ScoreResultDto;
import com.example.leaderboard.repository.ScoreEventRepository;
import com.example.leaderboard.repository.LegacyPerfectDayDateRepository;
import com.example.prediction.Prediction;
import com.example.prediction.PredictionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.example.leaderboard.support.LeaderboardTestFixtureFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameResultScoringServiceTest {

    @InjectMocks
    private GameResultScoringService gameResultScoringService;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private PredictionRepository predictionRepository;

    @Mock
    private ScoreEventRepository scoreEventRepository;

    @Mock
    private ScoringService scoringService;

    @Mock
    private GameResultSettlementBaselineService settlementBaselineService;

    @Mock
    private LegacyPerfectDayDateRepository legacyPerfectDayDateRepository;

    private static final LocalDate DATE = LocalDate.of(2024, 9, 15);

    @BeforeEach
    void setUpDefaults() {
        lenient().when(settlementBaselineService.prepareForScoring()).thenReturn(true);
        lenient().when(gameRepository.findByGameDate(any())).thenReturn(Collections.emptyList());
        lenient().when(scoreEventRepository.findProcessedPredictionIdsByPredictionIdIn(anyList()))
                .thenReturn(Collections.emptyList());
        lenient().when(predictionRepository.findByGameIdIn(anyList()))
                .thenReturn(Collections.emptyList());
    }

    // ============================================
    // processGameResult
    // ============================================

    @Test
    @DisplayName("홈팀 승리: 2명 예측 중 1명 정답 → processedCount=2")
    void processGameResult_happyPathHomeWin() {
        GameEntity game = finishedGame("g1", "LG", "KIA", 5, 3, DATE);
        Prediction p1 = prediction(1L, "g1", "home"); // correct
        Prediction p2 = prediction(2L, "g1", "away"); // wrong

        when(gameRepository.findByGameId("g1")).thenReturn(Optional.of(game));
        when(predictionRepository.findByGameId("g1")).thenReturn(List.of(p1, p2));

        ScoreResultDto correctResult = ScoreResultDto.builder().userId(1L).correct(true).totalEarned(100).currentStreak(1).build();
        ScoreResultDto wrongResult = ScoreResultDto.incorrect(2L, 0);
        when(scoringService.processPredictionResult(eq(1L), anyLong(), eq("g1"), eq(true), eq(false)))
                .thenReturn(correctResult);
        when(scoringService.processPredictionResult(eq(2L), anyLong(), eq("g1"), eq(false), eq(false)))
                .thenReturn(wrongResult);

        int count = gameResultScoringService.processGameResult("g1");

        assertThat(count).isEqualTo(2);
        verify(scoringService, times(2)).processPredictionResult(anyLong(), anyLong(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("원정팀 승리 시 isUpset=true 전달")
    void processGameResult_awayWinIsUpset() {
        GameEntity game = finishedGame("g2", "LG", "KIA", 2, 5, DATE);
        Prediction p = prediction(1L, "g2", "away"); // correct

        when(gameRepository.findByGameId("g2")).thenReturn(Optional.of(game));
        when(predictionRepository.findByGameId("g2")).thenReturn(List.of(p));
        when(scoringService.processPredictionResult(eq(1L), anyLong(), eq("g2"), eq(true), eq(true)))
                .thenReturn(ScoreResultDto.builder().userId(1L).correct(true).totalEarned(150).currentStreak(1).build());

        int count = gameResultScoringService.processGameResult("g2");

        assertThat(count).isEqualTo(1);
        // isUpset=true because away wins
        verify(scoringService).processPredictionResult(eq(1L), anyLong(), eq("g2"), eq(true), eq(true));
    }

    @Test
    @DisplayName("게임이 없으면 IllegalArgumentException")
    void processGameResult_gameNotFound() {
        when(gameRepository.findByGameId("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> gameResultScoringService.processGameResult("missing"));
    }

    @Test
    @DisplayName("비canonical 팀 코드 게임은 0 반환")
    void processGameResult_nonCanonicalTeam() {
        // "XX" is not a canonical team code
        GameEntity game = finishedGame("g3", "XX", "YY", 5, 3, DATE);
        when(gameRepository.findByGameId("g3")).thenReturn(Optional.of(game));

        int count = gameResultScoringService.processGameResult("g3");

        assertThat(count).isZero();
        verify(predictionRepository, never()).findByGameId(anyString());
    }

    @Test
    @DisplayName("더미 경기는 사용자 예측 점수 정산에서 제외")
    void processGameResult_dummyGameIsNotScored() {
        GameEntity dummyGame = GameEntity.builder()
                .gameId("dummy-finished")
                .homeTeam("LG")
                .awayTeam("KIA")
                .homeScore(5)
                .awayScore(3)
                .gameDate(DATE)
                .isDummy(true)
                .build();
        when(gameRepository.findByGameId("dummy-finished")).thenReturn(Optional.of(dummyGame));

        int processed = gameResultScoringService.processGameResult("dummy-finished");

        assertThat(processed).isZero();
        verify(predictionRepository, never()).findByGameId(anyString());
        verify(scoringService, never()).processPredictionResult(
                anyLong(), anyLong(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("미종료 게임은 0 반환")
    void processGameResult_gameNotFinished() {
        // homeScore=null → isFinished()=false
        GameEntity game = GameEntity.builder()
                .id(100L).gameId("g4").homeTeam("LG").awayTeam("KIA")
                .gameDate(DATE).isDummy(false)
                .build();
        when(gameRepository.findByGameId("g4")).thenReturn(Optional.of(game));

        int count = gameResultScoringService.processGameResult("g4");

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("무승부 게임도 처리 (winner='draw')")
    void processGameResult_noWinner() {
        GameEntity game = finishedGame("g5", "LG", "KIA", 3, 3, DATE);
        Prediction p = prediction(1L, "g5", "home"); // votedTeam != "draw" → incorrect

        when(gameRepository.findByGameId("g5")).thenReturn(Optional.of(game));
        when(predictionRepository.findByGameId("g5")).thenReturn(List.of(p));
        when(scoringService.processPredictionResult(eq(1L), anyLong(), eq("g5"), eq(false), eq(false)))
                .thenReturn(ScoreResultDto.incorrect(1L, 0));

        int count = gameResultScoringService.processGameResult("g5");

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("예측이 없으면 0 반환 (checkPerfectDay 전에 리턴)")
    void processGameResult_noPredictions() {
        GameEntity game = finishedGame("g6", "LG", "KIA", 5, 3, DATE);
        when(gameRepository.findByGameId("g6")).thenReturn(Optional.of(game));
        when(predictionRepository.findByGameId("g6")).thenReturn(Collections.emptyList());

        int count = gameResultScoringService.processGameResult("g6");

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("이미 처리된 예측은 건너뛰고 나머지만 처리")
    void processGameResult_alreadyProcessedSkipped() {
        GameEntity game = finishedGame("g7", "LG", "KIA", 5, 3, DATE);
        Prediction p1 = prediction(1L, "g7", "home");
        Prediction p2 = prediction(2L, "g7", "home");

        when(gameRepository.findByGameId("g7")).thenReturn(Optional.of(game));
        when(predictionRepository.findByGameId("g7")).thenReturn(List.of(p1, p2));
        // p1 already processed, p2 not
        when(scoreEventRepository.findProcessedPredictionIdsByPredictionIdIn(List.of(p1.getId(), p2.getId())))
                .thenReturn(List.of(p1.getId()));
        when(scoringService.processPredictionResult(eq(2L), eq(p2.getId()), eq("g7"), eq(true), eq(false)))
                .thenReturn(ScoreResultDto.builder().userId(2L).correct(true).totalEarned(100).currentStreak(1).build());

        int count = gameResultScoringService.processGameResult("g7");

        assertThat(count).isEqualTo(1);
        verify(scoringService, times(1)).processPredictionResult(anyLong(), anyLong(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("한 예측에서 예외 발생해도 나머지 계속 처리")
    void processGameResult_exceptionContinuesOthers() {
        GameEntity game = finishedGame("g8", "LG", "KIA", 5, 3, DATE);
        Prediction p1 = prediction(1L, "g8", "home");
        Prediction p2 = prediction(2L, "g8", "home");

        when(gameRepository.findByGameId("g8")).thenReturn(Optional.of(game));
        when(predictionRepository.findByGameId("g8")).thenReturn(List.of(p1, p2));
        // p1 throws, p2 succeeds
        when(scoringService.processPredictionResult(eq(1L), anyLong(), anyString(), anyBoolean(), anyBoolean()))
                .thenThrow(new RuntimeException("test error"));
        when(scoringService.processPredictionResult(eq(2L), anyLong(), anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(ScoreResultDto.builder().userId(2L).correct(true).totalEarned(100).currentStreak(1).build());

        int count = gameResultScoringService.processGameResult("g8");

        // p1 failed (not counted), p2 succeeded
        assertThat(count).isEqualTo(1);
    }

    // ============================================
    // processGamesForDate
    // ============================================

    @Test
    @DisplayName("여러 종료 게임 처리: 종료+canonical만 처리")
    void processGamesForDate_multipleFinishedGames() {
        GameEntity finished1 = finishedGame("gd1", "LG", "KIA", 5, 3, DATE);
        GameEntity finished2 = finishedGame("gd2", "HH", "SSG", 4, 2, DATE);
        // unfinished: no scores
        GameEntity unfinished = GameEntity.builder()
                .id(999L).gameId("gd3").homeTeam("NC").awayTeam("KT")
                .gameDate(DATE).isDummy(false)
                .build();

        when(gameRepository.findByGameDate(DATE)).thenReturn(List.of(finished1, finished2, unfinished));
        when(predictionRepository.findByGameId(anyString())).thenReturn(Collections.emptyList());

        assertThat(gameResultScoringService.processGamesForDate(DATE)).isZero();

        // Both games processed but no predictions → 0 total processed predictions
        verify(predictionRepository, times(2)).findByGameId(anyString());
    }

    @Test
    @DisplayName("일자별 결과 정산에서도 더미 경기는 제외")
    void processGamesForDate_skipsDummyGames() {
        GameEntity realGame = finishedGame("real-for-date", "LG", "KIA", 5, 3, DATE);
        GameEntity dummyGame = GameEntity.builder()
                .gameId("dummy-for-date")
                .homeTeam("HH")
                .awayTeam("SSG")
                .homeScore(4)
                .awayScore(2)
                .gameDate(DATE)
                .isDummy(true)
                .build();
        when(gameRepository.findByGameDate(DATE)).thenReturn(List.of(realGame, dummyGame));
        when(predictionRepository.findByGameId("real-for-date")).thenReturn(List.of());

        gameResultScoringService.processGamesForDate(DATE);

        verify(predictionRepository).findByGameId("real-for-date");
        verify(predictionRepository, never()).findByGameId("dummy-for-date");
    }

    @Test
    @DisplayName("종료 게임이 없으면 0 반환")
    void processGamesForDate_noFinishedGames() {
        GameEntity unfinished = GameEntity.builder()
                .id(998L).gameId("gd4").homeTeam("LG").awayTeam("KIA")
                .gameDate(DATE).isDummy(false)
                .build();
        when(gameRepository.findByGameDate(DATE)).thenReturn(List.of(unfinished));

        int count = gameResultScoringService.processGamesForDate(DATE);

        assertThat(count).isZero();
        verify(gameRepository, never()).findByGameId(anyString());
    }

    @Test
    @DisplayName("한 게임에서 예외 발생해도 다음 게임 계속 처리")
    void processGamesForDate_exceptionInOneGame() {
        GameEntity game1 = finishedGame("ge1", "LG", "KIA", 5, 3, DATE);
        GameEntity game2 = finishedGame("ge2", "HH", "SSG", 4, 2, DATE);

        when(gameRepository.findByGameDate(DATE)).thenReturn(List.of(game1, game2));
        when(predictionRepository.findByGameId("ge1")).thenThrow(new RuntimeException("db error"));
        when(predictionRepository.findByGameId("ge2")).thenReturn(Collections.emptyList());

        // Should not throw despite game1 error
        assertThat(gameResultScoringService.processGamesForDate(DATE)).isZero();

        verify(predictionRepository).findByGameId("ge2");
    }

    // ============================================
    // checkPerfectDay (via processGameResult)
    // ============================================

    @Test
    @DisplayName("3경기 전승 시 processPerfectDay 호출")
    void processGameResult_perfectDayWith3of3Correct() {
        // 3 finished canonical games on same date, user predicted all correctly
        GameEntity g1 = finishedGame("pd1", "LG", "KIA", 5, 3, DATE);
        GameEntity g2 = finishedGame("pd2", "HH", "SSG", 4, 2, DATE);
        GameEntity g3 = finishedGame("pd3", "NC", "KT", 6, 1, DATE);

        Long userId = 99L;
        Prediction pred1 = prediction(userId, "pd1", "home");
        Prediction pred2 = prediction(userId, "pd2", "home");
        Prediction pred3 = prediction(userId, "pd3", "home");

        // processGameResult("pd1") flow
        when(gameRepository.findByGameId("pd1")).thenReturn(Optional.of(g1));
        when(predictionRepository.findByGameId("pd1")).thenReturn(List.of(pred1));
        when(scoringService.processPredictionResult(eq(userId), anyLong(), eq("pd1"), eq(true), eq(false)))
                .thenReturn(ScoreResultDto.builder().userId(userId).correct(true).totalEarned(100).currentStreak(1).build());

        when(gameRepository.findByGameDate(DATE)).thenReturn(List.of(g1, g2, g3));
        when(predictionRepository.findByGameIdIn(List.of("pd1", "pd2", "pd3")))
                .thenReturn(List.of(pred1, pred2, pred3));
        // All winners are "home", all predictions are "home"

        gameResultScoringService.processGameResult("pd1");

        verify(scoringService).processPerfectDay(userId, DATE, 3);
    }

    @Test
    @DisplayName("3경기 미만이면 processPerfectDay 호출 안됨")
    void processGameResult_noPerfectDayWhenLessThan3Games() {
        GameEntity g1 = finishedGame("np1", "LG", "KIA", 5, 3, DATE);
        GameEntity g2 = finishedGame("np2", "HH", "SSG", 4, 2, DATE);

        Long userId = 98L;
        Prediction pred = prediction(userId, "np1", "home");

        when(gameRepository.findByGameId("np1")).thenReturn(Optional.of(g1));
        when(predictionRepository.findByGameId("np1")).thenReturn(List.of(pred));
        when(scoringService.processPredictionResult(eq(userId), anyLong(), eq("np1"), eq(true), eq(false)))
                .thenReturn(ScoreResultDto.builder().userId(userId).correct(true).totalEarned(100).currentStreak(1).build());

        when(gameRepository.findByGameDate(DATE)).thenReturn(List.of(g1, g2));

        gameResultScoringService.processGameResult("np1");

        verify(scoringService, never()).processPerfectDay(anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("새벽 정산 이후 날짜의 지연 완료 경기도 pending 순환 탐색으로 정산")
    void processPendingGames_reconcilesLateFinishedGame() {
        GameEntity lateGame = finishedGame("late-game", "LG", "KIA", 4, 2, DATE.minusDays(3));
        Prediction prediction = prediction(110L, "late-game", "home");
        when(settlementBaselineService.reserveNextPendingGameBatch()).thenReturn(List.of("late-game"));
        when(gameRepository.findByGameIdIn(List.of("late-game"))).thenReturn(List.of(lateGame));
        when(predictionRepository.findByGameId("late-game")).thenReturn(List.of(prediction));
        when(scoringService.processPredictionResult(eq(110L), eq(prediction.getId()), eq("late-game"),
                eq(true), eq(false)))
                .thenReturn(ScoreResultDto.builder().userId(110L).correct(true).totalEarned(100).build());

        int processed = gameResultScoringService.processPendingGames();

        assertThat(processed).isEqualTo(1);
        verify(scoringService).processPredictionResult(110L, prediction.getId(), "late-game", true, false);
    }

    @Test
    @DisplayName("pending 목록에 남은 더미 경기도 점수에 반영하지 않음")
    void processPendingGames_skipsDummyGame() {
        GameEntity dummyGame = GameEntity.builder()
                .gameId("late-dummy")
                .homeTeam("LG")
                .awayTeam("KIA")
                .homeScore(4)
                .awayScore(2)
                .gameDate(DATE.minusDays(3))
                .isDummy(true)
                .build();
        when(settlementBaselineService.reserveNextPendingGameBatch()).thenReturn(List.of("late-dummy"));
        when(gameRepository.findByGameIdIn(List.of("late-dummy"))).thenReturn(List.of(dummyGame));

        int processed = gameResultScoringService.processPendingGames();

        assertThat(processed).isZero();
        verify(predictionRepository, never()).findByGameId("late-dummy");
        verify(scoringService, never()).processPredictionResult(
                anyLong(), anyLong(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("cutover 시점에 이미 종료된 경기 날짜는 퍼펙트데이 재지급 대상에서 제외")
    void processGameResult_doesNotReawardPerfectDayForLegacyDate() {
        GameEntity game = finishedGame("legacy-pd", "LG", "KIA", 5, 3, DATE);
        Prediction prediction = prediction(111L, "legacy-pd", "home");
        when(gameRepository.findByGameId("legacy-pd")).thenReturn(Optional.of(game));
        when(predictionRepository.findByGameId("legacy-pd")).thenReturn(List.of(prediction));
        when(scoringService.processPredictionResult(eq(111L), eq(prediction.getId()), eq("legacy-pd"),
                eq(true), eq(false)))
                .thenReturn(ScoreResultDto.builder().userId(111L).correct(true).totalEarned(100).build());
        when(gameRepository.findByGameDate(DATE)).thenReturn(List.of(game,
                finishedGame("legacy-pd-2", "HH", "SSG", 4, 2, DATE),
                finishedGame("legacy-pd-3", "NC", "KT", 3, 1, DATE)));
        when(legacyPerfectDayDateRepository.existsById(DATE)).thenReturn(true);

        gameResultScoringService.processGameResult("legacy-pd");

        verify(scoringService, never()).processPerfectDay(anyLong(), any(), anyInt());
    }
}
