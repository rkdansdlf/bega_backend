package com.example.rankingPrediction;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
	
	@Column(name = "prediction_data", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> predictionData; 
	
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

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
	
	// Entity를 외부 전송용 DTO 객체로 변환
	public RankingPredictionResponseDto toDto() {
		return new RankingPredictionResponseDto(
				this.id,
				this.userId,
				this.seasonYear,
				this.predictionData,
				this.createdAt
			);		
	}
	
}
	
	
	
	
	


