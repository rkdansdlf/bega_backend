package com.example.leaderboard.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.leaderboard.service.GameResultScoringService;
import java.lang.reflect.Method;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class GameResultSchedulerTest {

    @Mock
    private GameResultScoringService gameResultScoringService;

    @Test
    @DisplayName("game result scheduler가 비활성화되면 오늘 경기 정산을 실행하지 않는다")
    void processFinishedGamesSkipsWhenDisabled() {
        GameResultScheduler scheduler = new GameResultScheduler(gameResultScoringService, false);

        scheduler.processFinishedGames();

        verify(gameResultScoringService, never()).processGamesForDate(any(LocalDate.class));
    }

    @Test
    @DisplayName("game result scheduler가 활성화되면 오늘 경기 정산을 실행한다")
    void processFinishedGamesRunsWhenEnabled() {
        GameResultScheduler scheduler = new GameResultScheduler(gameResultScoringService, true);

        scheduler.processFinishedGames();

        verify(gameResultScoringService).processGamesForDate(any(LocalDate.class));
    }

    @Test
    @DisplayName("오늘 경기 정산 scheduler는 fixed-delay와 initial-delay를 env로 조절할 수 있다")
    void processFinishedGamesScheduleShouldBeConfigurable() throws NoSuchMethodException {
        Method method = GameResultScheduler.class.getMethod("processFinishedGames");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${app.leaderboard.game-result-scheduler.fixed-delay-ms:600000}");
        assertThat(scheduled.initialDelayString())
                .isEqualTo("${app.leaderboard.game-result-scheduler.initial-delay-ms:600000}");
    }

    @Test
    @DisplayName("어제 경기 정산 cron은 env로 조절할 수 있다")
    void processYesterdayGamesScheduleShouldBeConfigurable() throws NoSuchMethodException {
        Method method = GameResultScheduler.class.getMethod("processYesterdayGames");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron())
                .isEqualTo("${app.leaderboard.game-result-scheduler.yesterday-cron:0 0 2 * * *}");
    }
}
