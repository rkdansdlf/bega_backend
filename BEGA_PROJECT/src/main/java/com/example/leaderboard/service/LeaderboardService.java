package com.example.leaderboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.leaderboard.dto.*;
import com.example.leaderboard.entity.ScoreEvent;
import com.example.leaderboard.entity.UserScore;
import com.example.leaderboard.repository.ScoreEventRepository;
import com.example.leaderboard.repository.UserScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 리더보드 서비스
 * 리더보드 조회 및 랭킹 관련 기능을 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LeaderboardService {

    private final UserScoreRepository userScoreRepository;
    private final ScoreEventRepository scoreEventRepository;
    private final UserRepository userRepository;

    /**
     * 리더보드 조회
     * @param type 리더보드 타입 (total, season, monthly, weekly)
     * @param page 페이지 번호
     * @param size 페이지 크기
     */
    public Page<LeaderboardEntryDto> getLeaderboard(String type, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Page<UserScore> scorePage = switch (type.toLowerCase()) {
            case "season" -> userScoreRepository.findAllBySeasonScoreDesc(pageable);
            case "monthly" -> userScoreRepository.findAllByMonthlyScoreDesc(pageable);
            case "weekly" -> userScoreRepository.findAllByWeeklyScoreDesc(pageable);
            default -> userScoreRepository.findAllByTotalScoreDesc(pageable);
        };

        // 사용자 정보 일괄 조회
        List<Long> userIds = scorePage.getContent().stream()
                .map(UserScore::getUserId)
                .toList();
        Map<Long, UserEntity> userMap = getUserMap(userIds);

        // 랭크 계산 (페이지 오프셋 기반)
        long startRank = (long) page * size + 1;

        return scorePage.map(userScore -> {
            UserEntity user = userMap.get(userScore.getUserId());
            String nickname = user != null ? user.getName() : "Unknown";
            String profileUrl = user != null ? user.getProfileImageUrl() : null;

            long rank = startRank + scorePage.getContent().indexOf(userScore);

            Long score = switch (type.toLowerCase()) {
                case "season" -> userScore.getSeasonScore();
                case "monthly" -> userScore.getMonthlyScore();
                case "weekly" -> userScore.getWeeklyScore();
                default -> userScore.getTotalScore();
            };

            return LeaderboardEntryDto.fromWithScore(userScore, rank, score, nickname, profileUrl);
        });
    }

    /**
     * 현재 사용자 통계 조회
     */
    public UserStatsDto getUserStats(Long userId) {
        UserScore userScore = userScoreRepository.findByUserId(userId)
                .orElseGet(() -> UserScore.createForUser(userId));

        UserEntity user = userRepository.findById(userId).orElse(null);
        String nickname = user != null ? user.getName() : "Unknown";
        String profileUrl = user != null ? user.getProfileImageUrl() : null;

        UserStatsDto stats = UserStatsDto.from(userScore, nickname, profileUrl);

        // 랭킹 정보 추가
        stats.setTotalRank(userScoreRepository.findTotalRankByScore(userScore.getTotalScore()));
        stats.setSeasonRank(userScoreRepository.findSeasonRankByScore(userScore.getSeasonScore()));
        stats.setMonthlyRank(userScoreRepository.findMonthlyRankByScore(userScore.getMonthlyScore()));
        stats.setWeeklyRank(userScoreRepository.findWeeklyRankByScore(userScore.getWeeklyScore()));
        stats.setRank(stats.getSeasonRank());

        return stats;
    }

    /**
     * 특정 사용자 랭킹 조회 (시즌 기준)
     */
    public UserRankDto getUserRank(Long userId) {
        UserScore userScore = userScoreRepository.findByUserId(userId)
                .orElseGet(() -> UserScore.createForUser(userId));

        Long rank = userScoreRepository.findSeasonRankByScore(userScore.getSeasonScore());

        return UserRankDto.builder()
                .rank(rank != null ? rank : 0L)
                .score(userScore.getSeasonScore())
                .level(userScore.getUserLevel())
                .build();
    }

    /**
     * 현재 사용자 또는 새 사용자의 점수 데이터 조회/생성
     */
    @Transactional
    public UserScore getOrCreateUserScore(Long userId) {
        return userScoreRepository.findByUserId(userId)
                .orElseGet(() -> userScoreRepository.save(UserScore.createForUser(userId)));
    }

    /**
     * 핫 스트릭 목록 조회 (연승 중인 유저들)
     */
    public List<HotStreakDto> getHotStreaks(int minStreak, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<UserScore> hotStreakers = userScoreRepository.findHotStreaks(minStreak, pageable);

        List<Long> userIds = hotStreakers.stream().map(UserScore::getUserId).toList();
        Map<Long, UserEntity> userMap = getUserMap(userIds);

        return hotStreakers.stream()
                .map(userScore -> {
                    UserEntity user = userMap.get(userScore.getUserId());
                    String nickname = user != null ? user.getName() : "Unknown";
                    String profileUrl = user != null ? user.getProfileImageUrl() : null;
                    return HotStreakDto.from(userScore, nickname, profileUrl);
                })
                .toList();
    }

    /**
     * 최근 점수 획득 이벤트 조회 (글로벌 피드)
     */
    public List<RecentScoreDto> getRecentScores(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<ScoreEvent> recentEvents = scoreEventRepository.findRecentScores(pageable);

        List<Long> userIds = recentEvents.stream().map(ScoreEvent::getUserId).toList();
        Map<Long, UserEntity> userMap = getUserMap(userIds);

        return recentEvents.stream()
                .map(event -> {
                    UserEntity user = userMap.get(event.getUserId());
                    String nickname = user != null ? user.getName() : "Unknown";
                    String profileUrl = user != null ? user.getProfileImageUrl() : null;
                    return RecentScoreDto.from(event, nickname, profileUrl);
                })
                .toList();
    }

    /**
     * 사용자의 점수 히스토리 조회
     */
    public Page<RecentScoreDto> getUserScoreHistory(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ScoreEvent> events = scoreEventRepository.findByUserId(userId, pageable);

        Optional<UserEntity> user = userRepository.findById(userId);
        String nickname = user.map(UserEntity::getName).orElse("Unknown");
        String profileUrl = user.map(UserEntity::getProfileImageUrl).orElse(null);

        return events.map(event -> RecentScoreDto.from(event, nickname, profileUrl));
    }

    /**
     * 총 사용자 수 조회
     */
    public Long getTotalUserCount() {
        return userScoreRepository.countTotalUsers();
    }

    /**
     * 평균 점수 조회
     */
    public Double getAverageScore() {
        return userScoreRepository.findAverageScore();
    }

    // ============================================
    // SEED TEST DATA (Development/Testing only)
    // ============================================

    /**
     * 테스트용 더미 리더보드 데이터 생성
     * 기존 점수가 없는 사용자에게만 랜덤 점수 데이터를 생성합니다.
     * @return 생성된 UserScore 개수
     */
    @Transactional
    public int seedTestData() {
        List<UserEntity> users = userRepository.findAll();
        Random random = new Random();
        int seededCount = 0;

        for (UserEntity user : users) {
            if (userScoreRepository.findByUserId(user.getId()).isEmpty()) {
                UserScore score = UserScore.builder()
                        .userId(user.getId())
                        .totalScore((long) (random.nextInt(95000) + 5000))     // 5,000 ~ 100,000
                        .seasonScore((long) (random.nextInt(57000) + 3000))    // 3,000 ~ 60,000
                        .monthlyScore((long) (random.nextInt(19000) + 1000))   // 1,000 ~ 20,000
                        .weeklyScore((long) (random.nextInt(4500) + 500))      // 500 ~ 5,000
                        .currentStreak(random.nextInt(12))                      // 0 ~ 11
                        .maxStreak(random.nextInt(15) + 5)                      // 5 ~ 19
                        .userLevel(random.nextInt(49) + 1)                      // 1 ~ 49
                        .experiencePoints((long) (random.nextInt(24900) + 100)) // 100 ~ 25,000
                        .correctPredictions(random.nextInt(190) + 10)           // 10 ~ 199
                        .totalPredictions(random.nextInt(280) + 20)             // 20 ~ 299
                        .build();
                userScoreRepository.save(score);
                seededCount++;
                log.info("Seeded test data for userId: {}", user.getId());
            }
        }

        log.info("Seeded {} user scores for testing", seededCount);
        return seededCount;
    }

    // ============================================
    // PRIVATE HELPER METHODS
    // ============================================

    private Map<Long, UserEntity> getUserMap(List<Long> userIds) {
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }
}
