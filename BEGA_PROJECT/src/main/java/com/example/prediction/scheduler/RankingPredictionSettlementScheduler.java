package com.example.prediction.scheduler;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.prediction.RankingPredictionService;

import lombok.extern.slf4j.Slf4j;

/**
 * 순위 예측 시즌 정산 스케줄러.
 * 다음 시즌 예측 기간이 열리는 11월 1일 새벽, 방금 끝난 시즌의 확정 순위와
 * 예측을 비교해 정산한다.
 */
@Component
@Slf4j
public class RankingPredictionSettlementScheduler {

	private final RankingPredictionService rankingPredictionService;
	private final boolean enabled;

	public RankingPredictionSettlementScheduler(
			RankingPredictionService rankingPredictionService,
			@Value("${app.prediction.ranking-settlement-scheduler.enabled:true}") boolean enabled) {
		this.rankingPredictionService = rankingPredictionService;
		this.enabled = enabled;
	}

	@Scheduled(cron = "${app.prediction.ranking-settlement-scheduler.cron:0 0 1 1 11 *}")
	public void settlePreviousSeason() {
		if (!enabled) {
			log.debug("Skipping ranking prediction settlement because "
					+ "app.prediction.ranking-settlement-scheduler.enabled=false");
			return;
		}

		int seasonToSettle = LocalDate.now().getYear();
		try {
			int settledCount = rankingPredictionService.settleSeason(seasonToSettle);
			if (settledCount > 0) {
				log.info("Ranking prediction settlement complete for season {}: {} predictions settled",
						seasonToSettle, settledCount);
			} else {
				log.debug("No ranking predictions settled for season {} (already settled or data not ready)",
						seasonToSettle);
			}
		} catch (Exception e) {
			log.error("Error settling ranking predictions for season {}: {}", seasonToSettle, e.getMessage(), e);
		}
	}
}
