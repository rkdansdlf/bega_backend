package com.example.prediction.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import com.example.prediction.RankingPredictionService;

@ExtendWith(MockitoExtension.class)
class RankingPredictionSettlementSchedulerTest {

    @Mock
    private RankingPredictionService rankingPredictionService;

    @Test
    @DisplayName("정산 스케줄러가 비활성화되면 정산을 실행하지 않는다")
    void settlePreviousSeasonSkipsWhenDisabled() {
        RankingPredictionSettlementScheduler scheduler =
                new RankingPredictionSettlementScheduler(rankingPredictionService, false);

        scheduler.settlePreviousSeason();

        verify(rankingPredictionService, never()).settleSeason(anyInt());
    }

    @Test
    @DisplayName("정산 스케줄러가 활성화되면 현재 연도를 정산 대상 시즌으로 실행한다")
    void settlePreviousSeasonRunsWhenEnabled() {
        when(rankingPredictionService.settleSeason(anyInt())).thenReturn(3);
        RankingPredictionSettlementScheduler scheduler =
                new RankingPredictionSettlementScheduler(rankingPredictionService, true);

        scheduler.settlePreviousSeason();

        verify(rankingPredictionService).settleSeason(LocalDate.now().getYear());
    }

    @Test
    @DisplayName("정산 중 예외가 발생해도 스케줄러가 전파하지 않는다")
    void settlePreviousSeasonSwallowsExceptions() {
        when(rankingPredictionService.settleSeason(anyInt()))
                .thenThrow(new RuntimeException("db unavailable"));
        RankingPredictionSettlementScheduler scheduler =
                new RankingPredictionSettlementScheduler(rankingPredictionService, true);

        scheduler.settlePreviousSeason();
    }

    @Test
    @DisplayName("정산 cron은 env로 조절할 수 있다")
    void settlePreviousSeasonCronShouldBeConfigurable() throws NoSuchMethodException {
        Method method = RankingPredictionSettlementScheduler.class.getMethod("settlePreviousSeason");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron())
                .isEqualTo("${app.prediction.ranking-settlement-scheduler.cron:0 0 1 1 11 *}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }
}
