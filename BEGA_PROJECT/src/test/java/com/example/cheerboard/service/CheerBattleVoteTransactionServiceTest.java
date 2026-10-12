package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.repository.CheerBattleLogRepository;
import com.example.cheerboard.repository.CheerVoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheerBattleVoteTransactionServiceTest {

    @InjectMocks
    private CheerBattleVoteTransactionService voteTransactionService;

    @Mock
    private CheerVoteRepository cheerVoteRepository;

    @Mock
    private CheerBattleLogRepository cheerBattleLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CheerMonitoringMetricsService metricsService;

    @Test
    void vote_deductsPointsAndReturnsTheUpdatedAggregateCount() {
        UserEntity user = user(1L, "user@test.com", 10);
        when(userRepository.findByIdForWrite(1L)).thenReturn(Optional.of(user));
        when(userRepository.deductOneCheerPointIfAvailable(1L)).thenReturn(1);
        when(cheerVoteRepository.incrementVoteCount("game1", "TEAMA")).thenReturn(1);
        when(cheerVoteRepository.findVoteCount("game1", "TEAMA")).thenReturn(6);

        int count = voteTransactionService.vote("game1", "TEAMA", 1L);

        assertThat(count).isEqualTo(6);
        verify(userRepository).deductOneCheerPointIfAvailable(1L);
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(cheerVoteRepository, never()).saveAndFlush(any(CheerVoteEntity.class));
        verify(metricsService).recordBattleVote("success");
    }

    @Test
    void vote_insufficientPointsDoesNotWriteVoteRows() {
        when(userRepository.findByIdForWrite(2L)).thenReturn(Optional.of(user(2L, "poor@test.com", 0)));

        assertThrows(IllegalStateException.class,
                () -> voteTransactionService.vote("game1", "TEAMA", 2L));

        verify(cheerBattleLogRepository, never()).save(any());
        verify(cheerVoteRepository, never()).incrementVoteCount(any(), any());
        verify(metricsService).recordBattleVote("insufficient_points");
    }

    @Test
    void vote_reportsAggregateInsertRaceForWholeTransactionRetry() {
        when(userRepository.findByIdForWrite(3L)).thenReturn(Optional.of(user(3L, "racer@test.com", 10)));
        when(userRepository.deductOneCheerPointIfAvailable(3L)).thenReturn(1);
        when(cheerVoteRepository.incrementVoteCount("game1", "TEAMA")).thenReturn(0);
        when(cheerVoteRepository.saveAndFlush(any(CheerVoteEntity.class)))
                .thenThrow(new DataIntegrityViolationException("concurrent aggregate insert"));

        assertThrows(CheerBattleVoteTransactionService.VoteCountInsertRaceException.class,
                () -> voteTransactionService.vote("game1", "TEAMA", 3L));

        verify(metricsService, never()).recordBattleVote("success");
    }

    private UserEntity user(Long id, String email, int points) {
        return UserEntity.builder().id(id).email(email).cheerPoints(points).build();
    }
}
