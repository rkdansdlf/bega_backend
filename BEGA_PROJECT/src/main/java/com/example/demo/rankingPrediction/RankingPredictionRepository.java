package com.example.demo.rankingPrediction;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RankingPredictionRepository extends JpaRepository<RankingPrediction, Long> {

	// 특정 사용자의 특정 시즌 예측 찾기
	Optional<RankingPrediction> findByUserIdAndSeasonYear(String userId, Integer seasonYear);

	// 이미 예측을 저장했는지 확인
	boolean existsByUserIdAndSeasonYear(String userId, Integer seasonYear);
}
