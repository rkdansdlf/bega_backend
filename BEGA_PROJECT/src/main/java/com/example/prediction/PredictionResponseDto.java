package com.example.demo.prediction;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PredictionResponseDto {

	private String gameId;
	private Long homeVotes;
	private Long awayVotes;
	private Long totalVotes;
	private Integer homePercentage;
	private Integer awayPercentage;
}
