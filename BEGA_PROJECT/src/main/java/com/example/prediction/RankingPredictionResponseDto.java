package com.example.prediction;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;

@Getter
public class RankingPredictionResponseDto {

	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private Long id;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
	private String shareId;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private int seasonYear;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private List<String> teamIdsInOrder;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private List<TeamRankingDetail> teamDetails; // 상세 정보 추가
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private LocalDateTime createdAt;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
	private Integer exactMatchCount; // 시즌 정산 후 정확히 맞춘 팀 수 (0~10). 정산 전에는 null
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
	private LocalDateTime settledAt; // 시즌 정산 시각. 정산 전에는 null

	public RankingPredictionResponseDto(Long id, String shareId, int seasonYear, List<String> teamIdsInOrder,
			List<TeamRankingDetail> teamDetails, LocalDateTime createdAt,
			Integer exactMatchCount, LocalDateTime settledAt) {
		this.id = id;
		this.shareId = shareId;
		this.seasonYear = seasonYear;
		this.teamIdsInOrder = teamIdsInOrder;
		this.teamDetails = teamDetails;
		this.createdAt = createdAt;
		this.exactMatchCount = exactMatchCount;
		this.settledAt = settledAt;
	}

	@Getter
	public static class TeamRankingDetail {
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
		private String teamId;
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
		private String teamName;
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		private Integer currentRank; // 현재 시즌 순위
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		private Integer lastSeasonRank; // 전 시즌 순위

		public TeamRankingDetail(String teamId, String teamName, Integer currentRank, Integer lastSeasonRank) {
			this.teamId = teamId;
			this.teamName = teamName;
			this.currentRank = currentRank;
			this.lastSeasonRank = lastSeasonRank;
		}
	}

}
