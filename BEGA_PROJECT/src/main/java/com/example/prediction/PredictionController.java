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
    
    
    // 투표하기
     // POST /api/predictions/vote?userId=1
     // Body: { "gameId": "20240427LGOB0", "votedTeam": "home" }
     
    @PostMapping("/predictions/vote")
    public ResponseEntity<String> vote(
            Principal principal,
            @RequestBody PredictionRequestDto request) {
        try {
		    Long userId = Long.valueOf(principal.getName());
		        
            predictionService.vote(userId, request);
            return ResponseEntity.ok("투표가 완료되었습니다.");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
     // 투표 현황 조회
     // GET /api/predictions/status/{gameId}
     
    @GetMapping("/predictions/status/{gameId}")
    public ResponseEntity<PredictionResponseDto> getVoteStatus(@PathVariable String gameId) {
        PredictionResponseDto response = predictionService.getVoteStatus(gameId);
        return ResponseEntity.ok(response);
    }
    
    
     // 투표 취소
     // DELETE /api/predictions/{gameId}?userId=1
     
    @DeleteMapping("/predictions/{gameId}")
    public ResponseEntity<String> cancelVote(
				    Principal principal,
            @PathVariable String gameId) {
        try {
		        Long userId = Long.valueOf(principal.getName());
		        
            predictionService.cancelVote(userId, gameId);
            return ResponseEntity.ok("투표가 취소되었습니다.");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    // 과거 경기 결과 조회 (스코어 및 결과)
    @GetMapping("/games/past")
    public ResponseEntity<List<MatchDto>> getPastGames() {
        
    	// Service 메서드 호출 시 파라미터가 불필요함
        List<MatchDto> pastGames = predictionService.getPastGames();
        return ResponseEntity.ok(pastGames);
    }
    
    // 오늘 경기 목록 조회
    //  GET /api/predictions/matches/today
     
    @GetMapping("/matches/today")
    public ResponseEntity<List<Match>> getTodayMatches() {
        List<Match> matches = predictionService.getTodayMatches();
        return ResponseEntity.ok(matches);
    }
    
    // 특정 날짜의 경기 목록 조회
    @GetMapping("/matches/date")
    public ResponseEntity<List<Match>> getMatchesByDate(
		        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<Match> matches = predictionService.getMatchesByDate(date);
        return ResponseEntity.ok(matches);
    }
    
}
