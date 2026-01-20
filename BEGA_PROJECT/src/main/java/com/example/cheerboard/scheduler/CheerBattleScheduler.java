package com.example.cheerboard.scheduler;

import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.entity.CheerVoteId;
import com.example.cheerboard.repository.CheerVoteRepository;
import com.example.cheerboard.service.CheerBattleService;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheerBattleScheduler {

    private final GameRepository gameRepository;
    private final CheerVoteRepository cheerVoteRepository;
    private final CheerBattleService cheerBattleService;

    /**
     * 매일 자정(00:00)에 당일 경기 투표 데이터 초기화
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void createDailyBattles() {
        LocalDate today = LocalDate.now();
        List<GameEntity> todaysGames = gameRepository.findByGameDate(today);

        if (todaysGames.isEmpty()) {
            log.info("No games found for today ({}), skipping Cheer Battle initialization.", today);
            return;
        }

        int initializedCount = 0;
        for (GameEntity game : todaysGames) {
            String gameId = game.getGameId();
            String homeTeamId = game.getHomeTeam();
            String awayTeamId = game.getAwayTeam();

            // 홈 팀 투표 데이터 생성 (없으면 생성)
            initializeVoteRecord(gameId, homeTeamId);
            // 원정 팀 투표 데이터 생성 (없으면 생성)
            initializeVoteRecord(gameId, awayTeamId);

            initializedCount++;
        }

        log.info("Initialized Cheer Battle votes for {} games on {}", initializedCount, today);
    }

    private void initializeVoteRecord(String gameId, String teamId) {
        CheerVoteId id = CheerVoteId.builder()
                .gameId(gameId)
                .teamId(teamId)
                .build();

        if (!cheerVoteRepository.existsById(id)) {
            cheerVoteRepository.save(CheerVoteEntity.builder()
                    .gameId(gameId)
                    .teamId(teamId)
                    .voteCount(0)
                    .build());
        }
    }

    /**
     * 매일 새벽 3시(03:00)에 인메모리 투표 캐시 초기화
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void resetDailyVotes() {
        cheerBattleService.clearMemoryCache();
        log.info("Cheer Battle in-memory cache cleared.");
    }
}
