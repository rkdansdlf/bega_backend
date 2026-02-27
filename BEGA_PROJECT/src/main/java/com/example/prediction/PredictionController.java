package com.example.prediction;

import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Objects;
import jakarta.validation.Valid;

@Tag(name = "경기 예측", description = "경기 결과 예측 투표, 내 투표 조회, 시즌 예측 순위")
@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class PredictionController {

    private final PredictionService predictionService;
    private final PredictionRepository predictionRepository;
    private static final Pattern PREDICTION_GAME_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final int MAX_MY_VOTE_BATCH_SIZE = 250;

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
    public ResponseEntity<?> getMatchesByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "true") boolean includePast,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "false") boolean withMeta) {
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.max(1, Math.min(500, size));

        if (withMeta) {
            MatchRangePageResponseDto response = predictionService.getMatchesByDateRangeWithMetadata(
                    startDate,
                    endDate,
                    includePast,
                    normalizedPage,
                    normalizedSize
            );
            return ResponseEntity.ok(response);
        }

        List<MatchDto> matches = predictionService.getMatchesByDateRange(startDate, endDate, includePast, normalizedPage, normalizedSize);
        return ResponseEntity.ok(matches);
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/matches/bounds")
    public ResponseEntity<MatchBoundsResponseDto> getMatchBounds() {
        return ResponseEntity.ok(predictionService.getMatchBounds());
    }

    // 특정 경기 상세 조회
    @PreAuthorize("permitAll()")
    @GetMapping("/matches/{gameId}")
    public ResponseEntity<?> getMatchDetail(@PathVariable String gameId) {
        String normalizedGameId = normalizePathGameId(gameId);
        if (normalizedGameId == null) {
            return ResponseEntity.badRequest().body("게임 ID가 잘못되었습니다.");
        }

        GameDetailDto detail = predictionService.getGameDetail(normalizedGameId);
        return ResponseEntity.ok(detail);
    }

    // 투표하기
    @PostMapping("/predictions/vote")
    public ResponseEntity<?> vote(@Valid @RequestBody PredictionRequestDto request, Principal principal) {
        if (principal == null) {
            // 사용자 정보가 없으면, 401 Unauthorized 또는 적절한 오류 응답을 반환
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        PredictionRequestDto normalizedRequest = normalizePredictionRequest(request);
        if (normalizedRequest == null) {
            return ResponseEntity.badRequest().body("투표 입력 값이 올바르지 않습니다.");
        }

        Long userId;
        try {
            userId = parsePrincipalUserId(principal);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        predictionService.vote(userId, normalizedRequest);
        return ResponseEntity.ok("투표 성공");
    }

    // 투표 현황 조회
    @PreAuthorize("permitAll()")
    @GetMapping("/predictions/status/{gameId}")
    public ResponseEntity<?> getVoteStatus(@PathVariable String gameId) {
        String normalizedGameId = normalizePathGameId(gameId);
        if (normalizedGameId == null) {
            return ResponseEntity.badRequest().body("게임 ID가 잘못되었습니다.");
        }

        PredictionResponseDto response = predictionService.getVoteStatus(normalizedGameId);
        return ResponseEntity.ok(response);
    }

    // 투표 취소
    @DeleteMapping("/predictions/{gameId}")
    public ResponseEntity<String> cancelVote(
            Principal principal,
            @PathVariable String gameId) {
        try {
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
            }

            Long userId = parsePrincipalUserId(principal);
            String normalizedGameId = normalizePathGameId(gameId);
            if (normalizedGameId == null) {
                return ResponseEntity.badRequest().body("게임 ID가 잘못되었습니다.");
            }

            predictionService.cancelVote(userId, normalizedGameId);
            return ResponseEntity.ok("투표가 취소되었습니다.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 다수 경기 사용자 투표 일괄 조회
    @PostMapping("/predictions/my-votes")
    public ResponseEntity<Map<String, Object>> getMyVotesBulk(
            @Valid @RequestBody PredictionMyVotesRequestDto request,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("votes", Map.of()));
        }

        Long userId;
        try {
            userId = parsePrincipalUserId(principal);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "votes", Map.of(),
                    "message", e.getMessage()
            ));
        }
        List<String> gameIds = request == null ? null : request.getGameIds();

        if (gameIds == null || gameIds.isEmpty()) {
            return ResponseEntity.ok(Map.of("votes", Map.of()));
        }

        Map<String, String> votes = new HashMap<>();
        List<String> distinctGameIds = normalizeGameIds(gameIds);

        if (distinctGameIds.size() > MAX_MY_VOTE_BATCH_SIZE) {
            return ResponseEntity.badRequest().body(Map.of(
                    "votes", Map.of(),
                    "message", "요청한 경기 수가 너무 많습니다."
            ));
        }

        if (distinctGameIds.isEmpty()) {
            return ResponseEntity.ok(Map.of("votes", Map.of()));
        }

        votes = buildUserVotesForGames(userId, distinctGameIds);

        return ResponseEntity.ok(Map.of("votes", votes));
    }

    private Map<String, String> buildUserVotesForGames(Long userId, List<String> gameIds) {
        Map<String, String> votes = new HashMap<>();
        if (gameIds == null || gameIds.isEmpty()) {
            return votes;
        }

        List<String> distinctGameIds = normalizeGameIds(gameIds);
        if (distinctGameIds.isEmpty() || distinctGameIds.size() > MAX_MY_VOTE_BATCH_SIZE) {
            return votes;
        }

        for (String resolvedGameId : distinctGameIds) {
            votes.put(resolvedGameId, null);
        }

        List<Prediction> predictions = predictionRepository.findByUserIdAndGameIdIn(
                userId,
                distinctGameIds
        );
        for (Prediction prediction : predictions) {
            votes.put(prediction.getGameId(), prediction.getVotedTeam());
        }

        return votes;
    }

    private String normalizePathGameId(String gameId) {
        if (gameId == null) {
            return null;
        }
        String normalized = gameId.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!PREDICTION_GAME_ID_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    private PredictionRequestDto normalizePredictionRequest(PredictionRequestDto request) {
        if (request == null) {
            return null;
        }

        String gameId = normalizePathGameId(request.getGameId());
        String votedTeam = normalizeVotedTeam(request.getVotedTeam());
        if (gameId == null || votedTeam == null) {
            return null;
        }

        request.setGameId(gameId);
        request.setVotedTeam(votedTeam);
        return request;
    }

    private String normalizeVotedTeam(String votedTeam) {
        if (votedTeam == null) {
            return null;
        }
        String normalized = votedTeam.trim().toLowerCase(Locale.ROOT);
        if (!"home".equals(normalized) && !"away".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private List<String> normalizeGameIds(List<String> gameIds) {
        return gameIds.stream()
                .map(this::normalizePathGameId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
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
        Long userId;
        try {
            userId = parsePrincipalUserId(principal);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
        UserPredictionStatsDto stats = predictionService.getUserStats(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", stats);
        return ResponseEntity.ok(response);
    }

    private Long parsePrincipalUserId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new IllegalArgumentException("로그인 사용자 정보를 확인할 수 없습니다.");
        }

        String normalizedUserId = principal.getName().trim();
        if (normalizedUserId.isEmpty()) {
            throw new IllegalArgumentException("로그인 사용자 정보를 확인할 수 없습니다.");
        }

        try {
            return Long.valueOf(normalizedUserId);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("로그인 사용자 ID 형식이 올바르지 않습니다.");
        }
    }

}
