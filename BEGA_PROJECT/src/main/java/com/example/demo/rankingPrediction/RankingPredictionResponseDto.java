package com.example.demo.rankingPrediction;

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
	private String userId;
	private int seasonYear;
	private List<String> teamIdsInOrder;
	private List<TeamRankingDetail> teamDetails; // 상세 정보 추가
	private LocalDateTime createdAt;

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TeamRankingDetail {
		private String teamId;
		private String teamName;
		private Integer currentRank; // 현재 시즌 순위
		private Integer lastSeasonRank; // 전 시즌 순위
	}

}
