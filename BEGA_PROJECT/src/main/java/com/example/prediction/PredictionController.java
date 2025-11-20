package com.example.prediction;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("isAuthenticated()")
public class PredictionController {

    private final PredictionService predictionService;
    
    // 과거 경기 조회 (10월 25~31일 일주일치)
    @GetMapping("/games/past")
    public ResponseEntity<List<MatchDto>> getPastGames() {
        List<MatchDto> matches = predictionService.getMatchesByDateRange(
            LocalDate.of(2024, 10, 25),
            LocalDate.of(2024, 10, 31)
        );
        return ResponseEntity.ok(matches);
    }

    // 특정 날짜의 경기 조회 (모든 경우에 사용)
    @GetMapping("/matches")
    public ResponseEntity<List<MatchDto>> getMatches(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<MatchDto> matches = predictionService.getMatchesByDate(date);
        return ResponseEntity.ok(matches);
    }
    
    // 특정 기간의 경기 조회 (과거 일주일치 등)
    @GetMapping("/matches/range")
    public ResponseEntity<List<MatchDto>> getMatchesByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<MatchDto> matches = predictionService.getMatchesByDateRange(startDate, endDate);
        return ResponseEntity.ok(matches);
    }

    // 투표하기
    @PostMapping("/predictions/vote")
    public ResponseEntity<String> vote(
            Principal principal,
            @RequestBody PredictionRequestDto request) {
        try {
            Long userId = Long.valueOf(principal.getName());
            predictionService.vote(userId, request);
            return ResponseEntity.ok("투표가 완료되었습니다.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 투표 현황 조회
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
}