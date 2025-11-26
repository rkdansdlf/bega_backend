package com.example.rankingPrediction;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RankingPredictionResponseDto {

	private Long id;
	private Long userId;
	private int seasonYear;
	private List<String> teamIdsInOrder;
	private LocalDateTime createdAt;
	
}
	


