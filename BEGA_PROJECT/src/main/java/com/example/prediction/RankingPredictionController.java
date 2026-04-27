package com.example.prediction;

import java.security.Principal;
import java.util.Map;
import java.util.regex.Pattern;

import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import jakarta.validation.Valid;
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
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/predictions/ranking")
@RequiredArgsConstructor
@Slf4j
public class RankingPredictionController {

	private static final Pattern SHARE_ID_PATTERN = Pattern.compile(
			"(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

	private final RankingPredictionService rankingPredictionService;

	@PreAuthorize("permitAll()")
	@GetMapping("/current-season")
	public ResponseEntity<?> getCurrentSeason() {
		int currentSeason = rankingPredictionService.getCurrentSeason();
		return ResponseEntity.ok(Map.of("seasonYear", currentSeason));
	}

	// 예측 저장 요청

	@PreAuthorize("isAuthenticated()")
	@PostMapping
	public ResponseEntity<?> savePrediction(
			Principal principal, @Valid @RequestBody RankingPredictionRequestDto requestDto) {
		Principal authenticatedPrincipal = requirePrincipal(principal);
		RankingPredictionResponseDto savedDto = rankingPredictionService.savePrediction(
				requestDto,
				authenticatedPrincipal.getName());

		return ResponseEntity.ok(savedDto);
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping
	public ResponseEntity<RankingPredictionResponseDto> getPredction(
			Principal principal,
			@RequestParam int seasonYear) {

		Principal authenticatedPrincipal = requirePrincipal(principal);

		RankingPredictionResponseDto prediction = rankingPredictionService.getPrediction(authenticatedPrincipal.getName(),
				seasonYear);

		return ResponseEntity.ok(prediction);
	}

	// 공유용 예측 조회 (로그인 불필요)
	@PreAuthorize("permitAll()")
	@GetMapping("/share/{shareId}/{seasonYear}")
	public ResponseEntity<?> getSharedPrediction(
			@PathVariable String shareId,
			@PathVariable int seasonYear) {

		if (!isValidShareId(shareId)) {
			throw new BadRequestBusinessException("INVALID_SHARE_ID", "공유 식별자 형식이 올바르지 않습니다.");
		}

		RankingPredictionResponseDto prediction = rankingPredictionService.getPredictionByShareIdAndSeason(shareId,
				seasonYear);

		if (prediction != null) {
			return ResponseEntity.ok(prediction);
		}
		throw new NotFoundBusinessException(
				"RANKING_PREDICTION_SHARE_NOT_FOUND",
				"공유된 시즌 순위 예측을 찾을 수 없습니다.");
	}

	private Principal requirePrincipal(Principal principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException("로그인이 필요합니다.");
		}
		return principal;
	}

	private boolean isValidShareId(String shareId) {
		return shareId != null && SHARE_ID_PATTERN.matcher(shareId.trim()).matches();
	}

}
