package com.example.BegaDiary.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.BegaDiary.DiaryWinning;
import com.example.demo.entity.UserEntity;

@Repository
public interface BegaDiaryRepository extends JpaRepository<BegaDiary, Long>{

	Optional<BegaDiary> findByDiaryDate(LocalDate diaryDate);
	boolean existsByUserAndDiaryDate(UserEntity user, LocalDate diaryDate);
	List<BegaDiary> findByUser_Id(Long id);
	
	// 총 개수
    @Query("SELECT COUNT(d) FROM BegaDiary d WHERE d.user.id = :userId")
    int countByUserId(@Param("userId") Long userId);
    
    // 승/패/무 개수
    @Query("SELECT COUNT(d) FROM BegaDiary d WHERE d.user.id = :userId AND d.winning = :winning")
    int countByUserIdAndWinning(@Param("userId") Long userId, @Param("winning") DiaryWinning winning);
    
    // 연간 개수
    @Query("SELECT COUNT(d) FROM BegaDiary d WHERE d.user.id = :userId AND YEAR(d.diaryDate) = :year")
    int countByUserIdAndYear(@Param("userId") Long userId, @Param("year") int year);
    
    // 연간 승리 개수
    @Query("SELECT COUNT(d) FROM BegaDiary d WHERE d.user.id = :userId AND YEAR(d.diaryDate) = :year AND d.winning = 'WIN'")
    int countYearlyWins(@Param("userId") Long userId, @Param("year") int year);
    
    // 가장 많이 간 구장 (TOP 1)
    @Query("SELECT d.stadium, COUNT(d) as cnt FROM BegaDiary d " +
           "WHERE d.user.id = :userId AND d.stadium IS NOT NULL " +
           "GROUP BY d.stadium ORDER BY cnt DESC")
    List<Object[]> findMostVisitedStadium(@Param("userId") Long userId);
    
    // 가장 행복했던 달 (좋음/최고 감정)
    @Query("SELECT MONTH(d.diaryDate), COUNT(d) as cnt FROM BegaDiary d " +
           "WHERE d.user.id = :userId AND (d.mood = 'BEST') " +
           "GROUP BY MONTH(d.diaryDate) ORDER BY cnt DESC")
    List<Object[]> findHappiestMonth(@Param("userId") Long userId);
    
    // 첫 직관 날짜
    @Query("SELECT MIN(d.diaryDate) FROM BegaDiary d WHERE d.user.id = :userId")
    LocalDate findFirstDiaryDate(@Param("userId") Long userId);
    
    @Query("SELECT COUNT(d) FROM BegaDiary d WHERE d.user.id = :userId AND YEAR(d.diaryDate) = :year AND MONTH(d.diaryDate) = :month")
    int countByUserIdAndYearAndMonth(@Param("userId") Long userId, @Param("year") int year, @Param("month") int month);
	

}
