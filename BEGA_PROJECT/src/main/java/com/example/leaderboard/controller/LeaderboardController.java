package com.example.leaderboard.controller;

import com.example.leaderboard.dto.*;
import com.example.leaderboard.service.AchievementService;
import com.example.leaderboard.service.LeaderboardService;
import com.example.leaderboard.service.PowerupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 리더보드 API 컨트롤러
 * 레트로 게이미피케이션 리더보드 시스템의 REST API 엔드포인트
 */
@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
@Slf4j
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final PowerupService powerupService;
    private final AchievementService achievementService;

    // ============================================
    // LEADERBOARD ENDPOINTS
    // ============================================

    /**
     * 리더보드 조회
     * @param type 리더보드 타입 (total, season, monthly, weekly)
     * @param page 페이지 번호
     * @param size 페이지 크기
     */
    @GetMapping
    @PreAuthorize("permitAll()")
    public ResponseEntity<Page<LeaderboardEntryDto>> getLeaderboard(
            @RequestParam(defaultValue = "season") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("Get leaderboard: type={}, page={}, size={}", type, page, size);
        Page<LeaderboardEntryDto> leaderboard = leaderboardService.getLeaderboard(type, page, size);
        return ResponseEntity.ok(leaderboard);
    }

    /**
     * 내 통계 조회
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserStatsDto> getMyStats(Principal principal) {
        Long userId = extractUserId(principal);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        log.debug("Get my stats: userId={}", userId);
        UserStatsDto stats = leaderboardService.getUserStats(userId);
        return ResponseEntity.ok(stats);
    }

    /**
     * 사용자 통계 조회
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<UserStatsDto> getUserStats(@PathVariable Long userId) {
        log.debug("Get user stats: userId={}", userId);
        UserStatsDto stats = leaderboardService.getUserStats(userId);
        return ResponseEntity.ok(stats);
    }

    /**
     * 사용자 랭킹 조회 (시즌 기준)
     */
    @GetMapping("/users/{userId}/rank")
    @PreAuthorize("permitAll()")
    public ResponseEntity<UserRankDto> getUserRank(@PathVariable Long userId) {
        log.debug("Get user rank: userId={}", userId);
        UserRankDto rank = leaderboardService.getUserRank(userId);
        return ResponseEntity.ok(rank);
    }

    // ============================================
    // HOT STREAKS & RECENT SCORES
    // ============================================

    /**
     * 핫 스트릭 조회 (연승 중인 유저 목록)
     */
    @GetMapping("/hot-streaks")
    @PreAuthorize("permitAll()")
    public ResponseEntity<List<HotStreakDto>> getHotStreaks(
            @RequestParam(defaultValue = "3") int minStreak,
            @RequestParam(defaultValue = "10") int limit
    ) {
        log.debug("Get hot streaks: minStreak={}, limit={}", minStreak, limit);
        List<HotStreakDto> hotStreaks = leaderboardService.getHotStreaks(minStreak, limit);
        return ResponseEntity.ok(hotStreaks);
    }

    /**
     * 최근 점수 획득 조회
     */
    @GetMapping("/recent-scores")
    @PreAuthorize("permitAll()")
    public ResponseEntity<List<RecentScoreDto>> getRecentScores(
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.debug("Get recent scores: limit={}", limit);
        List<RecentScoreDto> recentScores = leaderboardService.getRecentScores(limit);
        return ResponseEntity.ok(recentScores);
    }

    /**
     * 내 점수 히스토리 조회
     */
    @GetMapping("/me/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<RecentScoreDto>> getMyScoreHistory(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = extractUserId(principal);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        log.debug("Get my score history: userId={}, page={}, size={}", userId, page, size);
        Page<RecentScoreDto> history = leaderboardService.getUserScoreHistory(userId, page, size);
        return ResponseEntity.ok(history);
    }

    // ============================================
    // POWERUP ENDPOINTS
    // ============================================

    /**
     * 파워업 인벤토리 조회
     */
    @GetMapping("/powerups")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Integer>> getPowerups(Principal principal) {
        Long userId = extractUserId(principal);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        log.debug("Get powerups: userId={}", userId);
        List<PowerupInventoryDto> powerups = powerupService.getUserPowerups(userId);
        Map<String, Integer> inventory = powerups.stream()
                .collect(Collectors.toMap(PowerupInventoryDto::getType, PowerupInventoryDto::getQuantity));
        return ResponseEntity.ok(inventory);
    }

    /**
     * 활성 파워업 조회
     */
    @GetMapping("/powerups/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ActivePowerupDto>> getActivePowerups(Principal principal) {
        Long userId = extractUserId(principal);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        log.debug("Get active powerups: userId={}", userId);
        List<ActivePowerupDto> activePowerups = powerupService.getActivePowerups(userId);
        return ResponseEntity.ok(activePowerups);
    }

    /**
     * 파워업 사용
     */
    @PostMapping("/powerups/{type}/use")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PowerupUseResultDto> usePowerup(
            Principal principal,
            @PathVariable String type,
            @RequestParam(required = false) String gameId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Long userId = extractUserId(principal);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        String resolvedGameId = gameId;
        if (resolvedGameId == null && body != null) {
            Object value = body.get("gameId");
            if (value != null) {
                resolvedGameId = value.toString();
            }
        }

        log.info("Use powerup: userId={}, type={}, gameId={}", userId, type, resolvedGameId);
        Integer remainingCount = powerupService.usePowerup(userId, type, resolvedGameId);

        if (remainingCount != null) {
            return ResponseEntity.ok(PowerupUseResultDto.builder()
                    .success(true)
                    .message("파워업이 활성화되었습니다!")
                    .remainingCount(remainingCount)
                    .build());
        }

        return ResponseEntity.badRequest().body(PowerupUseResultDto.builder()
                .success(false)
                .message("파워업 사용에 실패했습니다. 인벤토리를 확인해주세요.")
                .remainingCount(0)
                .build());
    }

    // ============================================
    // ACHIEVEMENT ENDPOINTS
    // ============================================

    /**
     * 전체 업적 조회
     */
    @GetMapping("/achievements")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AchievementDto>> getAllAchievements(Principal principal) {
        Long userId = extractUserId(principal);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        log.debug("Get all achievements: userId={}", userId);
        List<AchievementDto> achievements = achievementService.getUserAchievements(userId);
        return ResponseEntity.ok(achievements);
    }

    /**
     * 최근 획득 업적 조회
     */
    @GetMapping("/achievements/recent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AchievementDto>> getRecentAchievements(
            Principal principal,
            @RequestParam(defaultValue = "5") int limit
    ) {
        Long userId = extractUserId(principal);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        log.debug("Get recent achievements: userId={}, limit={}", userId, limit);
        List<AchievementDto> achievements = achievementService.getRecentAchievements(userId, limit);
        return ResponseEntity.ok(achievements);
    }

    /**
     * 최근 희귀 업적 획득 피드 (EPIC, LEGENDARY)
     */
    @GetMapping("/achievements/rare")
    @PreAuthorize("permitAll()")
    public ResponseEntity<List<AchievementDto>> getRecentRareAchievements(
            @RequestParam(defaultValue = "10") int limit
    ) {
        log.debug("Get recent rare achievements: limit={}", limit);
        List<AchievementDto> achievements = achievementService.getRecentRareAchievements(limit);
        return ResponseEntity.ok(achievements);
    }

    // ============================================
    // STATISTICS ENDPOINTS
    // ============================================

    /**
     * 전체 통계 조회
     */
    @GetMapping("/stats")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> getGlobalStats() {
        Long totalUsers = leaderboardService.getTotalUserCount();
        Double avgScore = leaderboardService.getAverageScore();

        return ResponseEntity.ok(Map.of(
                "totalUsers", totalUsers != null ? totalUsers : 0,
                "averageScore", avgScore != null ? Math.round(avgScore) : 0
        ));
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    /**
     * Principal에서 사용자 ID 추출
     */
    private Long extractUserId(Principal principal) {
        if (principal == null) {
            return null;
        }

        try {
            // Principal의 name은 userId (Long)로 설정되어 있음
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse userId from principal: {}", principal.getName());
            return null;
        }
    }
}
