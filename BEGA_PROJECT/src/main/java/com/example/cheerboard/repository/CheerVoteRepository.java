package com.example.cheerboard.repository;

import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.entity.CheerVoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CheerVoteRepository extends JpaRepository<CheerVoteEntity, CheerVoteId> {
    List<CheerVoteEntity> findByGameId(String gameId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CheerVoteEntity v SET v.voteCount = v.voteCount + 1 WHERE v.gameId = :gameId AND v.teamId = :teamId")
    int incrementVoteCount(@Param("gameId") String gameId, @Param("teamId") String teamId);

    @Query("SELECT v.voteCount FROM CheerVoteEntity v WHERE v.gameId = :gameId AND v.teamId = :teamId")
    Integer findVoteCount(@Param("gameId") String gameId, @Param("teamId") String teamId);
}
