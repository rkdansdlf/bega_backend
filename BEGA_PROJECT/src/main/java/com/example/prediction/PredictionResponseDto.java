package com.example.prediction;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PredictionResponseDto {

	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private String gameId;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private Long homeVotes;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private Long awayVotes;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private Long totalVotes;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private Integer homePercentage;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private Integer awayPercentage;
}
