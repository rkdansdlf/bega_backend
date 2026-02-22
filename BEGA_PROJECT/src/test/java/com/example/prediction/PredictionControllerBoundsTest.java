package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PredictionControllerBoundsTest {

    @Test
    void getMatchBoundsShouldReturnBoundsPayload() {
        PredictionService predictionService = mock(PredictionService.class);
        PredictionRepository predictionRepository = mock(PredictionRepository.class);
        PredictionController controller = new PredictionController(predictionService, predictionRepository);

        MatchBoundsResponseDto bounds = new MatchBoundsResponseDto(
                LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 10, 1),
                true
        );
        when(predictionService.getMatchBounds()).thenReturn(bounds);

        ResponseEntity<MatchBoundsResponseDto> response = controller.getMatchBounds();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isHasData()).isTrue();
        assertThat(response.getBody().getEarliestGameDate()).isEqualTo(LocalDate.of(2026, 3, 20));
        assertThat(response.getBody().getLatestGameDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        verify(predictionService).getMatchBounds();
    }
}
