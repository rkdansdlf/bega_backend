package com.example.kbo.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.kbo.entity.GameEventEntity;

@Repository
public interface GameEventRepository extends JpaRepository<GameEventEntity, Integer> {

    List<GameEventEntity> findByGameIdAndEventSeqGreaterThanOrderByEventSeqAsc(
            String gameId,
            Integer eventSeq,
            Pageable pageable);

    List<GameEventEntity> findByGameIdOrderByEventSeqDesc(String gameId, Pageable pageable);

    Optional<GameEventEntity> findFirstByGameIdOrderByEventSeqDesc(String gameId);

    @Query("""
            SELECT e
            FROM GameEventEntity e
            WHERE e.gameId IN :gameIds
              AND e.eventSeq = (
                  SELECT MAX(latest.eventSeq)
                  FROM GameEventEntity latest
                  WHERE latest.gameId = e.gameId
              )
            """)
    List<GameEventEntity> findLatestByGameIds(@Param("gameIds") Collection<String> gameIds);
}
