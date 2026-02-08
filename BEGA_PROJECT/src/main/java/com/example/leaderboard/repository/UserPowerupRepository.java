package com.example.leaderboard.repository;

import com.example.leaderboard.entity.UserPowerup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPowerupRepository extends JpaRepository<UserPowerup, Long> {

    // ============================================
    // USER INVENTORY
    // ============================================

    List<UserPowerup> findByUserId(Long userId);

    Optional<UserPowerup> findByUserIdAndPowerupType(Long userId, UserPowerup.PowerupType powerupType);

    @Query("SELECT up FROM UserPowerup up WHERE up.userId = :userId AND up.quantity > 0")
    List<UserPowerup> findAvailablePowerups(@Param("userId") Long userId);

    // ============================================
    // QUANTITY OPERATIONS
    // ============================================

    @Modifying
    @Query("UPDATE UserPowerup up SET up.quantity = up.quantity + :amount WHERE up.userId = :userId AND up.powerupType = :type")
    int addPowerup(@Param("userId") Long userId, @Param("type") UserPowerup.PowerupType type, @Param("amount") int amount);

    @Modifying
    @Query("UPDATE UserPowerup up SET up.quantity = up.quantity - 1 WHERE up.userId = :userId AND up.powerupType = :type AND up.quantity > 0")
    int usePowerup(@Param("userId") Long userId, @Param("type") UserPowerup.PowerupType type);

    // ============================================
    // STATISTICS
    // ============================================

    @Query("SELECT up.powerupType, SUM(up.quantity) FROM UserPowerup up WHERE up.userId = :userId GROUP BY up.powerupType")
    List<Object[]> getPowerupSummary(@Param("userId") Long userId);

    @Query("SELECT COUNT(up) FROM UserPowerup up WHERE up.userId = :userId AND up.quantity > 0")
    Long countAvailablePowerupTypes(@Param("userId") Long userId);

    // ============================================
    // CHECK AVAILABILITY
    // ============================================

    @Query("SELECT CASE WHEN up.quantity > 0 THEN true ELSE false END FROM UserPowerup up WHERE up.userId = :userId AND up.powerupType = :type")
    Boolean hasPowerup(@Param("userId") Long userId, @Param("type") UserPowerup.PowerupType type);

    default boolean hasAvailablePowerup(Long userId, UserPowerup.PowerupType type) {
        Boolean result = hasPowerup(userId, type);
        return result != null && result;
    }
}
