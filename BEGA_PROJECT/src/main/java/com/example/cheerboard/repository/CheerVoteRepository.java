package com.example.cheerboard.repository;

import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.entity.CheerVoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CheerVoteRepository extends JpaRepository<CheerVoteEntity, CheerVoteId> {
    List<CheerVoteEntity> findByGameId(String gameId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CheerVoteEntity v SET v.voteCount = v.voteCount + 1 WHERE v.gameId = :gameId AND v.teamId = :teamId")
    int incrementVoteCount(@Param("gameId") String gameId, @Param("teamId") String teamId);

    /**
     * 집계 행을 항상 INSERT 로만 생성한다. 복합 키가 할당된 엔티티에 save() 를 쓰면 merge(SELECT 후 UPDATE)로
     * 동작해 동시에 커밋된 행의 vote_count 를 0 으로 덮어쓸 수 있으므로, 충돌 시 반드시 예외가 나도록 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO cheer_battle_votes (game_id, team_id, vote_count) VALUES (:gameId, :teamId, 0)",
            nativeQuery = true)
    int insertZeroVoteCount(@Param("gameId") String gameId, @Param("teamId") String teamId);

    @Query("SELECT v.voteCount FROM CheerVoteEntity v WHERE v.gameId = :gameId AND v.teamId = :teamId")
    Integer findVoteCount(@Param("gameId") String gameId, @Param("teamId") String teamId);
}
