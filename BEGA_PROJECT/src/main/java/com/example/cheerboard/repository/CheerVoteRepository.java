package com.example.cheerboard.repository;

import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.entity.CheerVoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CheerVoteRepository extends JpaRepository<CheerVoteEntity, CheerVoteId> {
    List<CheerVoteEntity> findByGameId(String gameId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CheerVoteEntity v SET v.voteCount = v.voteCount + 1 WHERE v.gameId = :gameId AND v.teamId = :teamId")
    int incrementVoteCount(@Param("gameId") String gameId, @Param("teamId") String teamId);

    /**
     * Inserts the first aggregate row with a plain INSERT. {@code saveAndFlush} would merge an
     * existing row and overwrite its count with 0, so a late first vote silently erased earlier
     * increments. A duplicate key here fails with {@code DataIntegrityViolationException} instead.
     */
    @Transactional
    @Modifying
    @Query(value = "INSERT INTO cheer_battle_votes (game_id, team_id, vote_count) VALUES (:gameId, :teamId, 0)",
            nativeQuery = true)
    int insertFirstVote(@Param("gameId") String gameId, @Param("teamId") String teamId);

    @Query("SELECT v.voteCount FROM CheerVoteEntity v WHERE v.gameId = :gameId AND v.teamId = :teamId")
    Integer findVoteCount(@Param("gameId") String gameId, @Param("teamId") String teamId);
}
