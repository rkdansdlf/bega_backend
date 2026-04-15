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
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.common.config.CacheConfig;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.ConflictBusinessException;
import com.example.common.exception.NotFoundBusinessException;
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
        CacheManager cacheManager = new ConcurrentMapCacheManager(
                CacheConfig.RANKING_PREDICTION_CONTEXT,
                CacheConfig.RANKING_SHARE_IDS);
        rankingPredictionService = new RankingPredictionService(
                rankingPredictionRepository,
                gameRepository,
                homePageTeamRepository,
                userRepository,
                cacheManager);
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

    @Test
    void getPrediction_reusesCachedRankingContextAndShareId() {
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
        when(gameRepository.findTeamRankingsBySeason(2026)).thenReturn(List.<Object[]>of(new Object[] { 1, "LG" }));
        when(gameRepository.findTeamRankingsBySeason(2025)).thenReturn(List.<Object[]>of(new Object[] { 3, "LG" }));
        when(homePageTeamRepository.findAll()).thenReturn(List.of());

        RankingPredictionResponseDto first = rankingPredictionService.getPrediction("7", 2026);
        RankingPredictionResponseDto second = rankingPredictionService.getPrediction("7", 2026);

        assertThat(first.getShareId()).isEqualTo(uniqueId.toString());
        assertThat(second.getShareId()).isEqualTo(uniqueId.toString());
        org.mockito.Mockito.verify(gameRepository, org.mockito.Mockito.times(1)).findTeamRankingsBySeason(2026);
        org.mockito.Mockito.verify(gameRepository, org.mockito.Mockito.times(1)).findTeamRankingsBySeason(2025);
        org.mockito.Mockito.verify(homePageTeamRepository, org.mockito.Mockito.times(1)).findAll();
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.times(1)).findById(7L);
    }

    @Test
    void getPrediction_throwsNotFoundWhenMissing() {
        when(rankingPredictionRepository.findByUserIdAndSeasonYear("7", 2026))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> rankingPredictionService.getPrediction("7", 2026))
                .isInstanceOf(NotFoundBusinessException.class)
                .hasMessageContaining("저장된 시즌 순위 예측을 찾을 수 없습니다.");
    }

    @Test
    void savePrediction_rejectsDuplicateTeamCodes() {
        try (MockedStatic<SeasonUtils> seasonUtils = Mockito.mockStatic(SeasonUtils.class)) {
            seasonUtils.when(SeasonUtils::isPredictionPeriod).thenReturn(true);
            seasonUtils.when(SeasonUtils::getCurrentPredictionSeason).thenReturn(2026);

            RankingPredictionRequestDto request = new RankingPredictionRequestDto();
            request.setSeasonYear(2026);
            request.setTeamIdsInOrder(List.of("LG", "DB", "SSG", "KT", "KH", "NC", "SS", "LT", "KIA", "LG"));

            assertThatThrownBy(() -> rankingPredictionService.savePrediction(request, "7"))
                    .isInstanceOf(BadRequestBusinessException.class)
                    .hasMessageContaining("중복 없이 10개 팀을 모두 선택해야 합니다.");
        }
    }

    @Test
    void savePrediction_rejectsUnsupportedTeamCodes() {
        try (MockedStatic<SeasonUtils> seasonUtils = Mockito.mockStatic(SeasonUtils.class)) {
            seasonUtils.when(SeasonUtils::isPredictionPeriod).thenReturn(true);
            seasonUtils.when(SeasonUtils::getCurrentPredictionSeason).thenReturn(2026);

            RankingPredictionRequestDto request = new RankingPredictionRequestDto();
            request.setSeasonYear(2026);
            request.setTeamIdsInOrder(List.of("LG", "DB", "SSG", "KT", "KH", "NC", "SS", "LT", "KIA", "XYZ"));

            assertThatThrownBy(() -> rankingPredictionService.savePrediction(request, "7"))
                    .isInstanceOf(BadRequestBusinessException.class)
                    .hasMessageContaining("지원하지 않는 팀 코드가 포함되어 있습니다.");
        }
    }

    @Test
    void savePrediction_mapsUniqueConstraintViolationToConflict() {
        try (MockedStatic<SeasonUtils> seasonUtils = Mockito.mockStatic(SeasonUtils.class)) {
            seasonUtils.when(SeasonUtils::isPredictionPeriod).thenReturn(true);
            seasonUtils.when(SeasonUtils::getCurrentPredictionSeason).thenReturn(2026);

            RankingPredictionRequestDto request = new RankingPredictionRequestDto();
            request.setSeasonYear(2026);
            request.setTeamIdsInOrder(List.of("LG", "DB", "SSG", "KT", "KH", "NC", "SS", "LT", "KIA", "HH"));

            when(rankingPredictionRepository.existsByUserIdAndSeasonYear("7", 2026))
                    .thenReturn(false);
            when(rankingPredictionRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(RankingPrediction.class)))
                    .thenThrow(new DataIntegrityViolationException("violates uk_rank_pred_user_season"));

            assertThatThrownBy(() -> rankingPredictionService.savePrediction(request, "7"))
                    .isInstanceOf(ConflictBusinessException.class)
                    .hasMessageContaining("이미");
        }
    }
}
