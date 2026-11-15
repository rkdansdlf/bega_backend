package com.example.teamRecommendationTest;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamUserAnswersDto {
	
	@NotNull(message = "answers는 필수입니다.")
	private Map<String, String> answers;
}
