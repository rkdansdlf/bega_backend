package com.example.rankingPrediction;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RankingPredictionService {

	private final RankingPredictionRepository rankingPredictionRepository;

	// 순위 예측을 저장 (수정 불가, 1회만 가능)
	@Transactional
	public RankingPredictionResponseDto savePrediction(
			RankingPredictionRequestDto requestDto, 
			String userIdString) {

		// 1. Principal에서 받은 String ID를 Long 타입으로 변환
		Long currentUserId = Long.valueOf(userIdString);
		int currentSeasonYear = requestDto.getSeasonYear();

		// 2. 이미 예측했는지 확인
		boolean alreadyPredicted = rankingPredictionRepository
				.existsByUserIdAndSeasonYear(currentUserId, currentSeasonYear);

		// 3. 이미 예측했으면 에러 발생
		if (alreadyPredicted) {
			throw new IllegalStateException(
				"이미 " + currentSeasonYear + " 시즌 순위 예측을 완료하셨습니다. 수정은 불가능합니다."
			);
		}

		// 4. 새로운 예측 생성
		RankingPrediction newPrediction = new RankingPrediction(
				currentUserId,
				currentSeasonYear,
				requestDto.getTeamIdsInOrder()
		);

		// 5. DB에 저장
		RankingPrediction saved = rankingPredictionRepository.save(newPrediction);
		return saved.toDto();
	}

	// 현재 로그인된 사용자의 특정 시즌 예측 조회
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