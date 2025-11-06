package com.example.BegaDiary.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BegaDiary.Entity.BegaGame;

@Repository
public interface BegaGameRepository extends JpaRepository<BegaGame, Long> {
	List<BegaGame> findByGameDate(LocalDate gameDate);
}
