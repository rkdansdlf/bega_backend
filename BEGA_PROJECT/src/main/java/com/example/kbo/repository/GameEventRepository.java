package com.example.kbo.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.kbo.entity.GameEventEntity;

public interface GameEventRepository extends JpaRepository<GameEventEntity, Integer> {

    List<GameEventEntity> findByGameIdAndEventSeqGreaterThanOrderByEventSeqAsc(
            String gameId,
            Integer eventSeq,
            Pageable pageable);

    List<GameEventEntity> findByGameIdOrderByEventSeqDesc(String gameId, Pageable pageable);

    Optional<GameEventEntity> findFirstByGameIdOrderByEventSeqDesc(String gameId);

    @Query(value = """
            SELECT e.*
            FROM game_events e
            JOIN (
                SELECT game_id, MAX(event_seq) AS max_event_seq
                FROM game_events
                WHERE game_id IN (:gameIds)
                GROUP BY game_id
            ) latest
              ON latest.game_id = e.game_id
             AND latest.max_event_seq = e.event_seq
            WHERE e.game_id IN (:gameIds)
            """, nativeQuery = true)
    List<GameEventEntity> findLatestByGameIds(@Param("gameIds") Collection<String> gameIds);
}
