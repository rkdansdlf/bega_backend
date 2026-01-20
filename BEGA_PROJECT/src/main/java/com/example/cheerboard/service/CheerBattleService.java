package com.example.cheerboard.service;

import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.entity.CheerVoteId;
import com.example.cheerboard.repository.CheerVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class CheerBattleService {

    private final CheerVoteRepository cheerVoteRepository;

    // Game ID -> Team ID -> Vote Count (In-memory cache)
    private final Map<String, Map<String, AtomicInteger>> gameVotes = new ConcurrentHashMap<>();

    @Transactional
    public int vote(String gameId, String teamId) {
        // Update memory
        Map<String, AtomicInteger> teamVotes = gameVotes.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>());
        int newValue = teamVotes.computeIfAbsent(teamId, k -> new AtomicInteger(0)).incrementAndGet();

        // Update DB
        CheerVoteId id = CheerVoteId.builder()
                .gameId(gameId)
                .teamId(teamId)
                .build();

        CheerVoteEntity entity = cheerVoteRepository.findById(id)
                .orElse(CheerVoteEntity.builder()
                        .gameId(gameId)
                        .teamId(teamId)
                        .voteCount(0)
                        .build());

        entity.setVoteCount(newValue);
        cheerVoteRepository.save(entity);

        return newValue;
    }

    public Map<String, Integer> getGameStats(String gameId) {
        Map<String, Integer> result = new java.util.HashMap<>();

        // Try load from memory first
        if (gameVotes.containsKey(gameId)) {
            gameVotes.get(gameId).forEach((k, v) -> result.put(k, v.get()));
        } else {
            // If missing in memory, load from DB
            List<CheerVoteEntity> entities = cheerVoteRepository.findByGameId(gameId);
            entities.forEach(e -> {
                result.put(e.getTeamId(), e.getVoteCount());
                // Populate memory
                gameVotes.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
                        .put(e.getTeamId(), new AtomicInteger(e.getVoteCount()));
            });
        }

        return result;
    }
}
