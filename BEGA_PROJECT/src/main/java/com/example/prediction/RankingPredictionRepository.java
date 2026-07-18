package com.example.prediction;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingPredictionRepository extends JpaRepository<RankingPrediction, Long> {

	// 특정 사용자의 특정 시즌 예측 찾기
	Optional<RankingPrediction> findByUserIdAndSeasonYear(String userId, Integer seasonYear);

	// 이미 예측을 저장했는지 확인
	boolean existsByUserIdAndSeasonYear(String userId, Integer seasonYear);

	// 특정 시즌에서 아직 정산되지 않은 예측 찾기 (시즌 정산 스케줄러용)
	List<RankingPrediction> findBySeasonYearAndSettledAtIsNull(int seasonYear);
}
