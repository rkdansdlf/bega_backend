package com.example.prediction;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.common.exception.BadRequestBusinessException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GameLiveController {

    private static final Pattern GAME_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final int MAX_LIVE_BATCH_SIZE = 50;

    private final GameLiveService gameLiveService;
    private final GameLiveRelayService gameLiveRelayService;

    @GetMapping("/matches/{gameId}/live")
    public ResponseEntity<GameLiveSnapshotDto> getLiveSnapshot(
            @PathVariable String gameId,
            @RequestParam(required = false) Integer afterSeq,
            @RequestParam(required = false) Integer limit) {
        String normalizedGameId = requireGameId(gameId);
        Integer normalizedAfterSeq = normalizeAfterSeq(afterSeq);
        return ResponseEntity.ok(gameLiveService.getLiveSnapshot(normalizedGameId, normalizedAfterSeq, limit));
    }

    @GetMapping("/matches/{gameId}/live-relay")
    public ResponseEntity<GameRelaySnapshotDto> getLiveRelaySnapshot(
            @PathVariable String gameId,
            @RequestParam(required = false) Integer afterId,
            @RequestParam(required = false) Integer limit) {
        String normalizedGameId = requireGameId(gameId);
        Integer normalizedAfterId = normalizeAfterId(afterId);
        return ResponseEntity.ok(gameLiveRelayService.getRelaySnapshot(normalizedGameId, normalizedAfterId, limit));
    }

    @GetMapping("/matches/live")
    public ResponseEntity<List<GameLiveSummaryDto>> getLiveSummaries(@RequestParam String gameIds) {
        List<String> normalizedGameIds = normalizeGameIds(gameIds);
        if (normalizedGameIds.size() > MAX_LIVE_BATCH_SIZE) {
            throw new BadRequestBusinessException(
                    "TOO_MANY_GAME_IDS",
                    "요청한 경기 수가 너무 많습니다.");
        }
        return ResponseEntity.ok(gameLiveService.getLiveSummaries(normalizedGameIds));
    }

    private Integer normalizeAfterSeq(Integer afterSeq) {
        if (afterSeq == null) {
            return null;
        }
        if (afterSeq < 0) {
            throw new BadRequestBusinessException("INVALID_AFTER_SEQ", "이벤트 순번이 잘못되었습니다.");
        }
        return afterSeq;
    }

    private Integer normalizeAfterId(Integer afterId) {
        if (afterId == null) {
            return null;
        }
        if (afterId < 0) {
            throw new BadRequestBusinessException("INVALID_AFTER_ID", "문자중계 ID가 잘못되었습니다.");
        }
        return afterId;
    }

    private List<String> normalizeGameIds(String gameIds) {
        if (gameIds == null || gameIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(gameIds.split(","))
                .map(this::normalizeGameId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String requireGameId(String gameId) {
        String normalized = normalizeGameId(gameId);
        if (normalized == null) {
            throw new BadRequestBusinessException("INVALID_GAME_ID", "게임 ID가 잘못되었습니다.");
        }
        return normalized;
    }

    private String normalizeGameId(String gameId) {
        if (gameId == null) {
            return null;
        }
        String normalized = gameId.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!GAME_ID_PATTERN.matcher(normalized).matches()) {
            throw new BadRequestBusinessException("INVALID_GAME_ID", "게임 ID가 잘못되었습니다.");
        }
        return normalized;
    }
}
