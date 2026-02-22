package com.example.prediction;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, Long>{

	// 특정 경기의 모든 투표 조회
	List<Prediction> findByGameId(String gameId);
	
	// 특정 경기에 특정 유저가 투표했는지 확인
	Optional<Prediction> findByGameIdAndUserId(String gameId, Long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Prediction p WHERE p.gameId = :gameId AND p.userId = :userId")
	Optional<Prediction> findByGameIdAndUserIdForWrite(
			@Param("gameId") String gameId,
			@Param("userId") Long userId);
	
	// 특정 경기에 특정 팀(votedTeam)이 받은 총 투표 수를 계산
	Long countByGameIdAndVotedTeam(String gameId, String votedTeam);
	
	// 특정 경기의 전체 투표 수
	Long countByGameId(String gameId);
	
	// 특정 유저의 모든 투표 조회 (최신순)
	List<Prediction> findAllByUserIdOrderByCreatedAtDesc(Long userId);

	// 특정 유저의 특정 경기들 투표 조회
	List<Prediction> findByUserIdAndGameIdIn(Long userId, Collection<String> gameIds);

}
