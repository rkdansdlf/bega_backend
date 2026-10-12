package com.example.cheerboard.service;

import com.example.auth.repository.UserRepository;
import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.repository.CheerVoteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheerBattleServiceTest {

    @InjectMocks
    private CheerBattleService cheerBattleService;

    @Mock
    private CheerVoteRepository cheerVoteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.example.cheerboard.repository.CheerBattleLogRepository cheerBattleLogRepository;

    @Mock
    private CheerBattleVoteTransactionService voteTransactionService;

    @Test
    @DisplayName("Normally vote - deduct points and increment count")
    void vote_success() {
        when(voteTransactionService.vote("game1", "TEAMA", 1L)).thenReturn(6);

        int result = cheerBattleService.vote("game1", "teamA", 1L);

        assertThat(result).isEqualTo(6);
        assertThat(cheerBattleService.getGameStats("game1")).containsEntry("TEAMA", 6);
        verify(voteTransactionService).vote("game1", "TEAMA", 1L);
    }

    @Test
    @DisplayName("첫 투표 후 통계를 읽어도 아직 투표하지 않은 팀 집계를 누락하지 않는다")
    void voteBeforeColdStatsReadKeepsAllTeamRows() {
        when(voteTransactionService.vote("game1", "TEAMA", 6L)).thenReturn(1);
        when(cheerVoteRepository.findByGameId("game1")).thenReturn(List.of(
                voteRow("game1", "TEAMA", 1),
                voteRow("game1", "TEAMB", 0)));

        cheerBattleService.vote("game1", "teamA", 6L);

        assertThat(cheerBattleService.getGameStats("game1"))
                .containsEntry("TEAMA", 1)
                .containsEntry("TEAMB", 0);
        verify(cheerVoteRepository).findByGameId("game1");
    }

    @Test
    @DisplayName("진행 중인 오래된 통계 조회가 방금 반영된 캐시 값을 덮지 않는다")
    void staleColdStatsReadDoesNotOverwriteConcurrentVote() throws Exception {
        CountDownLatch staleRowsRead = new CountDownLatch(1);
        CountDownLatch allowStaleRowsToMerge = new CountDownLatch(1);
        List<CheerVoteEntity> staleRows = new AbstractList<>() {
            private final CheerVoteEntity row = voteRow("game1", "TEAMA", 0);

            @Override
            public CheerVoteEntity get(int index) {
                if (index != 0) {
                    throw new IndexOutOfBoundsException(index);
                }
                return row;
            }

            @Override
            public int size() {
                return 1;
            }

            @Override
            public void forEach(Consumer<? super CheerVoteEntity> action) {
                staleRowsRead.countDown();
                try {
                    if (!allowStaleRowsToMerge.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to merge stale vote stats");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting to merge stale vote stats", exception);
                }
                action.accept(row);
            }
        };
        when(cheerVoteRepository.findByGameId("game1")).thenReturn(staleRows);
        when(voteTransactionService.vote("game1", "TEAMA", 7L)).thenReturn(1);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<java.util.Map<String, Integer>> staleRead = executor.submit(
                () -> cheerBattleService.getGameStats("game1"));
        try {
            assertThat(staleRowsRead.await(5, TimeUnit.SECONDS)).isTrue();
            cheerBattleService.vote("game1", "teamA", 7L);
            allowStaleRowsToMerge.countDown();
            staleRead.get(5, TimeUnit.SECONDS);

            assertThat(cheerBattleService.getGameStats("game1")).containsEntry("TEAMA", 1);
        } finally {
            allowStaleRowsToMerge.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("Vote failed - insufficient points")
    void vote_insufficient_points() {
        when(voteTransactionService.vote("game1", "TEAMA", 2L))
                .thenThrow(new IllegalStateException("insufficient points"));

        assertThrows(IllegalStateException.class,
                () -> cheerBattleService.vote("game1", "teamA", 2L));
        verify(voteTransactionService).vote("game1", "TEAMA", 2L);
    }

    @Test
    @DisplayName("failed vote write does not remain in the in-memory game stats")
    void vote_failedWriteDoesNotLeavePhantomMemoryCount() {
        when(voteTransactionService.vote("game1", "TEAMA", 3L))
                .thenThrow(new DataIntegrityViolationException("simulated vote write failure"));

        assertThrows(DataIntegrityViolationException.class,
                () -> cheerBattleService.vote("game1", "teamA", 3L));

        assertThat(cheerBattleService.getGameStats("game1")).isEmpty();
    }

    @Test
    void vote_retriesOnceAfterAggregateInsertConflict() {
        when(voteTransactionService.vote("game1", "TEAMA", 4L))
                .thenThrow(new CheerBattleVoteTransactionService.VoteCountInsertRaceException(
                        new DataIntegrityViolationException("concurrent aggregate insert")))
                .thenReturn(1);

        assertThat(cheerBattleService.vote("game1", "teamA", 4L)).isEqualTo(1);

        verify(voteTransactionService, times(2)).vote("game1", "TEAMA", 4L);
        assertThat(cheerBattleService.getGameStats("game1")).containsEntry("TEAMA", 1);
    }

    @Test
    void vote_defersMemoryUpdateUntilAmbientTransactionCommits() {
        when(voteTransactionService.vote("game1", "TEAMA", 5L)).thenReturn(6);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(cheerBattleService.vote("game1", "teamA", 5L)).isEqualTo(6);
            assertThat(cheerBattleService.getGameStats("game1")).isEmpty();

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(TransactionSynchronization::afterCommit);

            assertThat(cheerBattleService.getGameStats("game1")).containsEntry("TEAMA", 6);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private CheerVoteEntity voteRow(String gameId, String teamId, int voteCount) {
        return CheerVoteEntity.builder()
                .gameId(gameId)
                .teamId(teamId)
                .voteCount(voteCount)
                .build();
    }
}
