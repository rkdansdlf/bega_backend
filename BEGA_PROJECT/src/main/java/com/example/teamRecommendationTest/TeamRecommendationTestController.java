package com.example.teamRecommendationTest;

import java.security.Principal;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/quiz")  
@RequiredArgsConstructor
public class TeamRecommendationTestController {
	
	private final TeamRecommendationTestService teamRecommendationTestService;
	
	@PostMapping("/result")
	@ApiResponse(responseCode = "200", description = "팀 추천 결과",
			content = @Content(schema = @Schema(implementation = TeamResultDto.class)))
	@ApiResponse(responseCode = "409", description = "내부 팀 추천 데이터가 없어 운영자 확인이 필요합니다 (MANUAL_BASEBALL_DATA_REQUIRED).",
			content = @Content(schema = @Schema(implementation = com.example.common.dto.ApiResponse.class)))
	public TeamResultDto getResult(
			Principal principal,
			@Valid @RequestBody TeamUserAnswersDto teamUserAnswersDto) {
		 
		 	Long userId = Long.valueOf(principal.getName());
	        return teamRecommendationTestService.CalculateBestTeam(userId,teamUserAnswersDto);
	    }

}
