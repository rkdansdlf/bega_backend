package com.example.prediction;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class PredictionController {

    private final PredictionService predictionService;
    private final PredictionRepository predictionRepository;

    public PredictionController(PredictionService predictionService, PredictionRepository predictionRepository) {
        this.predictionService = predictionService;
        this.predictionRepository = predictionRepository;
    }

    // 과거 경기 조회 (오늘 기준 이전 일주일치 - 최신순)
    @PreAuthorize("permitAll()")
    @GetMapping("/games/past")
    public ResponseEntity<List<MatchDto>> getPastGames() {
        List<MatchDto> matches = predictionService.getRecentCompletedGames();
        return ResponseEntity.ok(matches);
    }

    // 특정 날짜의 경기 조회 (모든 경우에 사용)
    @PreAuthorize("permitAll()")
    @GetMapping("/matches")
    public ResponseEntity<List<MatchDto>> getMatches(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<MatchDto> matches = predictionService.getMatchesByDate(date);
        return ResponseEntity.ok(matches);
    }

    // 특정 기간의 경기 조회 (과거 일주일치 등)
    @PreAuthorize("permitAll()")
    @GetMapping("/matches/range")
    public ResponseEntity<List<MatchDto>> getMatchesByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<MatchDto> matches = predictionService.getMatchesByDateRange(startDate, endDate);
        return ResponseEntity.ok(matches);
    }

    // 특정 경기 상세 조회
    @PreAuthorize("permitAll()")
    @GetMapping("/matches/{gameId}")
    public ResponseEntity<GameDetailDto> getMatchDetail(@PathVariable String gameId) {
        GameDetailDto detail = predictionService.getGameDetail(gameId);
        return ResponseEntity.ok(detail);
    }

    // 투표하기
    @PostMapping("/predictions/vote")
    public ResponseEntity<?> vote(@RequestBody PredictionRequestDto request, Principal principal) {
        if (principal == null) {
            // 사용자 정보가 없으면, 401 Unauthorized 또는 적절한 오류 응답을 반환
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        String username = principal.getName();

        Long userId = Long.valueOf(username);

        predictionService.vote(userId, request);
        return ResponseEntity.ok("투표 성공");
    }

    // 투표 현황 조회
    @PreAuthorize("permitAll()")
    @GetMapping("/predictions/status/{gameId}")
    public ResponseEntity<PredictionResponseDto> getVoteStatus(@PathVariable String gameId) {
        PredictionResponseDto response = predictionService.getVoteStatus(gameId);
        return ResponseEntity.ok(response);
    }

    // 투표 취소
    @DeleteMapping("/predictions/{gameId}")
    public ResponseEntity<String> cancelVote(
            Principal principal,
            @PathVariable String gameId) {
        try {
            Long userId = Long.valueOf(principal.getName());
            predictionService.cancelVote(userId, gameId);
            return ResponseEntity.ok("투표가 취소되었습니다.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 사용자의 특정 경기 투표 조회
    @GetMapping("/predictions/my-vote/{gameId}")
    public ResponseEntity<?> getMyVote(
            Principal principal,
            @PathVariable String gameId) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        Long userId = Long.valueOf(principal.getName());

        Optional<Prediction> prediction = predictionRepository.findByGameIdAndUserId(gameId, userId);

        if (prediction.isPresent()) {
            return ResponseEntity.ok(Map.of("votedTeam", prediction.get().getVotedTeam()));
        } else {
            // NullPointerException을 피하기 위해 HashMap을 사용
            // Map.of()는 null 값을 허용하지 않음
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("votedTeam", null);
            return ResponseEntity.ok(responseMap);
        }
    }

    // 내 예측 통계 조회
    @GetMapping("/prediction/stats/me")
    public ResponseEntity<Map<String, Object>> getMyStats(Principal principal) {
        if (principal == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        Long userId = Long.valueOf(principal.getName());
        UserPredictionStatsDto stats = predictionService.getUserStats(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", stats);
        return ResponseEntity.ok(response);
    }

}
