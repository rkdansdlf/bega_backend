package com.example.prediction;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RankingPredictionRequestDto {

	@NotNull
	@Positive
	private Integer seasonYear;
	private List<String> teamIdsInOrder;
}
