package com.example.prediction;

import java.util.List;

import lombok.Getter;

@Getter
public class RankingPredictionRequestDto {

	private int seasonYear;
	private List<String> teamIdsInOrder;

	public int getSeasonYear() {
		return seasonYear;
	}

	public List<String> getTeamIdsInOrder() {
		return teamIdsInOrder;
	}
}
