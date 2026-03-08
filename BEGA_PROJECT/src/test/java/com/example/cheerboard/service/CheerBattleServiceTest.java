package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.entity.CheerVoteId;
import com.example.cheerboard.repository.CheerVoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;
import java.util.Optional;

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
    private CheerMonitoringMetricsService metricsService;

    @Test
    @DisplayName("Normally vote - deduct points and increment count")
    void vote_success() {
        // Given
        String gameId = "game1";
        String rawTeamId = "teamA";
        String normalizedTeamId = "TEAMA";
        Long userId = 1L;
        String email = "user@test.com";

        UserEntity user = UserEntity.builder()
                .id(userId)
                .email(email)
                .cheerPoints(10)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        CheerVoteId voteId = CheerVoteId.builder().gameId(gameId).teamId(normalizedTeamId).build();
        CheerVoteEntity voteEntity = CheerVoteEntity.builder()
                .gameId(gameId)
                .teamId(normalizedTeamId)
                .voteCount(5)
                .build();

        when(cheerVoteRepository.findById(Objects.requireNonNull(voteId))).thenReturn(Optional.of(voteEntity));
        when(cheerVoteRepository.incrementVoteCount(gameId, normalizedTeamId)).thenReturn(1);
        when(cheerVoteRepository.findVoteCount(gameId, normalizedTeamId)).thenReturn(6);

        // When
        int result = cheerBattleService.vote(gameId, rawTeamId, userId);

        // Then
        assertThat(result).isEqualTo(6); // 5 + 1
        assertThat(user.getCheerPoints()).isEqualTo(9); // 10 - 1

        verify(userRepository).save(user); // Points must be saved
        verify(cheerVoteRepository, never()).save(any(CheerVoteEntity.class)); // Existing vote row path uses atomic
                                                                               // update
        verify(cheerVoteRepository).incrementVoteCount(gameId, normalizedTeamId);
    }

    @Test
    @DisplayName("Vote failed - insufficient points")
    void vote_insufficient_points() {
        // Given
        String gameId = "game1";
        String teamId = "teamA";
        Long userId = 2L;
        String email = "poor@test.com";

        UserEntity user = UserEntity.builder()
                .id(userId)
                .email(email)
                .cheerPoints(0)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            cheerBattleService.vote(gameId, teamId, userId);
        });

        verify(cheerVoteRepository, never()).save(any(CheerVoteEntity.class));
    }
}
