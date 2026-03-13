package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.kbo.repository.GameRepository;

@ExtendWith(MockitoExtension.class)
class RankingPredictionServiceTest {

    @Mock
    private RankingPredictionRepository rankingPredictionRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private com.example.homepage.HomePageTeamRepository homePageTeamRepository;

    @Mock
    private UserRepository userRepository;

    private RankingPredictionService rankingPredictionService;

    @BeforeEach
    void setUp() {
        rankingPredictionService = new RankingPredictionService(
                rankingPredictionRepository,
                gameRepository,
                homePageTeamRepository,
                userRepository);
    }

    @Test
    void getPrediction_includesOpaqueShareId() {
        UUID uniqueId = UUID.randomUUID();
        RankingPrediction prediction = new RankingPrediction("7", 2026, List.of("LG", "SS"));
        UserEntity user = UserEntity.builder()
                .id(7L)
                .uniqueId(uniqueId)
                .handle("@tester")
                .name("tester")
                .email("tester@example.com")
                .role("ROLE_USER")
                .build();

        when(rankingPredictionRepository.findByUserIdAndSeasonYear("7", 2026))
                .thenReturn(Optional.of(prediction));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(gameRepository.findTeamRankingsBySeason(2026)).thenReturn(List.of());
        when(gameRepository.findTeamRankingsBySeason(2025)).thenReturn(List.of());
        when(homePageTeamRepository.findAll()).thenReturn(List.of());

        RankingPredictionResponseDto response = rankingPredictionService.getPrediction("7", 2026);

        assertThat(response).isNotNull();
        assertThat(response.getShareId()).isEqualTo(uniqueId.toString());
        assertThat(response.getTeamIdsInOrder()).containsExactly("LG", "SS");
    }

    @Test
    void getPredictionByShareIdAndSeason_resolvesUuidToUserPrediction() {
        UUID uniqueId = UUID.randomUUID();
        RankingPrediction prediction = new RankingPrediction("7", 2026, List.of("LG"));
        UserEntity user = UserEntity.builder()
                .id(7L)
                .uniqueId(uniqueId)
                .handle("@tester")
                .name("tester")
                .email("tester@example.com")
                .role("ROLE_USER")
                .build();

        when(userRepository.findByUniqueId(uniqueId)).thenReturn(Optional.of(user));
        when(rankingPredictionRepository.findByUserIdAndSeasonYear("7", 2026))
                .thenReturn(Optional.of(prediction));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(gameRepository.findTeamRankingsBySeason(2026)).thenReturn(List.of());
        when(gameRepository.findTeamRankingsBySeason(2025)).thenReturn(List.of());
        when(homePageTeamRepository.findAll()).thenReturn(List.of());

        RankingPredictionResponseDto response = rankingPredictionService.getPredictionByShareIdAndSeason(
                uniqueId.toString(),
                2026);

        assertThat(response).isNotNull();
        assertThat(response.getShareId()).isEqualTo(uniqueId.toString());
        assertThat(response.getTeamIdsInOrder()).containsExactly("LG");
    }

    @Test
    void getPredictionByShareIdAndSeason_rejectsNonUuidIdentifiers() {
        assertThatThrownBy(() -> rankingPredictionService.getPredictionByShareIdAndSeason("7", 2026))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("공유 식별자 형식이 올바르지 않습니다.");
    }
}
