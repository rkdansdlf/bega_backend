package com.example.leaderboard.repository;

import com.example.leaderboard.entity.GameResultSettlementBaseline;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameResultSettlementBaselineRepository
        extends JpaRepository<GameResultSettlementBaseline, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT baseline FROM GameResultSettlementBaseline baseline WHERE baseline.id = :id")
    Optional<GameResultSettlementBaseline> findByIdForUpdate(@Param("id") Long id);
}
