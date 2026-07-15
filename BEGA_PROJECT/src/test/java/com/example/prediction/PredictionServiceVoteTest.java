package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.common.config.CacheConfig;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.PredictionStatsGameProjection;
import com.example.kbo.service.LeagueStageResolver;
import com.example.kbo.validation.BaseballDataIntegrityGuard;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class PredictionServiceVoteTest extends PredictionServiceTestFixture {

    @Test
    void getVoteStatusShouldUseAggregatedCountsWhenFinalResultMissing() {
        PredictionVoteCountsProjection voteCounts = mock(PredictionVoteCountsProjection.class);

        when(voteFinalResultRepository.findById("202603200001")).thenReturn(Optional.empty());
        when(voteCounts.getHomeVotes()).thenReturn(7L);
        when(voteCounts.getAwayVotes()).thenReturn(3L);
        when(predictionRepository.findVoteCountsByGameId("202603200001")).thenReturn(voteCounts);

        PredictionResponseDto response = predictionService.getVoteStatus("202603200001");

        assertThat(response.getHomeVotes()).isEqualTo(7L);
        assertThat(response.getAwayVotes()).isEqualTo(3L);
        assertThat(response.getTotalVotes()).isEqualTo(10L);
        assertThat(response.getHomePercentage()).isEqualTo(70);
        assertThat(response.getAwayPercentage()).isEqualTo(30);
        verify(predictionRepository, never()).countByGameIdAndVotedTeam(any(), any());
        verify(predictionRepository, never()).countByGameId(any());
    }

    @Test
    void getVoteStatusShouldReuseCachedCountsAcrossRepeatedReads() {
        PredictionVoteCountsProjection voteCounts = mock(PredictionVoteCountsProjection.class);

        when(voteFinalResultRepository.findById("202603200001")).thenReturn(Optional.empty());
        when(voteCounts.getHomeVotes()).thenReturn(8L);
        when(voteCounts.getAwayVotes()).thenReturn(2L);
        when(predictionRepository.findVoteCountsByGameId("202603200001")).thenReturn(voteCounts);

        PredictionResponseDto first = predictionService.getVoteStatus("202603200001");
        PredictionResponseDto second = predictionService.getVoteStatus("202603200001");

        assertThat(first.getTotalVotes()).isEqualTo(10L);
        assertThat(second.getTotalVotes()).isEqualTo(10L);
        verify(voteFinalResultRepository, times(1)).findById("202603200001");
        verify(predictionRepository, times(1)).findVoteCountsByGameId("202603200001");
    }

    @Test
    void getVoteStatusShouldFallBackWhenCacheReadFails() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(cacheManager.getCache(CacheConfig.PREDICTION_VOTE_STATUS)).thenReturn(cache);
        when(cache.get("202603200001")).thenThrow(new SerializationException("broken cache payload"));
        PredictionVoteCountsProjection voteCounts = mock(PredictionVoteCountsProjection.class);
        when(voteFinalResultRepository.findById("202603200001")).thenReturn(Optional.empty());
        when(voteCounts.getHomeVotes()).thenReturn(6L);
        when(voteCounts.getAwayVotes()).thenReturn(4L);
        when(predictionRepository.findVoteCountsByGameId("202603200001")).thenReturn(voteCounts);

        PredictionService localService = new PredictionService(
                predictionRepository,
                gameRepository,
                gameMetadataRepository,
                gameInningScoreRepository,
                gameSummaryRepository,
                voteFinalResultRepository,
                userRepository,
                new LeagueStageResolver(gameRepository),
                mock(BaseballDataIntegrityGuard.class),
                cacheManager,
                transactionManager);

        PredictionResponseDto response = localService.getVoteStatus("202603200001");

        assertThat(response.getTotalVotes()).isEqualTo(10L);
        verify(cache).evict("202603200001");
        verify(predictionRepository).findVoteCountsByGameId("202603200001");
        verify(cache).put(eq("202603200001"), any(PredictionVoteStatusCacheEntry.class));
    }

    @Test
    void getVoteStatusShouldIgnoreCacheWriteFailure() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(cacheManager.getCache(CacheConfig.PREDICTION_VOTE_STATUS)).thenReturn(cache);
        when(cache.get("202603200001")).thenReturn(null);
        doThrow(new SerializationException("write failed")).when(cache)
                .put(eq("202603200001"), any(PredictionVoteStatusCacheEntry.class));
        PredictionVoteCountsProjection voteCounts = mock(PredictionVoteCountsProjection.class);
        when(voteFinalResultRepository.findById("202603200001")).thenReturn(Optional.empty());
        when(voteCounts.getHomeVotes()).thenReturn(5L);
        when(voteCounts.getAwayVotes()).thenReturn(5L);
        when(predictionRepository.findVoteCountsByGameId("202603200001")).thenReturn(voteCounts);

        PredictionService localService = new PredictionService(
                predictionRepository,
                gameRepository,
                gameMetadataRepository,
                gameInningScoreRepository,
                gameSummaryRepository,
                voteFinalResultRepository,
                userRepository,
                new LeagueStageResolver(gameRepository),
                mock(BaseballDataIntegrityGuard.class),
                cacheManager,
                transactionManager);

        PredictionResponseDto response = localService.getVoteStatus("202603200001");

        assertThat(response.getTotalVotes()).isEqualTo(10L);
        verify(cache).put(eq("202603200001"), any(PredictionVoteStatusCacheEntry.class));
        verify(cache).evict("202603200001");
    }

    @Test
    void getVoteStatusShouldPreferFinalResultWithoutPredictionCountQuery() {
        VoteFinalResult finalResult = VoteFinalResult.builder()
                .gameId("202603200001")
                .finalVotesA(11)
                .finalVotesB(9)
                .finalWinner("HOME")
                .build();

        when(voteFinalResultRepository.findById("202603200001")).thenReturn(Optional.of(finalResult));

        PredictionResponseDto response = predictionService.getVoteStatus("202603200001");

        assertThat(response.getHomeVotes()).isEqualTo(11L);
        assertThat(response.getAwayVotes()).isEqualTo(9L);
        assertThat(response.getTotalVotes()).isEqualTo(20L);
        assertThat(response.getHomePercentage()).isEqualTo(55);
        assertThat(response.getAwayPercentage()).isEqualTo(45);
        verify(predictionRepository, never()).findVoteCountsByGameId(any());
        verify(predictionRepository, never()).countByGameIdAndVotedTeam(any(), any());
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

    @Test
    @DisplayName("vote - 같은 팀으로 재전송하면 기존 투표를 유지하고 삭제하지 않음")
    void vote_sameTeamResubmissionIsNoOp() {
        GameEntity game = buildCanonicalMockGame("202603200001");
        stubVoteWindowOpen(game, "202603200001");
        Prediction existingPrediction = mock(Prediction.class);

        when(existingPrediction.getVotedTeam()).thenReturn("home");
        when(gameRepository.findByGameId("202603200001")).thenReturn(Optional.of(game));
        when(predictionRepository.findByGameIdAndUserIdForWrite("202603200001", 1L))
                .thenReturn(Optional.of(existingPrediction));

        PredictionRequestDto request = new PredictionRequestDto();
        request.setGameId("202603200001");
        request.setVotedTeam("home");

        predictionService.vote(1L, request);

        verify(predictionRepository, never()).delete(existingPrediction);
        verify(predictionRepository, never()).saveAndFlush(any());
        verify(userRepository, never()).findByIdForWrite(any());
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
        when(predictionRepository.findStatsRowsByUserIdOrderByCreatedAtDesc(1L))
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
        PredictionStatsRowProjection p1 = mock(PredictionStatsRowProjection.class);
        PredictionStatsRowProjection p2 = mock(PredictionStatsRowProjection.class);
        when(predictionRepository.findStatsRowsByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(p1, p2));

        PredictionStatsGameProjection g1 = mock(PredictionStatsGameProjection.class);
        PredictionStatsGameProjection g2 = mock(PredictionStatsGameProjection.class);
        when(gameRepository.findPredictionStatsGameSummaries(eq(List.of("202603200001", "202603210001")), anyList()))
                .thenReturn(List.of(g1, g2));

        // p1: voted "home", winner "home" → correct
        when(p1.getGameId()).thenReturn("202603200001");
        when(p1.getVotedTeam()).thenReturn("home");
        when(g1.getWinner()).thenReturn("home");
        when(g1.getGameId()).thenReturn("202603200001");

        // p2: voted "home", winner "away" → wrong (breaks streak)
        when(p2.getGameId()).thenReturn("202603210001");
        when(p2.getVotedTeam()).thenReturn("home");
        when(g2.getWinner()).thenReturn("away");
        when(g2.getGameId()).thenReturn("202603210001");

        var stats = predictionService.getUserStats(1L);

        assertThat(stats.getTotalPredictions()).isEqualTo(2);
        assertThat(stats.getCorrectPredictions()).isEqualTo(1);
        assertThat(stats.getAccuracy()).isEqualTo(50.0);
        assertThat(stats.getStreak()).isEqualTo(1); // newest correct, then broken
    }

    @Test
    @DisplayName("getUserStats uses minimal projections instead of full prediction/game entities")
    void getUserStats_usesMinimalProjectionQueries() {
        PredictionStatsRowProjection firstPrediction = mock(PredictionStatsRowProjection.class);
        PredictionStatsRowProjection secondPrediction = mock(PredictionStatsRowProjection.class);
        PredictionStatsGameProjection firstGame = mock(PredictionStatsGameProjection.class);
        PredictionStatsGameProjection secondGame = mock(PredictionStatsGameProjection.class);

        when(predictionRepository.findStatsRowsByUserIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(firstPrediction, secondPrediction));
        when(firstPrediction.getGameId()).thenReturn("202603200001");
        when(firstPrediction.getVotedTeam()).thenReturn("home");
        when(secondPrediction.getGameId()).thenReturn("202603210001");
        when(secondPrediction.getVotedTeam()).thenReturn("away");
        when(firstGame.getGameId()).thenReturn("202603200001");
        when(firstGame.getWinner()).thenReturn("home");
        when(secondGame.getGameId()).thenReturn("202603210001");
        when(secondGame.getWinner()).thenReturn("away");
        when(gameRepository.findPredictionStatsGameSummaries(eq(List.of("202603200001", "202603210001")), anyList()))
                .thenReturn(List.of(firstGame, secondGame));

        UserPredictionStatsDto stats = predictionService.getUserStats(7L);

        assertThat(stats.getTotalPredictions()).isEqualTo(2);
        assertThat(stats.getCorrectPredictions()).isEqualTo(2);
        assertThat(stats.getAccuracy()).isEqualTo(100.0);
        assertThat(stats.getStreak()).isEqualTo(2);
        verify(predictionRepository, times(1)).findStatsRowsByUserIdOrderByCreatedAtDesc(7L);
        verify(gameRepository, times(1)).findPredictionStatsGameSummaries(eq(List.of("202603200001", "202603210001")), anyList());
        verify(predictionRepository, never()).findAllByUserIdOrderByCreatedAtDesc(any());
        verify(gameRepository, never()).findByGameIdIn(any());
    }
}
