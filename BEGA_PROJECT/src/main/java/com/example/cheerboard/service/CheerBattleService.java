package com.example.cheerboard.service;

import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.repository.CheerVoteRepository;
import com.example.kbo.util.TeamCodeNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class CheerBattleService {

    private final CheerVoteRepository cheerVoteRepository;
    private final com.example.cheerboard.repository.CheerBattleLogRepository cheerBattleLogRepository;
    private final com.example.auth.repository.UserRepository userRepository;
    private final CheerBattleVoteTransactionService voteTransactionService;

    // Game ID -> Team ID -> Vote Count (In-memory cache)
    private final Map<String, Map<String, AtomicInteger>> gameVotes = new ConcurrentHashMap<>();
    private final java.util.Set<String> loadedGameStats = ConcurrentHashMap.newKeySet();

    public int vote(String gameId, String teamId, Long userId) {
        String normalizedTeamId = TeamCodeNormalizer.normalize(teamId);
        int newValue;
        try {
            newValue = voteTransactionService.vote(gameId, normalizedTeamId, userId);
        } catch (CheerBattleVoteTransactionService.VoteCountInsertRaceException race) {
            newValue = voteTransactionService.vote(gameId, normalizedTeamId, userId);
        }
        updateMemoryAfterCommit(gameId, normalizedTeamId, newValue);
        return newValue;
    }

    private void updateMemoryAfterCommit(String gameId, String teamId, int voteCount) {
        Runnable updateCache = () -> gameVotes.computeIfAbsent(gameId, ignored -> new ConcurrentHashMap<>())
                .compute(teamId, (ignored, counter) -> {
                    if (counter == null) {
                        return new AtomicInteger(voteCount);
                    }
                    counter.accumulateAndGet(voteCount, Math::max);
                    return counter;
                });

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            updateCache.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                updateCache.run();
            }
        });
    }

    public Map<String, Integer> getGameStats(String gameId) {
        if (loadedGameStats.contains(gameId)) {
            return snapshotGameStats(gameId);
        }

        List<CheerVoteEntity> entities = cheerVoteRepository.findByGameId(gameId);
        entities.forEach(entity -> gameVotes
                .computeIfAbsent(gameId, ignored -> new ConcurrentHashMap<>())
                .compute(entity.getTeamId(), (ignored, current) -> {
                    if (current == null) {
                        return new AtomicInteger(entity.getVoteCount());
                    }
                    current.accumulateAndGet(entity.getVoteCount(), Math::max);
                    return current;
                }));
        loadedGameStats.add(gameId);
        return snapshotGameStats(gameId);
    }

    private Map<String, Integer> snapshotGameStats(String gameId) {
        Map<String, Integer> result = new java.util.HashMap<>();
        Map<String, AtomicInteger> cachedStats = gameVotes.get(gameId);
        if (cachedStats != null) {
            cachedStats.forEach((teamId, count) -> result.put(teamId, count.get()));
        }
        return result;
    }

    public String getUserVote(String gameId, Long userId) {
        return userRepository.findById(userId)
                .flatMap(user -> cheerBattleLogRepository.findByGameIdAndUserEmail(gameId, user.getEmail()))
                .map(com.example.cheerboard.entity.CheerBattleLog::getTeamId)
                .orElse(null);
    }

    public void clearMemoryCache() {
        // 메모리 상의 투표 집계 초기화
        gameVotes.clear();
        loadedGameStats.clear();
    }
}
