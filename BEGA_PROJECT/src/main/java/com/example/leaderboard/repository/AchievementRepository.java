package com.example.leaderboard.repository;

import com.example.leaderboard.entity.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AchievementRepository extends JpaRepository<Achievement, Long> {

    // ============================================
    // BASIC QUERIES
    // ============================================

    Optional<Achievement> findByCode(String code);

    boolean existsByCode(String code);

    // ============================================
    // RARITY-BASED QUERIES
    // ============================================

    List<Achievement> findByRarity(Achievement.Rarity rarity);

    @Query("SELECT a FROM Achievement a ORDER BY " +
           "CASE a.rarity " +
           "  WHEN 'LEGENDARY' THEN 1 " +
           "  WHEN 'EPIC' THEN 2 " +
           "  WHEN 'RARE' THEN 3 " +
           "  ELSE 4 END, " +
           "a.pointsRequired DESC")
    List<Achievement> findAllOrderByRarityDesc();

    // ============================================
    // POINTS-BASED QUERIES
    // ============================================

    @Query("SELECT a FROM Achievement a WHERE a.pointsRequired <= :points ORDER BY a.pointsRequired DESC")
    List<Achievement> findUnlockableByPoints(@Param("points") Long points);

    @Query("SELECT a FROM Achievement a WHERE a.pointsRequired > :currentPoints ORDER BY a.pointsRequired ASC")
    List<Achievement> findNextAchievements(@Param("currentPoints") Long currentPoints);

    // ============================================
    // STATISTICS
    // ============================================

    @Query("SELECT a.rarity, COUNT(a) FROM Achievement a GROUP BY a.rarity")
    List<Object[]> countByRarity();

    @Query("SELECT COUNT(a) FROM Achievement a")
    Long countTotal();
}
