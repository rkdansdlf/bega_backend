package com.example.leaderboard.repository;

import com.example.leaderboard.entity.Achievement;
import com.example.leaderboard.entity.UserAchievement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {

    // ============================================
    // USER ACHIEVEMENTS
    // ============================================

    List<UserAchievement> findByUserId(Long userId);

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement WHERE ua.userId = :userId ORDER BY ua.earnedAt DESC")
    List<UserAchievement> findByUserIdWithAchievement(@Param("userId") Long userId);

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement WHERE ua.userId = :userId ORDER BY ua.earnedAt DESC")
    List<UserAchievement> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    // ============================================
    // CHECK EARNED
    // ============================================

    boolean existsByUserIdAndAchievement(Long userId, Achievement achievement);

    @Query("SELECT CASE WHEN COUNT(ua) > 0 THEN true ELSE false END FROM UserAchievement ua WHERE ua.userId = :userId AND ua.achievement.code = :code")
    boolean hasAchievement(@Param("userId") Long userId, @Param("code") String code);

    Optional<UserAchievement> findByUserIdAndAchievement(Long userId, Achievement achievement);

    // ============================================
    // ACHIEVEMENT CODE QUERIES
    // ============================================

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement a WHERE ua.userId = :userId AND a.code = :code")
    Optional<UserAchievement> findByUserIdAndAchievementCode(@Param("userId") Long userId, @Param("code") String code);

    @Query("SELECT a.code FROM UserAchievement ua JOIN ua.achievement a WHERE ua.userId = :userId")
    List<String> findEarnedAchievementCodes(@Param("userId") Long userId);

    // ============================================
    // RARITY-BASED QUERIES
    // ============================================

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement a WHERE ua.userId = :userId AND a.rarity = :rarity")
    List<UserAchievement> findByUserIdAndRarity(@Param("userId") Long userId, @Param("rarity") Achievement.Rarity rarity);

    // ============================================
    // RECENT ACHIEVEMENTS (GLOBAL FEED)
    // ============================================

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement ORDER BY ua.earnedAt DESC")
    List<UserAchievement> findRecentAchievements(Pageable pageable);

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement a WHERE a.rarity IN :rarities ORDER BY ua.earnedAt DESC")
    List<UserAchievement> findRecentRareAchievements(@Param("rarities") List<Achievement.Rarity> rarities, Pageable pageable);

    // ============================================
    // TIME-BASED QUERIES
    // ============================================

    @Query("SELECT ua FROM UserAchievement ua JOIN FETCH ua.achievement WHERE ua.userId = :userId AND ua.earnedAt >= :since")
    List<UserAchievement> findEarnedSince(@Param("userId") Long userId, @Param("since") Instant since);

    // ============================================
    // STATISTICS
    // ============================================

    @Query("SELECT COUNT(ua) FROM UserAchievement ua WHERE ua.userId = :userId")
    Long countByUserId(@Param("userId") Long userId);

    @Query("SELECT a.rarity, COUNT(ua) FROM UserAchievement ua JOIN ua.achievement a WHERE ua.userId = :userId GROUP BY a.rarity")
    List<Object[]> countByUserIdGroupByRarity(@Param("userId") Long userId);

    @Query("SELECT ua.achievement.id, COUNT(ua) FROM UserAchievement ua GROUP BY ua.achievement.id")
    List<Object[]> countUsersPerAchievement();

    // ============================================
    // LEADERBOARD BY ACHIEVEMENTS
    // ============================================

    @Query("SELECT ua.userId, COUNT(ua) as cnt FROM UserAchievement ua GROUP BY ua.userId ORDER BY cnt DESC")
    List<Object[]> findTopAchievers(Pageable pageable);
}
