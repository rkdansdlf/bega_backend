package com.example.leaderboard.repository;

import com.example.leaderboard.entity.UserScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserScoreRepository extends JpaRepository<UserScore, Long> {

    Optional<UserScore> findByUserId(Long userId);

    // ============================================
    // LEADERBOARD QUERIES (TOTAL SCORE)
    // ============================================

    @Query("SELECT us FROM UserScore us ORDER BY us.totalScore DESC")
    Page<UserScore> findAllByTotalScoreDesc(Pageable pageable);

    @Query("SELECT us FROM UserScore us ORDER BY us.seasonScore DESC")
    Page<UserScore> findAllBySeasonScoreDesc(Pageable pageable);

    @Query("SELECT us FROM UserScore us ORDER BY us.monthlyScore DESC")
    Page<UserScore> findAllByMonthlyScoreDesc(Pageable pageable);

    @Query("SELECT us FROM UserScore us ORDER BY us.weeklyScore DESC")
    Page<UserScore> findAllByWeeklyScoreDesc(Pageable pageable);

    // ============================================
    // HOT STREAKS
    // ============================================

    @Query("SELECT us FROM UserScore us WHERE us.currentStreak >= :minStreak ORDER BY us.currentStreak DESC")
    List<UserScore> findHotStreaks(@Param("minStreak") int minStreak, Pageable pageable);

    @Query("SELECT us FROM UserScore us WHERE us.currentStreak >= 3 ORDER BY us.currentStreak DESC")
    List<UserScore> findTopStreakers(Pageable pageable);

    // ============================================
    // RANKING QUERIES
    // ============================================

    @Query("SELECT COUNT(us) + 1 FROM UserScore us WHERE us.totalScore > :score")
    Long findTotalRankByScore(@Param("score") Long score);

    @Query("SELECT COUNT(us) + 1 FROM UserScore us WHERE us.seasonScore > :score")
    Long findSeasonRankByScore(@Param("score") Long score);

    @Query("SELECT COUNT(us) + 1 FROM UserScore us WHERE us.monthlyScore > :score")
    Long findMonthlyRankByScore(@Param("score") Long score);

    @Query("SELECT COUNT(us) + 1 FROM UserScore us WHERE us.weeklyScore > :score")
    Long findWeeklyRankByScore(@Param("score") Long score);

    // ============================================
    // STATISTICS
    // ============================================

    @Query("SELECT COUNT(us) FROM UserScore us")
    Long countTotalUsers();

    @Query("SELECT us FROM UserScore us WHERE us.userLevel >= :level ORDER BY us.userLevel DESC, us.experiencePoints DESC")
    List<UserScore> findByLevelGreaterThanEqual(@Param("level") int level, Pageable pageable);

    @Query("SELECT AVG(us.totalScore) FROM UserScore us")
    Double findAverageScore();

    // ============================================
    // LEVEL DISTRIBUTION
    // ============================================

    @Query("SELECT CASE " +
            "  WHEN us.userLevel BETWEEN 1 AND 10 THEN 'ROOKIE' " +
            "  WHEN us.userLevel BETWEEN 11 AND 30 THEN 'MINOR_LEAGUER' " +
            "  WHEN us.userLevel BETWEEN 31 AND 60 THEN 'MAJOR_LEAGUER' " +
            "  ELSE 'HALL_OF_FAME' " +
            "END, COUNT(us) " +
            "FROM UserScore us " +
            "GROUP BY CASE " +
            "  WHEN us.userLevel BETWEEN 1 AND 10 THEN 'ROOKIE' " +
            "  WHEN us.userLevel BETWEEN 11 AND 30 THEN 'MINOR_LEAGUER' " +
            "  WHEN us.userLevel BETWEEN 31 AND 60 THEN 'MAJOR_LEAGUER' " +
            "  ELSE 'HALL_OF_FAME' " +
            "END")
    List<Object[]> countByRankTier();
}
