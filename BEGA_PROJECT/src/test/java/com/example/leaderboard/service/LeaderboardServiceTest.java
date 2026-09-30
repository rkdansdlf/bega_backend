package com.example.leaderboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.leaderboard.dto.HotStreakDto;
import com.example.leaderboard.dto.LeaderboardEntryDto;
import com.example.leaderboard.dto.RecentScoreDto;
import com.example.leaderboard.dto.UserStatsDto;
import com.example.leaderboard.entity.ScoreEvent;
import com.example.leaderboard.entity.UserScore;
import com.example.leaderboard.repository.ScoreEventRepository;
import com.example.leaderboard.repository.UserScoreRepository;
import com.example.profile.storage.service.ProfileImageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    @Mock
    private ProfileImageService profileImageService;

    @Test
    @DisplayName("Leaderboard preserves DB-filtered totals and assigns visible ordinal ranks")
    void getLeaderboard_preservesVisibleTotalsAndRanks() {
        UserScore thirdScore = UserScore.builder().userId(10L).seasonScore(100L).userLevel(1).currentStreak(1).maxStreak(1).build();
        UserScore fourthScore = UserScore.builder().userId(20L).seasonScore(90L).userLevel(1).currentStreak(1).maxStreak(1).build();
        UserEntity thirdUser = UserEntity.builder().id(10L).handle("@third").name("Third").build();
        UserEntity fourthUser = UserEntity.builder().id(20L).handle("@fourth").name("Fourth").build();

        when(userScoreRepository.findVisibleLeaderboard(eq(7L), any()))
                .thenReturn(new PageImpl<>(List.of(thirdScore, fourthScore), PageRequest.of(1, 2), 5));
        when(userRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(thirdUser, fourthUser));

        var result = leaderboardService.getLeaderboard("season", 1, 2, 7L);

        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getContent()).extracting(LeaderboardEntryDto::getRank)
                .containsExactly(3L, 4L);
        assertThat(result.getContent()).extracting(LeaderboardEntryDto::getHandle)
                .containsExactly("@third", "@fourth");
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userScoreRepository).findVisibleLeaderboard(eq(7L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("seasonScore")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("seasonScore").isDescending()).isTrue();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("userId")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("userId").isAscending()).isTrue();
        verify(publicVisibilityVerifier, never()).canAccess(any(), any());
    }

    @Test
    @DisplayName("Leaderboard rejects page and size outside the public API bounds")
    void getLeaderboard_rejectsInvalidPageBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> leaderboardService.getLeaderboard("season", -1, 20, null));
        assertThrows(IllegalArgumentException.class,
                () -> leaderboardService.getLeaderboard("season", 0, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> leaderboardService.getLeaderboard("season", 0, 101, null));
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
    @DisplayName("User stats uses the snapshot rank query once without per-rank count fallback")
    void getUserStats_usesSnapshotRankQueryOnce() {
        UserScore userScore = UserScore.builder()
                .userId(42L)
                .totalScore(120L)
                .seasonScore(80L)
                .monthlyScore(30L)
                .weeklyScore(10L)
                .userLevel(3)
                .build();
        UserEntity user = UserEntity.builder()
                .id(42L)
                .handle("@ranked")
                .name("Ranked")
                .profileImageUrl("profiles/42.png")
                .build();

        when(userScoreRepository.findByUserId(42L)).thenReturn(Optional.of(userScore));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userScoreRepository.findRanksByScores(120L, 80L, 30L, 10L))
                .thenReturn(new UserScoreRepository.ScoreRankSnapshot() {
                    @Override
                    public Long getTotalRank() {
                        return 7L;
                    }

                    @Override
                    public Long getSeasonRank() {
                        return 3L;
                    }

                    @Override
                    public Long getMonthlyRank() {
                        return 2L;
                    }

                    @Override
                    public Long getWeeklyRank() {
                        return 1L;
                    }
                });

        UserStatsDto stats = leaderboardService.getUserStats(42L);

        assertThat(stats.getRank()).isEqualTo(3L);
        assertThat(stats.getTotalRank()).isEqualTo(7L);
        assertThat(stats.getSeasonRank()).isEqualTo(3L);
        assertThat(stats.getMonthlyRank()).isEqualTo(2L);
        assertThat(stats.getWeeklyRank()).isEqualTo(1L);
        verify(userScoreRepository, times(1)).findRanksByScores(120L, 80L, 30L, 10L);
        verify(userScoreRepository, never()).findSeasonRankByScore(any());
    }

    @Test
    @DisplayName("Recent score feed consumes a DB-filtered bounded result")
    void getRecentScores_usesVisibleRepositoryQuery() {
        ScoreEvent visibleEvent = ScoreEvent.builder()
                .id(1L)
                .userId(10L)
                .eventType(ScoreEvent.EventType.CORRECT_PREDICTION)
                .baseScore(10)
                .finalScore(10)
                .createdAt(LocalDateTime.now())
                .description("visible")
                .build();
        ScoreEvent secondVisibleEvent = ScoreEvent.builder()
                .id(2L)
                .userId(20L)
                .eventType(ScoreEvent.EventType.CORRECT_PREDICTION)
                .baseScore(10)
                .finalScore(10)
                .createdAt(LocalDateTime.now())
                .description("second-visible")
                .build();
        UserEntity visibleUser = UserEntity.builder().id(10L).handle("@visible").name("Visible").build();
        UserEntity secondVisibleUser = UserEntity.builder().id(20L).handle("@second").name("Second").build();

        when(scoreEventRepository.findVisibleRecentScores(isNull(), any()))
                .thenReturn(List.of(visibleEvent, secondVisibleEvent));
        when(userRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(visibleUser, secondVisibleUser));

        List<RecentScoreDto> events = leaderboardService.getRecentScores(20, null);

        assertThat(events).extracting(RecentScoreDto::getHandle)
                .containsExactly("@visible", "@second");
        verify(publicVisibilityVerifier, never()).canAccess(any(), any());
    }

    @Test
    @DisplayName("Hot streak feed consumes a DB-filtered bounded result")
    void getHotStreaks_usesVisibleRepositoryQuery() {
        UserScore visibleScore = UserScore.builder().userId(10L).totalScore(100L).currentStreak(5).userLevel(2).build();
        UserScore secondVisibleScore = UserScore.builder().userId(20L).totalScore(90L).currentStreak(4).userLevel(2).build();
        UserEntity visibleUser = UserEntity.builder().id(10L).handle("@visible").name("Visible").build();
        UserEntity secondVisibleUser = UserEntity.builder().id(20L).handle("@second").name("Second").build();

        when(userScoreRepository.findVisibleHotStreaks(anyInt(), isNull(), any()))
                .thenReturn(List.of(visibleScore, secondVisibleScore));
        when(userRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(visibleUser, secondVisibleUser));

        List<HotStreakDto> streaks = leaderboardService.getHotStreaks(3, 10, null);

        assertThat(streaks).extracting(HotStreakDto::getHandle)
                .containsExactly("@visible", "@second");
        verify(publicVisibilityVerifier, never()).canAccess(any(), any());
    }
}
