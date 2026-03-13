package com.example.prediction;

import com.example.common.dto.ApiResponse;
import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.UnauthorizedBusinessException;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
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

    @PreAuthorize("permitAll()")
    @GetMapping("/matches/day")
    public ResponseEntity<MatchDayNavigationResponseDto> getMatchDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(predictionService.getMatchDayNavigation(date));
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
        String normalizedGameId = requireNormalizedGameId(gameId);

        GameDetailDto detail = predictionService.getGameDetail(normalizedGameId);
        return ResponseEntity.ok(detail);
    }

    // 투표하기
    @PostMapping("/predictions/vote")
    public ResponseEntity<ApiResponse> vote(@Valid @RequestBody PredictionRequestDto request, Principal principal) {
        PredictionRequestDto normalizedRequest = requireNormalizedPredictionRequest(request);
        Long userId = parsePrincipalUserId(requirePrincipal(principal));

        predictionService.vote(userId, normalizedRequest);
        return ResponseEntity.ok(ApiResponse.success("투표 성공"));
    }

    // 투표 현황 조회
    @PreAuthorize("permitAll()")
    @GetMapping("/predictions/status/{gameId}")
    public ResponseEntity<?> getVoteStatus(@PathVariable String gameId) {
        String normalizedGameId = requireNormalizedGameId(gameId);

        PredictionResponseDto response = predictionService.getVoteStatus(normalizedGameId);
        return ResponseEntity.ok(response);
    }

    // 투표 취소
    @DeleteMapping("/predictions/{gameId}")
    public ResponseEntity<ApiResponse> cancelVote(
            Principal principal,
            @PathVariable String gameId) {
        Long userId = parsePrincipalUserId(requirePrincipal(principal));
        String normalizedGameId = requireNormalizedGameId(gameId);

        predictionService.cancelVote(userId, normalizedGameId);
        return ResponseEntity.ok(ApiResponse.success("투표가 취소되었습니다."));
    }

    // 다수 경기 사용자 투표 일괄 조회
    @PostMapping("/predictions/my-votes")
    public ResponseEntity<?> getMyVotesBulk(
            @Valid @RequestBody PredictionMyVotesRequestDto request,
            Principal principal) {
        Long userId = parsePrincipalUserIdForMyVotes(requirePrincipalForMyVotes(principal));
        List<String> gameIds = request == null ? null : request.getGameIds();

        if (gameIds == null || gameIds.isEmpty()) {
            return ResponseEntity.ok(emptyVotesPayload());
        }

        Map<String, String> votes = new HashMap<>();
        List<String> distinctGameIds = normalizeGameIds(gameIds);

        if (distinctGameIds.size() > MAX_MY_VOTE_BATCH_SIZE) {
            throw new BadRequestBusinessException(
                    "TOO_MANY_GAME_IDS",
                    "요청한 경기 수가 너무 많습니다.",
                    emptyVotesPayload());
        }

        if (distinctGameIds.isEmpty()) {
            return ResponseEntity.ok(emptyVotesPayload());
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
    public ResponseEntity<ApiResponse> getMyStats(Principal principal) {
        Long userId = parsePrincipalUserId(requirePrincipal(principal));
        UserPredictionStatsDto stats = predictionService.getUserStats(userId);
        return ResponseEntity.ok(ApiResponse.success("내 예측 통계를 조회했습니다.", stats));
    }

    private String requireNormalizedGameId(String gameId) {
        String normalizedGameId = normalizePathGameId(gameId);
        if (normalizedGameId == null) {
            throw new BadRequestBusinessException("INVALID_GAME_ID", "게임 ID가 잘못되었습니다.");
        }
        return normalizedGameId;
    }

    private PredictionRequestDto requireNormalizedPredictionRequest(PredictionRequestDto request) {
        PredictionRequestDto normalizedRequest = normalizePredictionRequest(request);
        if (normalizedRequest == null) {
            throw new BadRequestBusinessException("INVALID_PREDICTION_INPUT", "투표 입력 값이 올바르지 않습니다.");
        }
        return normalizedRequest;
    }

    private Principal requirePrincipal(Principal principal) {
        if (principal == null) {
            throw new AuthenticationRequiredException("로그인이 필요합니다.");
        }
        return principal;
    }

    private Principal requirePrincipalForMyVotes(Principal principal) {
        if (principal == null) {
            throw new UnauthorizedBusinessException(
                    "AUTHENTICATION_REQUIRED",
                    "로그인이 필요합니다.",
                    emptyVotesPayload());
        }
        return principal;
    }

    private Long parsePrincipalUserId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadRequestBusinessException("INVALID_PRINCIPAL", "로그인 사용자 정보를 확인할 수 없습니다.");
        }

        String normalizedUserId = principal.getName().trim();
        if (normalizedUserId.isEmpty()) {
            throw new BadRequestBusinessException("INVALID_PRINCIPAL", "로그인 사용자 정보를 확인할 수 없습니다.");
        }

        try {
            return Long.valueOf(normalizedUserId);
        } catch (NumberFormatException ex) {
            throw new BadRequestBusinessException("INVALID_PRINCIPAL", "로그인 사용자 ID 형식이 올바르지 않습니다.");
        }
    }

    private Long parsePrincipalUserIdForMyVotes(Principal principal) {
        try {
            return parsePrincipalUserId(principal);
        } catch (BadRequestBusinessException ex) {
            throw new BadRequestBusinessException(ex.getCode(), ex.getMessage(), emptyVotesPayload());
        }
    }

    private Map<String, Object> emptyVotesPayload() {
        return Map.of("votes", Map.of());
    }
}
