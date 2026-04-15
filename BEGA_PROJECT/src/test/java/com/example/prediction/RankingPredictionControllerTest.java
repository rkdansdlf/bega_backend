package com.example.prediction;

import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.ConflictBusinessException;
import com.example.common.exception.GlobalExceptionHandler;
import com.example.common.exception.NotFoundBusinessException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RankingPredictionControllerTest {

    @Mock
    private RankingPredictionService rankingPredictionService;

    @InjectMocks
    private RankingPredictionController rankingPredictionController;

    @Test
    void getSharedPrediction_rejectsInvalidShareId() {
        assertThatThrownBy(() -> rankingPredictionController.getSharedPrediction("123", 2026))
                .isInstanceOf(BadRequestBusinessException.class)
                .hasMessageContaining("공유 식별자 형식이 올바르지 않습니다.");
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

    @Test
    void getSharedPrediction_returnsStandardNotFoundResponseWhenMissing() throws Exception {
        String shareId = UUID.randomUUID().toString();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(rankingPredictionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(rankingPredictionService.getPredictionByShareIdAndSeason(shareId, 2026)).thenReturn(null);

        mockMvc.perform(get("/api/predictions/ranking/share/{shareId}/{seasonYear}", shareId, 2026))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RANKING_PREDICTION_SHARE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("공유된 시즌 순위 예측을 찾을 수 없습니다."));
    }

    @Test
    void getPrediction_returnsNotFoundWhenSavedPredictionMissing() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(rankingPredictionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(rankingPredictionService.getPrediction("7", 2026))
                .thenThrow(new NotFoundBusinessException(
                        "RANKING_PREDICTION_NOT_FOUND",
                        "저장된 시즌 순위 예측을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/predictions/ranking")
                        .principal(() -> "7")
                        .param("seasonYear", "2026"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RANKING_PREDICTION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("저장된 시즌 순위 예측을 찾을 수 없습니다."));
    }

    @Test
    void getCurrentSeason_returnsConflictWhenPredictionPeriodClosed() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(rankingPredictionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(rankingPredictionService.getCurrentSeason())
                .thenThrow(new ConflictBusinessException(
                        "RANKING_PREDICTION_CLOSED",
                        "현재는 순위 예측 기간이 아닙니다. (예측 가능 기간: 11월 1일 ~ 5월 31일)"));

        mockMvc.perform(get("/api/predictions/ranking/current-season"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RANKING_PREDICTION_CLOSED"));
    }
}
