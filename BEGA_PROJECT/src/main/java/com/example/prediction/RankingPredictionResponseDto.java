package com.example.prediction;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;

@Getter
public class RankingPredictionResponseDto {

	private Long id;
	private String userId;
	private int seasonYear;
	private List<String> teamIdsInOrder;
	private List<TeamRankingDetail> teamDetails; // 상세 정보 추가
	private LocalDateTime createdAt;

	public RankingPredictionResponseDto(Long id, String userId, int seasonYear, List<String> teamIdsInOrder,
			List<TeamRankingDetail> teamDetails, LocalDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.seasonYear = seasonYear;
		this.teamIdsInOrder = teamIdsInOrder;
		this.teamDetails = teamDetails;
		this.createdAt = createdAt;
	}

	@Getter
	public static class TeamRankingDetail {
		private String teamId;
		private String teamName;
		private Integer currentRank; // 현재 시즌 순위
		private Integer lastSeasonRank; // 전 시즌 순위

		public TeamRankingDetail(String teamId, String teamName, Integer currentRank, Integer lastSeasonRank) {
			this.teamId = teamId;
			this.teamName = teamName;
			this.currentRank = currentRank;
			this.lastSeasonRank = lastSeasonRank;
		}
	}

}
