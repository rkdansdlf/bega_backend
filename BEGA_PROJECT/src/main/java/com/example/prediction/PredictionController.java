package com.example.prediction;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class PredictionController {
	
	private final PredictionService predictionService;
	
	// 투표하기
	@PostMapping("/vote")
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
	@GetMapping("/status/{gameId}")
	public ResponseEntity<PredictionResponseDto> getVoteStatus(@PathVariable("gameId") String gameId){	
		PredictionResponseDto response = predictionService.getVoteStatus(gameId);
		return ResponseEntity.ok(response);
	}
	
	// 투표 취소
	@DeleteMapping("/{gameId}")
	public ResponseEntity<String> cancelVote(
			Principal principal,
			@PathVariable("gameId") String gameId) {
		try {
			Long userId = Long.valueOf(principal.getName());
			
			predictionService.cancelVote(userId, gameId);
			return ResponseEntity.ok("투표가 취소되었습니다.");
		} catch (IllegalStateException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}
	
	// 오늘 경기 목록 조회
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
