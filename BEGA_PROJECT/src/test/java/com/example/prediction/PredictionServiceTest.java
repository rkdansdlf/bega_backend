package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.GameInningScoreRepository;
import com.example.kbo.repository.GameMetadataRepository;
import com.example.kbo.repository.GameSummaryRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PredictionServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private PredictionRepository predictionRepository;

    @Mock
    private GameMetadataRepository gameMetadataRepository;

    @Mock
    private GameInningScoreRepository gameInningScoreRepository;

    @Mock
    private GameSummaryRepository gameSummaryRepository;

    @Mock
    private VoteFinalResultRepository voteFinalResultRepository;

    @Mock
    private UserRepository userRepository;

    private PredictionService predictionService;

    @BeforeEach
    void setUp() {
        predictionService = new PredictionService(
                predictionRepository,
                gameRepository,
                gameMetadataRepository,
                gameInningScoreRepository,
                gameSummaryRepository,
                voteFinalResultRepository,
                userRepository
        );
    }

    @Test
    void getMatchesByDateRangeShouldReturnEmptyWhenCanonicalMatchesAreEmpty() {
        LocalDate startDate = LocalDate.of(2026, 2, 1);
        LocalDate endDate = LocalDate.of(2026, 2, 28);
        Pageable pageRequest = PageRequest.of(0, 1000);

        when(gameRepository.findCanonicalByDateRange(
                any(LocalDate.class),
                any(LocalDate.class),
                anyList(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        List<MatchDto> matches = predictionService.getMatchesByDateRange(startDate, endDate);

        assertThat(matches).isEmpty();
    }

    @Test
    void getMatchesByDateRangeShouldKeepCanonicalFilteringWhenCanonicalMatchesExist() {
        LocalDate startDate = LocalDate.of(2026, 2, 1);
        LocalDate endDate = LocalDate.of(2026, 2, 28);
        GameEntity canonical = buildGame("202602010002", startDate, "HH", "SS", false);
        Pageable pageRequest = PageRequest.of(0, 1000);

        when(gameRepository.findCanonicalByDateRange(
                any(LocalDate.class),
                any(LocalDate.class),
                anyList(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(canonical), pageRequest, 1));

        List<MatchDto> matches = predictionService.getMatchesByDateRange(startDate, endDate);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getGameId()).isEqualTo("202602010002");
    }

    @Test
    void getMatchBoundsShouldReturnBoundsWhenDataExists() {
        LocalDate earliest = LocalDate.of(2026, 3, 20);
        LocalDate latest = LocalDate.of(2026, 10, 1);

        when(gameRepository.findCanonicalMinGameDate(anyList())).thenReturn(Optional.of(earliest));
        when(gameRepository.findCanonicalMaxGameDate(anyList())).thenReturn(Optional.of(latest));

        MatchBoundsResponseDto response = predictionService.getMatchBounds();

        assertThat(response.isHasData()).isTrue();
        assertThat(response.getEarliestGameDate()).isEqualTo(earliest);
        assertThat(response.getLatestGameDate()).isEqualTo(latest);
    }

    @Test
    void getMatchBoundsShouldReturnEmptyWhenNoDataExists() {
        when(gameRepository.findCanonicalMinGameDate(anyList())).thenReturn(Optional.empty());
        when(gameRepository.findCanonicalMaxGameDate(anyList())).thenReturn(Optional.empty());

        MatchBoundsResponseDto response = predictionService.getMatchBounds();

        assertThat(response.isHasData()).isFalse();
        assertThat(response.getEarliestGameDate()).isNull();
        assertThat(response.getLatestGameDate()).isNull();
    }

    // ========== vote() ==========

    @Test
    @DisplayName("vote - 존재하지 않는 경기에 투표하면 예외 발생")
    void vote_throwsIllegalArgumentWhenGameNotFound() {
        when(gameRepository.findByGameId("NOTEXIST")).thenReturn(Optional.empty());

        PredictionRequestDto request = new PredictionRequestDto();
        request.setGameId("NOTEXIST");
        request.setVotedTeam("home");

        assertThrows(IllegalArgumentException.class, () -> predictionService.vote(1L, request));
    }

    @Test
    @DisplayName("vote - 포인트가 0일 때 신규 투표 시 예외 발생")
    void vote_throwsWhenUserHasInsufficientPoints() {
        GameEntity game = buildCanonicalMockGame("202603200001");
        stubVoteWindowOpen(game, "202603200001");

        UserEntity user = mock(UserEntity.class);
        when(user.getCheerPoints()).thenReturn(0);

        when(gameRepository.findByGameId("202603200001")).thenReturn(Optional.of(game));
        when(userRepository.findByIdForWrite(1L)).thenReturn(Optional.of(user));
        when(predictionRepository.findByGameIdAndUserIdForWrite("202603200001", 1L))
                .thenReturn(Optional.empty());

        PredictionRequestDto request = new PredictionRequestDto();
        request.setGameId("202603200001");
        request.setVotedTeam("home");

        assertThrows(IllegalArgumentException.class, () -> predictionService.vote(1L, request));
    }

    // ========== cancelVote() ==========

    @Test
    @DisplayName("cancelVote - 투표 내역이 없으면 IllegalStateException 발생")
    void cancelVote_throwsWhenNoPredictionFound() {
        GameEntity game = buildCanonicalMockGame("202603200001");
        stubVoteWindowOpen(game, "202603200001");

        when(gameRepository.findByGameId("202603200001")).thenReturn(Optional.of(game));
        when(predictionRepository.findByGameIdAndUserIdForWrite("202603200001", 1L))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> predictionService.cancelVote(1L, "202603200001"));
    }

    @Test
    @DisplayName("cancelVote - 투표 레코드를 삭제하고 포인트는 환불하지 않음")
    void cancelVote_deletesVoteWithoutRefund() {
        GameEntity game = buildCanonicalMockGame("202603200001");
        stubVoteWindowOpen(game, "202603200001");
        Prediction prediction = mock(Prediction.class);

        when(gameRepository.findByGameId("202603200001")).thenReturn(Optional.of(game));
        when(predictionRepository.findByGameIdAndUserIdForWrite("202603200001", 1L))
                .thenReturn(Optional.of(prediction));

        predictionService.cancelVote(1L, "202603200001");

        verify(predictionRepository).delete(prediction);
        // userRepository should NOT be called (no refund)
        verify(userRepository, org.mockito.Mockito.never()).findByIdForWrite(any());
    }

    // ========== getUserStats() ==========

    @Test
    @DisplayName("getUserStats - 예측이 없으면 모든 통계가 0")
    void getUserStats_returnsZeroStatsWhenNoPredictions() {
        when(predictionRepository.findAllByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());

        var stats = predictionService.getUserStats(1L);

        assertThat(stats.getTotalPredictions()).isEqualTo(0);
        assertThat(stats.getCorrectPredictions()).isEqualTo(0);
        assertThat(stats.getAccuracy()).isEqualTo(0.0);
        assertThat(stats.getStreak()).isEqualTo(0);
    }

    @Test
    @DisplayName("getUserStats - 완료된 경기에 대한 정확도와 연속 적중 계산")
    void getUserStats_calculatesAccuracyAndStreak() {
        // Predictions fetched in DESC order: p1=newest (correct), p2=oldest (wrong)
        Prediction p1 = mock(Prediction.class);
        Prediction p2 = mock(Prediction.class);
        when(predictionRepository.findAllByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(p1, p2));

        GameEntity g1 = buildCanonicalMockGame("202603200001");
        GameEntity g2 = buildCanonicalMockGame("202603210001");

        // p1: voted "home", winner "home" → correct
        when(p1.getGameId()).thenReturn("202603200001");
        when(p1.getVotedTeam()).thenReturn("home");
        when(gameRepository.findByGameId("202603200001")).thenReturn(Optional.of(g1));
        when(g1.isFinished()).thenReturn(true);
        when(g1.getWinner()).thenReturn("home");

        // p2: voted "home", winner "away" → wrong (breaks streak)
        when(p2.getGameId()).thenReturn("202603210001");
        when(p2.getVotedTeam()).thenReturn("home");
        when(gameRepository.findByGameId("202603210001")).thenReturn(Optional.of(g2));
        when(g2.isFinished()).thenReturn(true);
        when(g2.getWinner()).thenReturn("away");

        var stats = predictionService.getUserStats(1L);

        assertThat(stats.getTotalPredictions()).isEqualTo(2);
        assertThat(stats.getCorrectPredictions()).isEqualTo(1);
        assertThat(stats.getAccuracy()).isEqualTo(50.0);
        assertThat(stats.getStreak()).isEqualTo(1); // newest correct, then broken
    }

    // ========== helpers ==========

    /** Creates a mock GameEntity that passes isCanonicalGame() and validateVoteOpen() checks. */
    private GameEntity buildCanonicalMockGame(String gameId) {
        GameEntity game = mock(GameEntity.class);
        lenient().when(game.getGameId()).thenReturn(gameId);
        lenient().when(game.getHomeTeam()).thenReturn("LG");
        lenient().when(game.getAwayTeam()).thenReturn("HH");
        // status=null → not in BLOCKED_VOTE_STATUSES, startTime=null → no time check
        return game;
    }

    private void stubVoteWindowOpen(GameEntity game, String gameId) {
        GameMetadataEntity metadata = mock(GameMetadataEntity.class);
        when(game.getGameDate()).thenReturn(LocalDate.now().plusDays(1));
        when(metadata.getStartTime()).thenReturn(LocalTime.of(18, 30));
        when(gameMetadataRepository.findByGameId(gameId)).thenReturn(Optional.of(metadata));
    }

    private GameEntity buildGame(String gameId, LocalDate gameDate, String homeTeam, String awayTeam, boolean isDummy) {
        return GameEntity.builder()
                .gameId(gameId)
                .gameDate(gameDate)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium("잠실")
                .isDummy(isDummy)
                .homeScore(0)
                .awayScore(0)
                .build();
    }
}
