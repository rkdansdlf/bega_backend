package com.example.prediction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchRepository extends JpaRepository<Match, String>{

	// gameId로 경기 조회
	Optional<Match> findByGameId(String gameId);
	
	// 특정 날짜의 경기 목록
	List<Match> findByGameDate(LocalDate today);
	
	// 특정 날짜 범위의 경기 목록 (내림차순)
	List<Match> findByGameDateBetweenOrderByGameDateDesc(LocalDate startDate, LocalDate endDate);
	
	

	
	
	
	
		


}
