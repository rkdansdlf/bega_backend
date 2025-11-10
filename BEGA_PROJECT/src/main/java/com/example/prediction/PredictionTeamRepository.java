package com.example.prediction;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PredictionTeamRepository extends JpaRepository<PredictionTeamEntity, String> {
	
	// teamId(약자)를 사용하여 해당 엔티티 전체를 조회하는 기본 메서드
	Optional<PredictionTeamEntity> findById(String teamId);
}
