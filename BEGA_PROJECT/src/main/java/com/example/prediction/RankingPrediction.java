package com.example.prediction;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.example.common.converter.StringListJsonConverter;

@Entity
@Table(name = "ranking_predictions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RankingPrediction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id")
	private String userId;

	@Column(name = "season_year")
	private int seasonYear;

	@Convert(converter = StringListJsonConverter.class)
	@org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONG32VARCHAR)
	@Column(name = "prediction_data", nullable = false)
	private List<String> predictionData;

	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	// 시즌 정산 시 확정된, 정확히 맞춘 팀 수 (0~10). 정산 전에는 null
	@Column(name = "exact_match_count")
	private Integer exactMatchCount;

	// 시즌 정산이 반영된 시각. 정산 전에는 null
	@Column(name = "settled_at")
	private LocalDateTime settledAt;

	// 새로운 예측 데이터를 DB에 저장할 때 사용
	public RankingPrediction(String userId, int seasonYear, List<String> predictionData) {
		this.userId = userId;
		this.seasonYear = seasonYear;
		this.predictionData = predictionData;
		this.createdAt = LocalDateTime.now();
	}

	// 기존 예측 데이터를 수정할 때 사용
	public void updatePredictionData(List<String> newPredictionData) {
		this.predictionData = newPredictionData;
	}

	// 시즌 정산 스케줄러가 확정된 결과를 반영할 때 사용
	public void markSettled(int exactMatchCount, LocalDateTime settledAt) {
		this.exactMatchCount = exactMatchCount;
		this.settledAt = settledAt;
	}

	// Entity를 외부 전송용 DTO 객체로 변환
	public RankingPredictionResponseDto toDto() {
		return new RankingPredictionResponseDto(
				this.id,
				null,
				this.seasonYear,
				this.predictionData,
				null, // teamDetails 필드 추가됨
				this.createdAt,
				this.exactMatchCount,
				this.settledAt);
	}

}
