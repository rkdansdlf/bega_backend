package com.example.kbo.repository;

import java.util.List;
import java.util.Optional;

import com.example.kbo.entity.PlayerSeasonBattingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerSeasonBattingRepository
        extends JpaRepository<PlayerSeasonBattingEntity, Integer> {

    Optional<PlayerSeasonBattingEntity> findTopByPlayerIdAndSeasonOrderByIdDesc(
            Integer playerId,
            Integer season
    );

    List<PlayerSeasonBattingEntity> findByPlayerIdOrderBySeasonDesc(Integer playerId);
}
