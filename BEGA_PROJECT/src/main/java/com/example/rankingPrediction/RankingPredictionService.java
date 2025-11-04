package com.example.demo.rankingPrediction;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RankingPredictionService {

	private final RankingPredictionRepository rankingPredictionRepository;
	
	// 순위 예측을 저장하거나 업데이트 하는 통합 로직
	// 현재 로그인한 사용자의 해당 시즌 예측이 이미 있는지 확인하고, 없으면 새로 저장
	@Transactional
	public RankingPredictionResponseDto saveOrUpdatePrediction(RankingPredictionRequestDto requestDto, String userIdString) {
	
		// 1. Principal에서 받은 String ID를 DB에서 사용할 Long 타입으로 변환
		Long currentUserId = Long.valueOf(userIdString);
		int currentSeasonYear = requestDto.getSeasonYear();
		
		// 2. 기존 예측 조회
		Optional<RankingPrediction> existingPrediction = 
				rankingPredictionRepository.findByUserIdAndSeasonYear(currentUserId, currentSeasonYear);
		
		RankingPrediction predictionToSave;
		
		if (existingPrediction.isPresent()) {
			// 3. 데이터가 있으면 업데이트
			predictionToSave = existingPrediction.get();
			
			predictionToSave.updatePredictionData(requestDto.getTeamIdsInOrder());
		} else {
			// 4. 데이터가 없으면 새로 생성
			predictionToSave = new RankingPrediction(
					currentUserId,
					currentSeasonYear,
					requestDto.getTeamIdsInOrder()
					
			);
		}
		
		RankingPrediction saved = rankingPredictionRepository.save(predictionToSave);
		return saved.toDto();
	}
		// 현재 로그인된 사용자의 특정 시즌 예측 불러옴 
		// DB에서 일치하는 예측 데이터를 찾아 반환
		@Transactional(readOnly = true)
		public RankingPredictionResponseDto getPrediction(String userIdString, int seasonYear) {
			
			// String ID를 Long 타입으로 변환
			Long userId = Long.valueOf(userIdString);
			
			// userId와 seasonYear를 기준으로 DB에서 데이터 조회
			return rankingPredictionRepository.findByUserIdAndSeasonYear(userId, seasonYear)
					.map(RankingPrediction::toDto)
					.orElse(null);
		
	}	
}
