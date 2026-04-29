package com.example.prediction;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.exception.GlobalExceptionHandler;
import com.example.kbo.validation.ManualBaseballDataMissingItem;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PredictionControllerManualDataRequiredTest {

    private MockMvc mockMvc;
    private PredictionService predictionService;

    @BeforeEach
    void setUp() {
        predictionService = mock(PredictionService.class);
        PredictionRepository predictionRepository = mock(PredictionRepository.class);
        PredictionController controller = new PredictionController(predictionService, predictionRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("prediction day API는 수동 야구 데이터 요청 계약을 409로 반환한다")
    void getMatchDayReturnsManualBaseballDataRequiredPayload() throws Exception {
        given(predictionService.getMatchDayNavigation(eq(LocalDate.of(2026, 4, 5))))
                .willThrow(new ManualBaseballDataRequiredException(
                        new ManualBaseballDataRequest(
                                "prediction.matches_by_date",
                                List.of(new ManualBaseballDataMissingItem(
                                        "game_date",
                                        "경기 날짜",
                                        "요청한 날짜의 경기 row가 없어 일정을 확인할 수 없습니다.",
                                        "YYYY-MM-DD")),
                                "다음 야구 데이터가 필요합니다: 날짜=2026-04-05, 경기 날짜",
                                true
                        )));

        mockMvc.perform(get("/api/matches/day").param("date", "2026-04-05"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MANUAL_BASEBALL_DATA_REQUIRED"))
                .andExpect(jsonPath("$.message").value("야구 데이터 준비가 필요합니다. 운영자가 데이터를 제공하면 다시 확인할 수 있습니다."))
                .andExpect(jsonPath("$.data.scope").value("prediction.matches_by_date"))
                .andExpect(jsonPath("$.data.blocking").value(true))
                .andExpect(jsonPath("$.data.missingItems[0].key").value("game_date"))
                .andExpect(jsonPath("$.data.missingItems[0].expected_format").value("YYYY-MM-DD"));
    }

    @Test
    @DisplayName("prediction detail API는 주요 기록 누락을 수동 야구 데이터 요청 계약으로 반환한다")
    void getMatchDetailReturnsManualBaseballDataRequiredPayload() throws Exception {
        given(predictionService.getGameDetail(eq("20260419HHLT0")))
                .willThrow(new ManualBaseballDataRequiredException(
                        new ManualBaseballDataRequest(
                                "prediction.game_detail.summary",
                                List.of(new ManualBaseballDataMissingItem(
                                        "game_summary",
                                        "경기 주요 기록",
                                        "완료 경기의 주요 기록 row가 없습니다.",
                                        "game_summary.summary_type, player_name, detail_text")),
                                "다음 야구 데이터가 필요합니다: 경기 ID=20260419HHLT0, 경기 주요 기록",
                                true
                        )));

        mockMvc.perform(get("/api/matches/20260419HHLT0"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MANUAL_BASEBALL_DATA_REQUIRED"))
                .andExpect(jsonPath("$.message").value("야구 데이터 준비가 필요합니다. 운영자가 데이터를 제공하면 다시 확인할 수 있습니다."))
                .andExpect(jsonPath("$.data.scope").value("prediction.game_detail.summary"))
                .andExpect(jsonPath("$.data.blocking").value(true))
                .andExpect(jsonPath("$.data.missingItems[0].key").value("game_summary"))
                .andExpect(jsonPath("$.data.missingItems[0].expected_format")
                        .value("game_summary.summary_type, player_name, detail_text"));
    }
}
