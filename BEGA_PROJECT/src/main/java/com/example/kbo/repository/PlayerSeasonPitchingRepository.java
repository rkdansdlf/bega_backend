package com.example.kbo.repository;

import java.util.List;
import java.util.Optional;

import com.example.kbo.entity.PlayerSeasonPitchingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerSeasonPitchingRepository
        extends JpaRepository<PlayerSeasonPitchingEntity, Integer> {

    Optional<PlayerSeasonPitchingEntity> findTopByPlayerIdAndSeasonOrderByIdDesc(
            Integer playerId,
            Integer season
    );

    List<PlayerSeasonPitchingEntity> findByPlayerIdOrderBySeasonDesc(Integer playerId);
}
