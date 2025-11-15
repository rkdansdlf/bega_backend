package com.example.homePage;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HomePageGameRepository extends JpaRepository<HomePageGame, Long>{

	// 특정 날짜의 경기 목록 조회
	List<HomePageGame> findByGameDate(LocalDate gameDate);
}
