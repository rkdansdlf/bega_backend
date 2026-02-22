package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.example.auth.repository.UserRepository;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.GameInningScoreRepository;
import com.example.kbo.repository.GameMetadataRepository;
import com.example.kbo.repository.GameSummaryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
                any(LocalDate.class),
                anyBoolean(),
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
                any(LocalDate.class),
                anyBoolean(),
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
