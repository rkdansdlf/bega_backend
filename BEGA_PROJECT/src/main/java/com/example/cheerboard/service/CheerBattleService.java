package com.example.cheerboard.service;

import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.entity.CheerVoteId;
import com.example.cheerboard.repository.CheerVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class CheerBattleService {

    private final CheerVoteRepository cheerVoteRepository;

    private final com.example.auth.repository.UserRepository userRepository;

    // Game ID -> Team ID -> Vote Count (In-memory cache)
    private final Map<String, Map<String, AtomicInteger>> gameVotes = new ConcurrentHashMap<>();

    @Transactional
    public int vote(String gameId, String teamId, String userEmail) {
        // 1. 사용자 포인트 차감
        com.example.auth.entity.UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 포인트 차감 (부족하면 예외 발생)
        user.deductCheerPoints(1);
        userRepository.save(user);

        // 2. Update memory (DB backup fallback)
        Map<String, AtomicInteger> teamVotes = gameVotes.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>());

        AtomicInteger counter = teamVotes.computeIfAbsent(teamId, k -> {
            // Memory miss -> Load from DB
            CheerVoteId id = CheerVoteId.builder()
                    .gameId(gameId)
                    .teamId(teamId)
                    .build();
            return cheerVoteRepository.findById(Objects.requireNonNull(id))
                    .map(entity -> new AtomicInteger(entity.getVoteCount()))
                    .orElse(new AtomicInteger(0));
        });

        int newValue = counter.incrementAndGet();

        // 3. Update DB
        CheerVoteId id = CheerVoteId.builder()
                .gameId(gameId)
                .teamId(teamId)
                .build();

        CheerVoteEntity entity = cheerVoteRepository.findById(Objects.requireNonNull(id))
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

    public void clearMemoryCache() {
        // 메모리 상의 투표 집계 초기화
        gameVotes.clear();
    }
}
