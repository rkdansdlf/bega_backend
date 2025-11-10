package com.example.BegaDiary.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BegaDiary.Entity.BegaDiary;

@Repository
public interface BegaDiaryRepository extends JpaRepository<BegaDiary, Long>{

	Optional<BegaDiary> findByDiaryDate(LocalDate diaryDate);
	boolean existsByDiaryDate(LocalDate diaryDate);
	List<BegaDiary> findByUser_Id(Long id);
	

}
