package com.example.leaderboard.service;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import com.example.leaderboard.dto.ScoreResultDto;
import com.example.leaderboard.repository.ScoreEventRepository;
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

    private static final LocalDate DATE = LocalDate.of(2024, 9, 15);

    @BeforeEach
    void setUpDefaults() {
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

        int count = gameResultScoringService.processGamesForDate(DATE);

        // Both games processed but no predictions → 0 total processed predictions
        verify(predictionRepository, times(2)).findByGameId(anyString());
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
        int count = gameResultScoringService.processGamesForDate(DATE);

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

        verify(scoringService).processPerfectDay(userId, 3);
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

        verify(scoringService, never()).processPerfectDay(anyLong(), anyInt());
    }
}
