package com.example.leaderboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.leaderboard.dto.HotStreakDto;
import com.example.leaderboard.dto.LeaderboardEntryDto;
import com.example.leaderboard.dto.RecentScoreDto;
import com.example.leaderboard.entity.ScoreEvent;
import com.example.leaderboard.entity.UserScore;
import com.example.leaderboard.repository.ScoreEventRepository;
import com.example.leaderboard.repository.UserScoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @InjectMocks
    private LeaderboardService leaderboardService;

    @Mock
    private UserScoreRepository userScoreRepository;

    @Mock
    private ScoreEventRepository scoreEventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PublicVisibilityVerifier publicVisibilityVerifier;

    @Test
    @DisplayName("Public leaderboard filters inaccessible private users")
    void getLeaderboard_filtersInaccessibleUsers() {
        UserScore visibleScore = UserScore.builder().userId(10L).seasonScore(100L).userLevel(1).currentStreak(1).maxStreak(1).build();
        UserScore hiddenScore = UserScore.builder().userId(20L).seasonScore(90L).userLevel(1).currentStreak(1).maxStreak(1).build();
        UserEntity visibleUser = UserEntity.builder().id(10L).handle("@visible").name("Visible").build();
        UserEntity hiddenUser = UserEntity.builder().id(20L).handle("@hidden").name("Hidden").privateAccount(true).build();

        when(userScoreRepository.findAllBySeasonScoreDesc(any()))
                .thenReturn(new PageImpl<>(List.of(visibleScore, hiddenScore), PageRequest.of(0, 20), 2));
        when(userRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(visibleUser, hiddenUser));
        when(publicVisibilityVerifier.canAccess(visibleUser, null)).thenReturn(true);
        when(publicVisibilityVerifier.canAccess(hiddenUser, null)).thenReturn(false);

        List<LeaderboardEntryDto> entries = leaderboardService.getLeaderboard("season", 0, 20, null).getContent();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getHandle()).isEqualTo("@visible");
    }

    @Test
    @DisplayName("Handle-based leaderboard stats validate profile visibility")
    void getUserStatsByHandle_validatesVisibility() {
        UserEntity hiddenUser = UserEntity.builder().id(20L).handle("@hidden").privateAccount(true).build();

        when(userRepository.findByHandle("@hidden")).thenReturn(Optional.of(hiddenUser));
        doThrow(new AccessDeniedException("비공개 계정"))
                .when(publicVisibilityVerifier).validate(hiddenUser, 7L, "리더보드 정보");

        assertThrows(AccessDeniedException.class,
                () -> leaderboardService.getUserStatsByHandle("@hidden", 7L));
    }

    @Test
    @DisplayName("Recent score feed filters inaccessible users")
    void getRecentScores_filtersInaccessibleUsers() {
        ScoreEvent visibleEvent = ScoreEvent.builder()
                .id(1L)
                .userId(10L)
                .eventType(ScoreEvent.EventType.CORRECT_PREDICTION)
                .baseScore(10)
                .finalScore(10)
                .createdAt(LocalDateTime.now())
                .description("visible")
                .build();
        ScoreEvent hiddenEvent = ScoreEvent.builder()
                .id(2L)
                .userId(20L)
                .eventType(ScoreEvent.EventType.CORRECT_PREDICTION)
                .baseScore(10)
                .finalScore(10)
                .createdAt(LocalDateTime.now())
                .description("hidden")
                .build();
        UserEntity visibleUser = UserEntity.builder().id(10L).handle("@visible").name("Visible").build();
        UserEntity hiddenUser = UserEntity.builder().id(20L).handle("@hidden").name("Hidden").privateAccount(true).build();

        when(scoreEventRepository.findRecentScores(any())).thenReturn(List.of(visibleEvent, hiddenEvent));
        when(userRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(visibleUser, hiddenUser));
        when(publicVisibilityVerifier.canAccess(visibleUser, null)).thenReturn(true);
        when(publicVisibilityVerifier.canAccess(hiddenUser, null)).thenReturn(false);

        List<RecentScoreDto> events = leaderboardService.getRecentScores(20, null);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getHandle()).isEqualTo("@visible");
    }

    @Test
    @DisplayName("Hot streak feed filters inaccessible users")
    void getHotStreaks_filtersInaccessibleUsers() {
        UserScore visibleScore = UserScore.builder().userId(10L).totalScore(100L).currentStreak(5).userLevel(2).build();
        UserScore hiddenScore = UserScore.builder().userId(20L).totalScore(90L).currentStreak(4).userLevel(2).build();
        UserEntity visibleUser = UserEntity.builder().id(10L).handle("@visible").name("Visible").build();
        UserEntity hiddenUser = UserEntity.builder().id(20L).handle("@hidden").name("Hidden").privateAccount(true).build();

        when(userScoreRepository.findHotStreaks(anyInt(), any())).thenReturn(List.of(visibleScore, hiddenScore));
        when(userRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(visibleUser, hiddenUser));
        when(publicVisibilityVerifier.canAccess(visibleUser, null)).thenReturn(true);
        when(publicVisibilityVerifier.canAccess(hiddenUser, null)).thenReturn(false);

        List<HotStreakDto> streaks = leaderboardService.getHotStreaks(3, 10, null);

        assertThat(streaks).hasSize(1);
        assertThat(streaks.get(0).getHandle()).isEqualTo("@visible");
    }
}
