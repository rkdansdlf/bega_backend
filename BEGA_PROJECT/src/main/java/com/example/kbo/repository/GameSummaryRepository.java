package com.example.kbo.repository;

import com.example.kbo.entity.GameSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameSummaryRepository extends JpaRepository<GameSummaryEntity, Integer> {
    List<GameSummaryEntity> findAllByGameIdOrderBySummaryTypeAscIdAsc(String gameId);
}
