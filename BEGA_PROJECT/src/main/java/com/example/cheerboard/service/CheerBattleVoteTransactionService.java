package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.entity.CheerBattleLog;
import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.repository.CheerBattleLogRepository;
import com.example.cheerboard.repository.CheerVoteRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheerBattleVoteTransactionService {

    private final CheerVoteRepository cheerVoteRepository;
    private final CheerBattleLogRepository cheerBattleLogRepository;
    private final UserRepository userRepository;
    private final CheerMonitoringMetricsService metricsService;

    @Transactional
    public int vote(String gameId, String normalizedTeamId, Long userId) {
        UserEntity user = userRepository.findByIdForWrite(userId)
                .orElseThrow(() -> {
                    metricsService.recordBattleVote("user_not_found");
                    return new IllegalArgumentException("사용자를 찾을 수 없습니다.");
                });

        String userEmail = user.getEmail();
        if (cheerBattleLogRepository.existsByGameIdAndUserEmail(gameId, userEmail)) {
            metricsService.recordBattleVote("already_voted");
            throw new IllegalStateException("이미 투표에 참여하셨습니다.");
        }

        if (userRepository.deductOneCheerPointIfAvailable(userId) == 0) {
            metricsService.recordBattleVote("insufficient_points");
            throw new IllegalStateException("응원 포인트가 부족합니다.");
        }

        CheerBattleLog battleLog = CheerBattleLog.builder()
                .gameId(gameId)
                .teamId(normalizedTeamId)
                .userEmail(userEmail)
                .build();
        cheerBattleLogRepository.save(Objects.requireNonNull(battleLog));

        int updatedRows = cheerVoteRepository.incrementVoteCount(gameId, normalizedTeamId);
        if (updatedRows == 0) {
            try {
                cheerVoteRepository.saveAndFlush(CheerVoteEntity.builder()
                        .gameId(gameId)
                        .teamId(normalizedTeamId)
                        .voteCount(0)
                        .build());
            } catch (DataIntegrityViolationException race) {
                throw new VoteCountInsertRaceException(race);
            }

            updatedRows = cheerVoteRepository.incrementVoteCount(gameId, normalizedTeamId);
            if (updatedRows == 0) {
                throw new IllegalStateException("투표 집계 행을 갱신할 수 없습니다.");
            }
        }

        Integer dbCount = cheerVoteRepository.findVoteCount(gameId, normalizedTeamId);
        if (dbCount == null) {
            throw new IllegalStateException("투표 집계 행을 조회할 수 없습니다.");
        }
        metricsService.recordBattleVote("success");
        return dbCount;
    }

    public static final class VoteCountInsertRaceException extends RuntimeException {
        public VoteCountInsertRaceException(DataIntegrityViolationException cause) {
            super(cause);
        }
    }
}
