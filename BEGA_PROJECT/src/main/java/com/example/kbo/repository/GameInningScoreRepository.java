package com.example.kbo.repository;

import com.example.kbo.entity.GameInningScoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameInningScoreRepository extends JpaRepository<GameInningScoreEntity, Integer> {
    List<GameInningScoreEntity> findAllByGameIdOrderByInningAscTeamSideAsc(String gameId);
}
