package com.example.prediction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface MatchRepository extends JpaRepository<Match, String>{

	// gameId로 경기 조회
	Optional<Match> findByGameId(String gameId);
	
	// 특정 날짜의 경기 목록
	List<Match> findByGameDate(LocalDate today);
	
	// @Query를 단일로 사용하여 오류 해결. Service에서 날짜를 계산하여 삽입
		@Query("SELECT m FROM Match m " +
		           "WHERE m.gameDate BETWEEN :startDate AND :endDate " +
		           "AND m.winningTeam IS NOT NULL " +
		           "ORDER BY m.gameDate DESC")
		    List<Match> findCompletedByDateRange(
		        @Param("startDate") LocalDate startDate, 
		        @Param("endDate") LocalDate endDate);
	
	
	
		


}
