package com.example.cheerboard.repository;

import com.example.cheerboard.entity.CheerBattleLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheerBattleLogRepository extends JpaRepository<CheerBattleLog, Long> {
    Optional<CheerBattleLog> findByGameIdAndUserEmail(String gameId, String userEmail);

    boolean existsByGameIdAndUserEmail(String gameId, String userEmail);
}
