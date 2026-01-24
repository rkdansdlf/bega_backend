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

    @Test
    @DisplayName("Normally vote - deduct points and increment count")
    @SuppressWarnings("null")
    void vote_success() {
        // Given
        String gameId = "game1";
        String teamId = "teamA";
        String email = "user@test.com";

        UserEntity user = UserEntity.builder()
                .email(email)
                .cheerPoints(10)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        CheerVoteId voteId = CheerVoteId.builder().gameId(gameId).teamId(teamId).build();
        CheerVoteEntity voteEntity = CheerVoteEntity.builder()
                .gameId(gameId)
                .teamId(teamId)
                .voteCount(5)
                .build();

        when(cheerVoteRepository.findById(Objects.requireNonNull(voteId))).thenReturn(Optional.of(voteEntity));

        // When
        int result = cheerBattleService.vote(gameId, teamId, email);

        // Then
        assertThat(result).isEqualTo(6); // 5 + 1
        assertThat(user.getCheerPoints()).isEqualTo(9); // 10 - 1

        verify(userRepository).save(user); // Points must be saved
        verify(cheerVoteRepository).save(any(CheerVoteEntity.class)); // Votes must be saved
    }

    @Test
    @DisplayName("Vote failed - insufficient points")
    void vote_insufficient_points() {
        // Given
        String gameId = "game1";
        String teamId = "teamA";
        String email = "poor@test.com";

        UserEntity user = UserEntity.builder()
                .email(email)
                .cheerPoints(0)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            cheerBattleService.vote(gameId, teamId, email);
        });

        @SuppressWarnings({ "null", "unused" })
        var _unused = verify(cheerVoteRepository, never()).save(any(CheerVoteEntity.class));
    }
}
