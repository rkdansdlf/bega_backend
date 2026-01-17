package com.example.prediction;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final GameRepository gameRepository;
    private final VoteFinalResultRepository voteFinalResultRepository;

    @Transactional(readOnly = true)
    public List<MatchDto> getRecentCompletedGames() {
        LocalDate today = LocalDate.now();

        List<LocalDate> allDates = gameRepository.findRecentDistinctGameDates(today);
        List<LocalDate> recentDates = allDates.stream()
                .limit(7)
                .collect(Collectors.toList());

        if (recentDates.isEmpty()) {
            return List.of();
        }

        List<GameEntity> matches = gameRepository.findAllByGameDatesIn(recentDates);

        return matches.stream()
                .map(MatchDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 오늘 이후 날짜 조회 시 더미 데이터 포함
    @Transactional(readOnly = true)
    public List<MatchDto> getMatchesByDate(LocalDate date) {
        List<GameEntity> matches = new ArrayList<>();

        // 1. 해당 날짜의 실제 경기
        matches.addAll(gameRepository.findByGameDate(date));

        // 2. 오늘 이후 날짜 조회 시 더미 데이터 포함 (미래 경기)
        LocalDate today = LocalDate.now();
        if (!date.isBefore(today)) { // date >= today (오늘 포함 이후)
            List<GameEntity> dummyMatches = gameRepository.findByIsDummy(true);
            matches.addAll(dummyMatches);
        }

        return matches.stream()
                .map(MatchDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MatchDto> getMatchesByDateRange(LocalDate startDate, LocalDate endDate) {
        List<GameEntity> matches = gameRepository.findCompletedByDateRange(startDate, endDate);

        return matches.stream()
                .map(MatchDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    @SuppressWarnings("null")
    public void vote(Long userId, PredictionRequestDto request) {
        GameEntity game = gameRepository.findByGameId(request.getGameId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기입니다."));

        if (game.isFinished()) {
            throw new IllegalStateException("이미 종료된 경기는 투표할 수 없습니다.");
        }

        Optional<Prediction> existing = predictionRepository
                .findByGameIdAndUserId(request.getGameId(), userId);

        if (existing.isPresent()) {
            Prediction prediction = existing.get();

            if (prediction.getVotedTeam().equals(request.getVotedTeam())) {
                predictionRepository.delete(prediction);
                return;
            }

            prediction.updateVotedTeam(request.getVotedTeam());

        } else {
            Prediction prediction = Prediction.builder()
                    .gameId(request.getGameId())
                    .userId(userId)
                    .votedTeam(request.getVotedTeam())
                    .build();
            predictionRepository.save(prediction);
        }
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("null")
    public PredictionResponseDto getVoteStatus(String gameId) {
        Optional<VoteFinalResult> finalResult = voteFinalResultRepository.findById(gameId);

        if (finalResult.isPresent()) {
            VoteFinalResult result = finalResult.get();
            int totalVotes = result.getFinalVotesA() + result.getFinalVotesB();

            return PredictionResponseDto.builder()
                    .gameId(gameId)
                    .homeVotes((long) result.getFinalVotesA())
                    .awayVotes((long) result.getFinalVotesB())
                    .totalVotes((long) totalVotes)
                    .homePercentage(result.getFinalVotesA())
                    .awayPercentage(result.getFinalVotesB())
                    .build();
        }

        Long homeVotes = predictionRepository.countByGameIdAndVotedTeam(gameId, "home");
        Long awayVotes = predictionRepository.countByGameIdAndVotedTeam(gameId, "away");
        Long totalVotes = homeVotes + awayVotes;

        int homePercentage = totalVotes > 0 ? (int) Math.round((homeVotes * 100.0) / totalVotes) : 0;
        int awayPercentage = totalVotes > 0 ? (int) Math.round((awayVotes * 100.0) / totalVotes) : 0;

        return PredictionResponseDto.builder()
                .gameId(gameId)
                .homeVotes(homeVotes)
                .awayVotes(awayVotes)
                .totalVotes(totalVotes)
                .homePercentage(homePercentage)
                .awayPercentage(awayPercentage)
                .build();
    }

    @Transactional
    @SuppressWarnings("null")
    public void cancelVote(Long userId, String gameId) {
        GameEntity game = gameRepository.findByGameId(gameId)
                .orElseThrow(() -> new IllegalArgumentException("경기 정보를 찾을 수 없습니다."));

        if (game.isFinished()) {
            throw new IllegalStateException("이미 종료된 경기는 투표를 취소할 수 없습니다.");
        }

        Prediction prediction = predictionRepository
                .findByGameIdAndUserId(gameId, userId)
                .orElseThrow(() -> new IllegalStateException("투표 내역이 없습니다."));

        predictionRepository.delete(prediction);
    }

    @Transactional
    @SuppressWarnings("null")
    public void saveFinalVoteResult(String gameId) {
        GameEntity game = gameRepository.findByGameId(gameId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기입니다."));

        if (!game.isFinished()) {
            throw new IllegalStateException("종료되지 않은 경기는 최종 결과를 저장할 수 없습니다.");
        }

        PredictionResponseDto currentStatus = getVoteStatus(gameId);

        int homePercentage = currentStatus.getHomePercentage();
        int awayPercentage = currentStatus.getAwayPercentage();

        String finalWinner = "DRAW";
        if (homePercentage > awayPercentage) {
            finalWinner = "HOME";
        } else if (awayPercentage > homePercentage) {
            finalWinner = "AWAY";
        }

        VoteFinalResult finalResult = VoteFinalResult.builder()
                .gameId(gameId)
                .finalVotesA(homePercentage)
                .finalVotesB(awayPercentage)
                .finalWinner(finalWinner)
                .build();

        voteFinalResultRepository.save(finalResult);
    }

    @Transactional(readOnly = true)
    public UserPredictionStatsDto getUserStats(Long userId) {
        List<Prediction> predictions = predictionRepository.findAllByUserIdOrderByCreatedAtDesc(userId);

        int totalFinished = 0;
        int correctCount = 0;
        int currentStreak = 0;
        boolean streakBroken = false;

        for (Prediction prediction : predictions) {
            Optional<GameEntity> gameOpt = gameRepository.findByGameId(prediction.getGameId());
            if (gameOpt.isPresent()) {
                GameEntity game = gameOpt.get();
                if (game.isFinished()) {
                    totalFinished++;
                    String actualWinner = game.getWinner(); // "home", "away", or "draw"
                    boolean isCorrect = prediction.getVotedTeam().equalsIgnoreCase(actualWinner);

                    if (isCorrect) {
                        correctCount++;
                        if (!streakBroken) {
                            currentStreak++;
                        }
                    } else {
                        // 결과가 나왔는데 틀린 경우 streak 종료
                        streakBroken = true;
                    }
                }
                // 경기가 아직 안 끝났으면 streak 계산에는 영향을 주지 않고 건너뜀 (최신순이므로)
            }
        }

        double accuracy = totalFinished > 0 ? Math.round((correctCount * 100.0 / totalFinished) * 10.0) / 10.0 : 0.0;

        return UserPredictionStatsDto.builder()
                .totalPredictions(totalFinished)
                .correctPredictions(correctCount)
                .accuracy(accuracy)
                .streak(currentStreak)
                .build();
    }
}