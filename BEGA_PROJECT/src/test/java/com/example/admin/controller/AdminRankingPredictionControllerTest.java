package com.example.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.dto.ApiResponse;
import com.example.prediction.RankingPredictionService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminRankingPredictionControllerTest {

    @Mock
    private RankingPredictionService rankingPredictionService;

    @InjectMocks
    private AdminRankingPredictionController controller;

    @Test
    @DisplayName("수동 정산 엔드포인트는 settleSeason을 호출하고 정산 건수를 반환한다")
    void settleSeason_triggersServiceAndReturnsSettledCount() {
        when(rankingPredictionService.settleSeason(2026)).thenReturn(3);

        ResponseEntity<ApiResponse<Integer>> result = controller.settleSeason(2026);

        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData()).isEqualTo(3);
        verify(rankingPredictionService).settleSeason(2026);
    }
}
