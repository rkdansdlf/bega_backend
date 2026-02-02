package com.example.leaderboard.scheduler;

import com.example.leaderboard.service.GameResultScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 게임 결과 점수 처리 스케줄러
 * 종료된 게임의 예측 결과를 주기적으로 처리합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GameResultScheduler {

    private final GameResultScoringService gameResultScoringService;

    /**
     * 오늘 종료된 게임들의 점수 처리
     * 매 10분마다 실행 (경기 종료 후 점수 처리)
     */
    @Scheduled(fixedRate = 600000) // 10분 = 600,000ms
    public void processFinishedGames() {
        log.debug("Starting scheduled game result processing...");

        try {
            LocalDate today = LocalDate.now();
            int processed = gameResultScoringService.processGamesForDate(today);

            if (processed > 0) {
                log.info("Scheduled processing: {} predictions processed for date {}", processed, today);
            }
        } catch (Exception e) {
            log.error("Error in scheduled game result processing: {}", e.getMessage(), e);
        }
    }

    /**
     * 어제 게임 정산 (늦은 밤 경기 보정)
     * 매일 새벽 2시에 실행하여 전날 미처리 게임 정산
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void processYesterdayGames() {
        log.info("Starting yesterday's game result processing...");

        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            int processed = gameResultScoringService.processGamesForDate(yesterday);

            log.info("Yesterday processing complete: {} predictions processed for date {}", processed, yesterday);
        } catch (Exception e) {
            log.error("Error processing yesterday's games: {}", e.getMessage(), e);
        }
    }
}
