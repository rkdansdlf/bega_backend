package com.example.prediction;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RankingPredictionRequestDto {

	private int seasonYear;
	private List<String> teamIdsInOrder;
}
