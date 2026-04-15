package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.common.exception.GlobalExceptionHandler;
import com.example.common.exception.NotFoundBusinessException;

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

    @Test
    void getMatchDayShouldReturnDayNavigationPayload() {
        PredictionService predictionService = mock(PredictionService.class);
        PredictionRepository predictionRepository = mock(PredictionRepository.class);
        PredictionController controller = new PredictionController(predictionService, predictionRepository);
        LocalDate date = LocalDate.of(2026, 5, 5);

        MatchDayNavigationResponseDto payload = new MatchDayNavigationResponseDto(
                date,
                java.util.List.of(),
                LocalDate.of(2026, 5, 4),
                LocalDate.of(2026, 5, 6),
                true,
                true
        );
        when(predictionService.getMatchDayNavigation(date)).thenReturn(payload);

        ResponseEntity<MatchDayNavigationResponseDto> response = controller.getMatchDay(date);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDate()).isEqualTo(date);
        assertThat(response.getBody().getPrevDate()).isEqualTo(LocalDate.of(2026, 5, 4));
        assertThat(response.getBody().getNextDate()).isEqualTo(LocalDate.of(2026, 5, 6));
        assertThat(response.getBody().isHasPrev()).isTrue();
        assertThat(response.getBody().isHasNext()).isTrue();
        verify(predictionService).getMatchDayNavigation(date);
    }

    @Test
    void getMatchDetailShouldReturnNotFoundWhenMissing() throws Exception {
        PredictionService predictionService = mock(PredictionService.class);
        PredictionRepository predictionRepository = mock(PredictionRepository.class);
        PredictionController controller = new PredictionController(predictionService, predictionRepository);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(predictionService.getGameDetail("MISSING"))
                .thenThrow(new NotFoundBusinessException("MATCH_NOT_FOUND", "경기 정보를 찾을 수 없습니다."));

        mockMvc.perform(get("/api/matches/{gameId}", "MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MATCH_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("경기 정보를 찾을 수 없습니다."));
    }
}
