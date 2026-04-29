package com.example.kbo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.kbo.entity.GamePlayByPlayEntity;

@Repository
public interface GamePlayByPlayRepository extends JpaRepository<GamePlayByPlayEntity, Integer> {

    List<GamePlayByPlayEntity> findByGameIdAndIdGreaterThanOrderByIdAsc(
            String gameId,
            Integer id,
            Pageable pageable);

    List<GamePlayByPlayEntity> findByGameIdOrderByIdDesc(String gameId, Pageable pageable);

    Optional<GamePlayByPlayEntity> findFirstByGameIdOrderByIdDesc(String gameId);
}
