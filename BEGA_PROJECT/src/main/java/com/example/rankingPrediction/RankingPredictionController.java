package com.example.rankingPrediction;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/predictions/ranking")
@RequiredArgsConstructor
public class RankingPredictionController {

	private final RankingPredictionService rankingPredictionService;
	
	// 예측 저장 요청
	
	@PostMapping 
	public ResponseEntity<RankingPredictionResponseDto> savePrediction(
			Principal principal, @RequestBody RankingPredictionRequestDto requestDto) {
		
		RankingPredictionResponseDto savedDto = rankingPredictionService.savePrediction(requestDto, principal.getName());
		
		return ResponseEntity.ok(savedDto);
	}
	
	@GetMapping
	public ResponseEntity<RankingPredictionResponseDto> getPredction(
			Principal principal,
			@RequestParam int seasonYear) {
		
		RankingPredictionResponseDto prediction = rankingPredictionService.getPrediction(principal.getName(), seasonYear);
		
		if (prediction != null) {
			return ResponseEntity.ok(prediction);
		} else {
			return ResponseEntity.notFound().build();
		}
	}
	
	

}
