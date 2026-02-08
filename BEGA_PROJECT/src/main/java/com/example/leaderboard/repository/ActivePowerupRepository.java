package com.example.leaderboard.repository;

import com.example.leaderboard.entity.ActivePowerup;
import com.example.leaderboard.entity.UserPowerup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivePowerupRepository extends JpaRepository<ActivePowerup, Long> {

        // ============================================
        // USER ACTIVE POWERUPS
        // ============================================

        List<ActivePowerup> findByUserId(Long userId);

        @Query("SELECT ap FROM ActivePowerup ap WHERE ap.userId = :userId AND ap.used = false AND (ap.expiresAt IS NULL OR ap.expiresAt > :now)")
        List<ActivePowerup> findActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

        // ============================================
        // GAME-SPECIFIC POWERUPS
        // ============================================

        @Query("SELECT ap FROM ActivePowerup ap WHERE ap.userId = :userId AND ap.gameId = :gameId AND ap.used = false")
        List<ActivePowerup> findActiveForGame(@Param("userId") Long userId, @Param("gameId") String gameId);

        @Query("SELECT ap FROM ActivePowerup ap WHERE ap.userId = :userId AND ap.gameId = :gameId AND ap.powerupType = :type AND ap.used = false")
        Optional<ActivePowerup> findActiveForGameAndType(
                        @Param("userId") Long userId,
                        @Param("gameId") String gameId,
                        @Param("type") UserPowerup.PowerupType type);

        // ============================================
        // TYPE-SPECIFIC QUERIES
        // ============================================

        @Query("SELECT ap FROM ActivePowerup ap WHERE ap.userId = :userId AND ap.powerupType = :type AND ap.used = false AND (ap.expiresAt IS NULL OR ap.expiresAt > :now)")
        List<ActivePowerup> findActiveByUserIdAndType(
                        @Param("userId") Long userId,
                        @Param("type") UserPowerup.PowerupType type,
                        @Param("now") LocalDateTime now);

        // ============================================
        // EXPIRATION MANAGEMENT
        // ============================================

        @Query("SELECT ap FROM ActivePowerup ap WHERE ap.expiresAt IS NOT NULL AND ap.expiresAt < :now AND ap.used = false")
        List<ActivePowerup> findExpiredPowerups(@Param("now") LocalDateTime now);

        @Modifying
        @Query("UPDATE ActivePowerup ap SET ap.used = true WHERE ap.expiresAt IS NOT NULL AND ap.expiresAt < :now AND ap.used = false")
        int markExpiredAsUsed(@Param("now") LocalDateTime now);

        // ============================================
        // MARK AS USED
        // ============================================

        @Modifying
        @Query("UPDATE ActivePowerup ap SET ap.used = true WHERE ap.id = :id")
        int markAsUsed(@Param("id") Long id);

        @Modifying
        @Query("UPDATE ActivePowerup ap SET ap.used = true WHERE ap.userId = :userId AND ap.gameId = :gameId AND ap.powerupType = :type")
        int markAsUsedForGame(
                        @Param("userId") Long userId,
                        @Param("gameId") String gameId,
                        @Param("type") UserPowerup.PowerupType type);

        // ============================================
        // CLEANUP
        // ============================================

        @Modifying
        @Query("DELETE FROM ActivePowerup ap WHERE ap.used = true AND ap.activatedAt < :before")
        int deleteOldUsedPowerups(@Param("before") LocalDateTime before);

        // ============================================
        // STATISTICS
        // ============================================

        @Query("SELECT ap.powerupType, COUNT(ap) FROM ActivePowerup ap WHERE ap.userId = :userId AND ap.used = true GROUP BY ap.powerupType")
        List<Object[]> getUsedPowerupStats(@Param("userId") Long userId);
}
