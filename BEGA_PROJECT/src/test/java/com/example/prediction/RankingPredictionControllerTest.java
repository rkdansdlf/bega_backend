package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class RankingPredictionControllerTest {

    @Mock
    private RankingPredictionService rankingPredictionService;

    @InjectMocks
    private RankingPredictionController rankingPredictionController;

    @Test
    void getSharedPrediction_rejectsInvalidShareId() {
        ResponseEntity<?> response = rankingPredictionController.getSharedPrediction("123", 2026);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "공유 식별자 형식이 올바르지 않습니다."));
        verifyNoInteractions(rankingPredictionService);
    }

    @Test
    void getSharedPrediction_returnsSharedPredictionForValidShareId() {
        String shareId = UUID.randomUUID().toString();
        RankingPredictionResponseDto dto = new RankingPredictionResponseDto(
                1L,
                shareId,
                2026,
                List.of("LG", "SS"),
                List.of(),
                java.time.LocalDateTime.now());

        when(rankingPredictionService.getPredictionByShareIdAndSeason(shareId, 2026)).thenReturn(dto);

        ResponseEntity<?> response = rankingPredictionController.getSharedPrediction(shareId, 2026);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(dto);
    }
}
