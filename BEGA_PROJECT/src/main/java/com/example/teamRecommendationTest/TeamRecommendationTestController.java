package com.example.teamRecommendationTest;

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
	    public TeamResultDto getResult(@RequestBody TeamUserAnswersDto teamUserAnswersDto) {
	        return teamRecommendationTestService.CalculateBestTeam(teamUserAnswersDto);
	    }

}
