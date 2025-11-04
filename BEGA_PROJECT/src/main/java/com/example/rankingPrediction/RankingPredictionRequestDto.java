package com.example.demo.rankingPrediction;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RankingPredictionRequestDto {

	private Long userId;
	private int seasonYear;
	private List<String> teamIdsInOrder;
}
