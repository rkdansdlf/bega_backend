package com.example.leaderboard.repository;

import com.example.leaderboard.entity.ScoreEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ScoreEventRepository extends JpaRepository<ScoreEvent, Long> {

    // ============================================
    // USER SCORE HISTORY
    // ============================================

    List<ScoreEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<ScoreEvent> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT se FROM ScoreEvent se WHERE se.userId = :userId AND se.createdAt >= :since ORDER BY se.createdAt DESC")
    List<ScoreEvent> findRecentByUserId(@Param("userId") Long userId, @Param("since") Instant since);

    // ============================================
    // RECENT SCORES (GLOBAL FEED)
    // ============================================

    @Query("SELECT se FROM ScoreEvent se ORDER BY se.createdAt DESC")
    List<ScoreEvent> findRecentScores(Pageable pageable);

    @Query("SELECT se FROM ScoreEvent se WHERE se.eventType = :eventType ORDER BY se.createdAt DESC")
    List<ScoreEvent> findRecentByEventType(@Param("eventType") ScoreEvent.EventType eventType, Pageable pageable);

    // ============================================
    // GAME-SPECIFIC QUERIES
    // ============================================

    List<ScoreEvent> findByGameId(String gameId);

    @Query("SELECT se FROM ScoreEvent se WHERE se.gameId = :gameId AND se.userId = :userId")
    List<ScoreEvent> findByGameIdAndUserId(@Param("gameId") String gameId, @Param("userId") Long userId);

    // ============================================
    // PREDICTION-SPECIFIC QUERIES
    // ============================================

    List<ScoreEvent> findByPredictionId(Long predictionId);

    boolean existsByPredictionIdAndUserId(Long predictionId, Long userId);

    // ============================================
    // STATISTICS
    // ============================================

    @Query("SELECT SUM(se.finalScore) FROM ScoreEvent se WHERE se.userId = :userId")
    Long sumFinalScoreByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(se) FROM ScoreEvent se WHERE se.userId = :userId AND se.eventType = :eventType")
    Long countByUserIdAndEventType(@Param("userId") Long userId, @Param("eventType") ScoreEvent.EventType eventType);

    @Query("SELECT se.eventType, COUNT(se), SUM(se.finalScore) FROM ScoreEvent se WHERE se.userId = :userId GROUP BY se.eventType")
    List<Object[]> getScoreBreakdownByUserId(@Param("userId") Long userId);

    // ============================================
    // TIME-BASED QUERIES
    // ============================================

    @Query("SELECT SUM(se.finalScore) FROM ScoreEvent se WHERE se.userId = :userId AND se.createdAt >= :since")
    Long sumFinalScoreSince(@Param("userId") Long userId, @Param("since") Instant since);

    @Query("SELECT se FROM ScoreEvent se WHERE se.createdAt >= :since ORDER BY se.finalScore DESC")
    List<ScoreEvent> findTopScoresSince(@Param("since") Instant since, Pageable pageable);

    // ============================================
    // STREAK ANALYSIS
    // ============================================

    @Query("SELECT MAX(se.streakCount) FROM ScoreEvent se WHERE se.userId = :userId")
    Integer findMaxStreakByUserId(@Param("userId") Long userId);

    @Query("SELECT se FROM ScoreEvent se WHERE se.streakCount >= :minStreak ORDER BY se.streakCount DESC, se.createdAt DESC")
    List<ScoreEvent> findHighStreakEvents(@Param("minStreak") int minStreak, Pageable pageable);
}
