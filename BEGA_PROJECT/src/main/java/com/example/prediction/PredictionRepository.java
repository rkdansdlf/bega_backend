package com.example.prediction;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, Long>{

	// 특정 경기의 모든 투표 조회
	List<Prediction> findByGameId(String gameId);
	
	// 특정 경기에 특정 유저가 투표했는지 확인
	Optional<Prediction> findByGameIdAndUserId(String gameId, Long userId);
	
	// 특정 경기에 특정 팀(votedTeam)이 받은 총 투표 수를 계산
	Long countByGameIdAndVotedTeam(String gameId, String votedTeam);
	
	// 특정 경기의 전체 투표 수
	Long countByGameId(String gameId);
	
	

}
