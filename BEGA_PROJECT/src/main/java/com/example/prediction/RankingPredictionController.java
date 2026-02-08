package com.example.prediction;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/predictions/ranking")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class RankingPredictionController {

	private final RankingPredictionService rankingPredictionService;

	@PreAuthorize("permitAll()")
	@GetMapping("/current-season")
	public ResponseEntity<?> getCurrentSeason() {

		try {
			int currentSeason = rankingPredictionService.getCurrentSeason();
			return ResponseEntity.ok(Map.of("seasonYear", currentSeason));
		} catch (IllegalStateException e) {
			return ResponseEntity
					.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", e.getMessage()));
		}
	}

	// 예측 저장 요청

	@PostMapping
	public ResponseEntity<?> savePrediction(
			Principal principal, @RequestBody RankingPredictionRequestDto requestDto) {

		// 로그인 체크
		if (principal == null) {
			return ResponseEntity
					.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("error", "로그인이 필요합니다."));
		}

		try {
			RankingPredictionResponseDto savedDto = rankingPredictionService.savePrediction(requestDto,
					principal.getName());

			return ResponseEntity.ok(savedDto);

		} catch (Exception e) {
			// 모든 예외 로깅
			e.printStackTrace();
			return ResponseEntity
					.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "서버 오류가 발생했습니다: " + e.getMessage()));
		}
	}

	@GetMapping
	public ResponseEntity<RankingPredictionResponseDto> getPredction(
			Principal principal,
			@RequestParam int seasonYear) {

		if (principal == null) {
			// @PreAuthorize("isAuthenticated()")에 의해 차단되지만, 안전을 위해 추가
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		RankingPredictionResponseDto prediction = rankingPredictionService.getPrediction(principal.getName(),
				seasonYear);

		return ResponseEntity.ok(prediction);
	}

	// 공유용 예측 조회 (로그인 불필요)
	@PreAuthorize("permitAll()")
	@GetMapping("/share/{userId}/{seasonYear}")
	public ResponseEntity<?> getSharedPrediction(
			@PathVariable String userId,
			@PathVariable int seasonYear) {

		try {
			RankingPredictionResponseDto prediction = rankingPredictionService.getPredictionByUserIdAndSeason(userId,
					seasonYear);

			if (prediction != null) {
				return ResponseEntity.ok(prediction);
			} else {
				return ResponseEntity.notFound().build();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity
					.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "공유 예측 조회 중 오류 발생: " + e.getMessage()));
		}
	}

}
