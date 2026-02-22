package com.example.cheerboard.repository;

import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.entity.CheerVoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CheerVoteRepository extends JpaRepository<CheerVoteEntity, CheerVoteId> {
    List<CheerVoteEntity> findByGameId(String gameId);
}
