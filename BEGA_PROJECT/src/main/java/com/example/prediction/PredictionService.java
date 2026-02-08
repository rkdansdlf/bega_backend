package com.example.prediction;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameInningScoreEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.entity.GameSummaryEntity;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.GameInningScoreRepository;
import com.example.kbo.repository.GameMetadataRepository;
import com.example.kbo.repository.GameSummaryRepository;
import com.example.kbo.util.KboTeamCodePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final GameRepository gameRepository;
    private final GameMetadataRepository gameMetadataRepository;
    private final GameInningScoreRepository gameInningScoreRepository;
    private final GameSummaryRepository gameSummaryRepository;
    private final VoteFinalResultRepository voteFinalResultRepository;
    private final com.example.auth.repository.UserRepository userRepository;
    private static final Set<String> BLOCKED_VOTE_STATUSES = Set.of(
            "COMPLETED",
            "CANCELLED",
            "POSTPONED",
            "SUSPENDED",
            "DELAYED",
            "LIVE",
            "IN_PROGRESS",
            "INPROGRESS"
    );

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
                .filter(this::isCanonicalGame)
                .map(MatchDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 특정 날짜의 경기 조회 (실제 DB 데이터만 조회, 더미 및 Mock 제외)
    @Transactional(readOnly = true)
    public List<MatchDto> getMatchesByDate(LocalDate date) {
        List<GameEntity> matches = gameRepository.findByGameDate(date).stream()
                .filter(game -> !Boolean.TRUE.equals(game.getIsDummy()))
                .filter(game -> !game.getGameId().startsWith("MOCK"))
                .filter(this::isCanonicalGame)
                .collect(Collectors.toList());

        return matches.stream()
                .map(MatchDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MatchDto> getMatchesByDateRange(LocalDate startDate, LocalDate endDate) {
        List<GameEntity> matches = gameRepository.findAllByDateRange(startDate, endDate).stream()
                .filter(game -> !Boolean.TRUE.equals(game.getIsDummy()))
                .filter(game -> !game.getGameId().startsWith("MOCK"))
                .filter(this::isCanonicalGame)
                .collect(Collectors.toList());

        return matches.stream()
                .map(MatchDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GameDetailDto getGameDetail(String gameId) {
        GameEntity game = gameRepository.findByGameId(gameId)
                .orElseThrow(() -> new IllegalArgumentException("경기 정보를 찾을 수 없습니다."));

        GameMetadataEntity metadata = gameMetadataRepository.findByGameId(gameId)
                .orElse(null);

        List<GameInningScoreEntity> inningScores = gameInningScoreRepository
                .findAllByGameIdOrderByInningAscTeamSideAsc(gameId);

        List<GameSummaryEntity> summaries = gameSummaryRepository
                .findAllByGameIdOrderBySummaryTypeAscIdAsc(gameId);

        return GameDetailDto.from(game, metadata, inningScores, summaries);
    }

    @Transactional
    public void vote(Long userId, PredictionRequestDto request) {
        GameEntity game = gameRepository.findByGameId(request.getGameId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기입니다."));

        if (!isCanonicalGame(game)) {
            throw new IllegalArgumentException("예측 대상이 아닌 경기입니다.");
        }

        validateVoteOpen(game);

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
            // 포인트 차감 (Entity Update)
            com.example.auth.entity.UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

            // 포인트 잔액 검증
            if (user.getCheerPoints() == null || user.getCheerPoints() < 1) {
                throw new IllegalArgumentException(
                        "응원 포인트가 부족합니다. (현재: " + (user.getCheerPoints() == null ? 0 : user.getCheerPoints()) + ")");
            }

            user.deductCheerPoints(1);
            userRepository.save(user);

            Prediction prediction = Prediction.builder()
                    .gameId(request.getGameId())
                    .userId(userId)
                    .votedTeam(request.getVotedTeam())
                    .build();
            predictionRepository.save(prediction);
        }
    }

    @Transactional(readOnly = true)
    public PredictionResponseDto getVoteStatus(String gameId) {
        Optional<VoteFinalResult> finalResult = voteFinalResultRepository.findById(gameId);

        if (finalResult.isPresent()) {
            VoteFinalResult result = finalResult.get();
            int finalVotesA = result.getFinalVotesA();
            int finalVotesB = result.getFinalVotesB();
            int totalVotes = finalVotesA + finalVotesB;

            // 저장된 투표수 기반으로 퍼센트 실시간 계산
            int homePercentage = totalVotes > 0 ? (int) Math.round((finalVotesA * 100.0) / totalVotes) : 0;
            int awayPercentage = totalVotes > 0 ? (int) Math.round((finalVotesB * 100.0) / totalVotes) : 0;

            return PredictionResponseDto.builder()
                    .gameId(gameId)
                    .homeVotes((long) finalVotesA)
                    .awayVotes((long) finalVotesB)
                    .totalVotes((long) totalVotes)
                    .homePercentage(homePercentage)
                    .awayPercentage(awayPercentage)
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

    private boolean isCanonicalGame(GameEntity game) {
        if (game == null) {
            return false;
        }
        return KboTeamCodePolicy.isCanonicalTeamCode(game.getHomeTeam())
                && KboTeamCodePolicy.isCanonicalTeamCode(game.getAwayTeam());
    }

    @Transactional
    public void cancelVote(Long userId, String gameId) {
        GameEntity game = gameRepository.findByGameId(gameId)
                .orElseThrow(() -> new IllegalArgumentException("경기 정보를 찾을 수 없습니다."));

        validateVoteOpen(game);

        Prediction prediction = predictionRepository
                .findByGameIdAndUserId(gameId, userId)
                .orElseThrow(() -> new IllegalStateException("투표 내역이 없습니다."));

        // 포인트 반환 없음 (No Refund Policy)

        predictionRepository.delete(prediction);
    }

    @Transactional
    public void saveFinalVoteResult(String gameId) {
        GameEntity game = gameRepository.findByGameId(gameId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기입니다."));

        if (!game.isFinished()) {
            throw new IllegalStateException("종료되지 않은 경기는 최종 결과를 저장할 수 없습니다.");
        }

        PredictionResponseDto currentStatus = getVoteStatus(gameId);

        // Raw Vote Counts 저장 (Long -> int 변환 필요)
        int homeVotes = currentStatus.getHomeVotes().intValue();
        int awayVotes = currentStatus.getAwayVotes().intValue();

        // 승자 판별은 퍼센트 기준이 아닌 득표수 기준으로 해도 됨 (동일 결과)
        String finalWinner = "DRAW";
        if (homeVotes > awayVotes) {
            finalWinner = "HOME";
        } else if (awayVotes > homeVotes) {
            finalWinner = "AWAY";
        }

        VoteFinalResult finalResult = VoteFinalResult.builder()
                .gameId(gameId)
                .finalVotesA(homeVotes)
                .finalVotesB(awayVotes)
                .finalWinner(finalWinner)
                .build();

        voteFinalResultRepository.save(finalResult);
    }

    private void validateVoteOpen(GameEntity game) {
        // 1. 경기 상태 체크 (차단 상태면 투표 불가)
        String status = game.getGameStatus();
        if (status != null) {
            String normalizedStatus = status.trim().toUpperCase();
            if (BLOCKED_VOTE_STATUSES.contains(normalizedStatus)) {
                throw new IllegalArgumentException("이미 진행 중이거나 종료된 경기(상태: " + status + ")는 투표할 수 없습니다.");
            }
        }

        // 2. 시간 기반 체크 (경기 시작 시간 이후 투표 불가)
        Optional<GameMetadataEntity> metadataOpt = gameMetadataRepository.findByGameId(game.getGameId());
        LocalDate gameDate = game.getGameDate();

        if (gameDate == null) {
            // 날짜 정보가 없으면 보수적으로 차단 (데이터 이상)
            throw new IllegalArgumentException("경기 날짜 정보가 없어 투표를 진행할 수 없습니다.");
        }

        if (metadataOpt.isPresent()) {
            LocalTime startTime = metadataOpt.get().getStartTime();
            if (startTime != null) {
                LocalDateTime startDateTime = LocalDateTime.of(gameDate, startTime);
                if (!LocalDateTime.now().isBefore(startDateTime)) {
                    throw new IllegalArgumentException("이미 시작된 경기는 투표할 수 없습니다.");
                }
            }
        } else {
            // 메타데이터(시작 시간)가 없는 경우 시간 검증을 생략하고,
            // 상태값 기반 판단만 적용한다.
        }
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
                if (!isCanonicalGame(game)) {
                    continue;
                }
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
